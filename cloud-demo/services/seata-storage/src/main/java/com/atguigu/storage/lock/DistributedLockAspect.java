package com.atguigu.storage.lock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 分布式鎖AOP切面
 * 
 * 實現@DistributedLockable註解的攔截邏輯，提供跨服務的分布式鎖功能。
 * 支持SpEL表達式解析動態鎖鍵，實現跨服務鎖衝突檢測和處理。
 * 
 * 主要功能：
 * - 攔截@DistributedLockable註解的方法
 * - 解析SpEL表達式生成動態鎖鍵
 * - 實現不同的失敗處理策略
 * - 記錄服務來源信息到鎖上下文
 * - 跨服務鎖衝突檢測和處理
 * 
 * @author system
 */
@Aspect
@Component
@Order(1) // 確保在事務切面之前執行
public class DistributedLockAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(DistributedLockAspect.class);
    
    @Autowired
    private DistributedLock distributedLock;
    
    @Autowired
    private CrossServiceLockKeyGenerator lockKeyGenerator;
    
    @Autowired(required = false)
    private CrossServiceLockMetricsCollector metricsCollector;
    
    @Autowired(required = false)
    private LocalLockTransactionSynchronization localTransactionSynchronization;
    
    @Value("${spring.application.name:seata-storage}")
    private String serviceName;
    
    @Value("${distributed.lock.enable-conflict-detection:true}")
    private boolean enableConflictDetection;
    
    @Value("${distributed.lock.max-retry-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${distributed.lock.retry-base-delay:100}")
    private long retryBaseDelay; // 毫秒
    
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    
    /**
     * 環繞通知：攔截@DistributedLockable註解的方法
     */
    @Around("@annotation(distributedLockable)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLockable distributedLockable) throws Throwable {
        String lockKey = null;
        boolean lockAcquired = false;
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 解析SpEL表達式生成鎖鍵
            lockKey = parseLockKey(joinPoint, distributedLockable.key());
            
            if (!StringUtils.hasText(lockKey)) {
                throw new DistributedLockException(
                    LockErrorCode.INVALID_LOCK_KEY, 
                    "解析鎖鍵失敗，SpEL表達式: " + distributedLockable.key()
                );
            }
            
            logger.debug("Attempting to acquire distributed lock: {} for method: {} in service: {}", 
                        lockKey, joinPoint.getSignature().toShortString(), serviceName);
            
            // 2. 嘗試獲取分布式鎖
            lockAcquired = tryAcquireLock(lockKey, distributedLockable);
            
            // 記錄指標數據
            if (metricsCollector != null) {
                long acquireTime = System.currentTimeMillis() - startTime;
                metricsCollector.recordLockAcquire(lockKey, serviceName, lockAcquired, 
                    java.time.Duration.ofMillis(acquireTime));
            }
            
            if (!lockAcquired) {
                // 3. 處理鎖獲取失敗的情況
                return handleLockAcquisitionFailure(joinPoint, distributedLockable, lockKey);
            }
            
            // 4. 記錄服務來源信息到鎖上下文
            recordLockContext(lockKey, distributedLockable);
            
            logger.info("Successfully acquired distributed lock: {} for method: {} in service: {}", 
                       lockKey, joinPoint.getSignature().toShortString(), serviceName);
            
            // 5. 執行目標方法
            Object result = joinPoint.proceed();
            
            logger.debug("Method execution completed for lock: {} in service: {}", lockKey, serviceName);
            
            return result;
            
        } catch (DistributedLockException e) {
            logger.error("Distributed lock error for key: {} in service: {}", lockKey, serviceName, e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while processing distributed lock for key: {} in service: {}", 
                        lockKey, serviceName, e);
            throw new DistributedLockException(LockErrorCode.LOCK_ACQUIRE_TIMEOUT, lockKey, serviceName, 
                                             "分布式鎖處理過程中發生異常", e);
        } finally {
            // 6. 釋放鎖
            if (lockAcquired && lockKey != null) {
                releaseLock(lockKey, startTime);
            }
        }
    }
    
    /**
     * 解析SpEL表達式生成鎖鍵
     */
    private String parseLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        try {
            // 創建SpEL評估上下文
            EvaluationContext context = createEvaluationContext(joinPoint);
            
            // 解析表達式
            Expression expression = expressionParser.parseExpression(keyExpression);
            
            // 評估表達式
            Object keyValue = expression.getValue(context);
            
            if (keyValue == null) {
                throw new DistributedLockException(
                    LockErrorCode.INVALID_LOCK_KEY, 
                    "SpEL表達式評估結果為null: " + keyExpression
                );
            }
            
            String lockKey = keyValue.toString();
            
            // 驗證鎖鍵格式
            if (!lockKeyGenerator.isValidStorageLockKey(lockKey) && !lockKey.startsWith("distributed:lock:")) {
                // 如果不是完整的鎖鍵格式，則使用鍵生成器生成標準格式
                if (lockKey.startsWith("storage:")) {
                    String commodityCode = lockKey.substring("storage:".length());
                    lockKey = lockKeyGenerator.generateStorageLockKey(commodityCode);
                } else {
                    // 對於其他格式，添加標準前綴
                    lockKey = "distributed:lock:" + lockKey;
                }
            }
            
            return lockKey;
            
        } catch (Exception e) {
            logger.error("Failed to parse SpEL expression: {} in service: {}", keyExpression, serviceName, e);
            throw DistributedLockException.spelExpressionError(keyExpression, e);
        }
    }
    
    /**
     * 創建SpEL評估上下文
     */
    private EvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // 獲取方法參數名和值
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        
        // 將方法參數添加到上下文中
        if (parameterNames != null && args != null) {
            for (int i = 0; i < parameterNames.length && i < args.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        
        // 添加一些有用的上下文變量
        context.setVariable("serviceName", serviceName);
        context.setVariable("methodName", signature.getMethod().getName());
        context.setVariable("className", signature.getDeclaringTypeName());
        
        return context;
    }
    
    /**
     * 嘗試獲取分布式鎖，支持重試策略
     */
    private boolean tryAcquireLock(String lockKey, DistributedLockable distributedLockable) {
        long waitTime = distributedLockable.waitTime();
        long leaseTime = distributedLockable.leaseTime();
        
        // 檢查跨服務鎖衝突
        if (enableConflictDetection) {
            detectCrossServiceLockConflict(lockKey);
        }
        
        // 嘗試獲取鎖
        boolean acquired = distributedLock.tryLock(lockKey, waitTime, leaseTime);
        
        // 如果配置了重試策略且獲取失敗，則進行重試
        if (!acquired && distributedLockable.failStrategy().shouldRetry()) {
            acquired = retryAcquireLock(lockKey, waitTime, leaseTime);
        }
        
        return acquired;
    }
    
    /**
     * 重試獲取鎖，使用指數退避策略
     */
    private boolean retryAcquireLock(String lockKey, long waitTime, long leaseTime) {
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                // 計算退避延遲時間（指數退避 + 隨機抖動）
                long delay = calculateBackoffDelay(attempt);
                Thread.sleep(delay);
                
                logger.debug("Retrying to acquire lock: {} (attempt {}/{}) in service: {}", 
                           lockKey, attempt, maxRetryAttempts, serviceName);
                
                boolean acquired = distributedLock.tryLock(lockKey, waitTime, leaseTime);
                if (acquired) {
                    logger.info("Successfully acquired lock: {} on retry attempt {} in service: {}", 
                               lockKey, attempt, serviceName);
                    return true;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Retry interrupted for lock: {} in service: {}", lockKey, serviceName);
                break;
            } catch (Exception e) {
                logger.warn("Error during retry attempt {} for lock: {} in service: {}", 
                           attempt, lockKey, serviceName, e);
            }
        }
        
        logger.warn("Failed to acquire lock: {} after {} retry attempts in service: {}", 
                   lockKey, maxRetryAttempts, serviceName);
        return false;
    }
    
    /**
     * 計算指數退避延遲時間
     */
    private long calculateBackoffDelay(int attempt) {
        // 指數退避：baseDelay * 2^(attempt-1)
        long exponentialDelay = retryBaseDelay * (1L << (attempt - 1));
        
        // 添加隨機抖動（±25%）
        long jitter = (long) (exponentialDelay * 0.25 * (ThreadLocalRandom.current().nextDouble() - 0.5));
        
        return exponentialDelay + jitter;
    }
    
    /**
     * 檢測跨服務鎖衝突
     */
    private void detectCrossServiceLockConflict(String lockKey) {
        try {
            if (distributedLock.isLocked(lockKey)) {
                // 檢查是否由其他服務持有
                if (distributedLock instanceof RedisDistributedLock) {
                    RedisDistributedLock redisLock = (RedisDistributedLock) distributedLock;
                    CrossServiceLockContext context = redisLock.getLockContext(lockKey);
                    
                    if (context != null && !serviceName.equals(context.getServiceSource())) {
                        logger.warn("Cross-service lock conflict detected. Key: {}, Current service: {}, Holder service: {}", 
                                   lockKey, serviceName, context.getServiceSource());
                        
                        // 可以在這裡實現更複雜的衝突處理邏輯
                        handleCrossServiceLockConflict(lockKey, context);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error during cross-service lock conflict detection for key: {} in service: {}", 
                       lockKey, serviceName, e);
        }
    }
    
    /**
     * 處理跨服務鎖衝突
     */
    private void handleCrossServiceLockConflict(String lockKey, CrossServiceLockContext holderContext) {
        // 記錄衝突信息
        logger.info("Handling cross-service lock conflict for key: {}, holder: {}, requester: {}", 
                   lockKey, holderContext.getServiceSource(), serviceName);
        
        // 記錄跨服務衝突指標
        if (metricsCollector != null) {
            metricsCollector.recordCrossServiceConflict(lockKey, serviceName, 
                holderContext.getServiceSource());
        }
        
        // 這裡可以實現更複雜的衝突解決策略，比如：
        // 1. 基於優先級的處理
        // 2. 基於業務上下文的處理
        // 3. 通知監控系統
        
        // 目前只記錄日誌，實際項目中可以根據需要擴展
    }
    
    /**
     * 記錄鎖上下文信息
     */
    private void recordLockContext(String lockKey, DistributedLockable distributedLockable) {
        try {
            String businessContext = StringUtils.hasText(distributedLockable.businessContext()) 
                ? distributedLockable.businessContext() 
                : "distributed-lock-operation";
            
            // 註冊鎖到本地事務同步器
            if (localTransactionSynchronization != null) {
                localTransactionSynchronization.registerLockToTransaction(lockKey, businessContext);
                logger.debug("Registered lock: {} to local transaction synchronization in service: {}", 
                            lockKey, serviceName);
            }
            
            // 如果使用RedisDistributedLock，上下文信息已經在tryLock中記錄
            // 這裡可以添加額外的上下文信息記錄邏輯
            
            logger.debug("Recorded lock context for key: {} with business context: {} in service: {}", 
                        lockKey, businessContext, serviceName);
            
        } catch (Exception e) {
            logger.warn("Failed to record lock context for key: {} in service: {}", lockKey, serviceName, e);
        }
    }
    
    /**
     * 處理鎖獲取失敗的情況
     */
    private Object handleLockAcquisitionFailure(ProceedingJoinPoint joinPoint, 
                                               DistributedLockable distributedLockable, 
                                               String lockKey) throws Throwable {
        LockFailStrategy strategy = distributedLockable.failStrategy();
        
        logger.warn("Failed to acquire distributed lock: {} using strategy: {} in service: {}", 
                   lockKey, strategy, serviceName);
        
        // 記錄鎖超時指標
        if (metricsCollector != null) {
            metricsCollector.recordLockTimeout(lockKey, serviceName, 
                java.time.Duration.ofSeconds(distributedLockable.waitTime()));
        }
        
        switch (strategy) {
            case EXCEPTION:
                throw new DistributedLockException(
                    LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
                    String.format("無法獲取分布式鎖: %s，服務: %s", lockKey, serviceName)
                );
                
            case RETURN_NULL:
                logger.info("Returning null due to lock acquisition failure for key: {} in service: {}", 
                           lockKey, serviceName);
                return null;
                
            case FAST_FAIL:
                throw new DistributedLockException(
                    LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
                    String.format("快速失敗：無法獲取分布式鎖: %s，服務: %s", lockKey, serviceName)
                );
                
            case FALLBACK:
                logger.warn("Executing fallback logic for lock: {} in service: {}", lockKey, serviceName);
                return executeFallbackLogic(joinPoint, lockKey);
                
            case IGNORE:
                logger.warn("Ignoring lock protection and executing method for key: {} in service: {}", 
                           lockKey, serviceName);
                return joinPoint.proceed();
                
            case RETRY:
                // 重試邏輯已經在tryAcquireLock中處理
                throw new DistributedLockException(
                    LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
                    String.format("重試後仍無法獲取分布式鎖: %s，服務: %s", lockKey, serviceName)
                );
                
            default:
                throw new DistributedLockException(
                    LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
                    String.format("未知的失敗策略: %s，鎖: %s，服務: %s", strategy, lockKey, serviceName)
                );
        }
    }
    
    /**
     * 執行降級邏輯
     */
    private Object executeFallbackLogic(ProceedingJoinPoint joinPoint, String lockKey) throws Throwable {
        logger.info("Executing fallback logic for lock: {} in service: {}", lockKey, serviceName);
        
        // 這裡可以實現具體的降級邏輯，比如：
        // 1. 使用本地鎖
        // 2. 執行簡化的業務邏輯
        // 3. 返回緩存結果
        
        // 目前的實現是直接執行原方法（相當於忽略鎖保護）
        // 實際項目中應該根據具體業務需求實現降級邏輯
        return joinPoint.proceed();
    }
    
    /**
     * 釋放鎖
     */
    private void releaseLock(String lockKey, long startTime) {
        try {
            // 如果鎖已註冊到事務同步器，則不在這裡釋放，讓事務同步器處理
            if (localTransactionSynchronization != null && 
                localTransactionSynchronization.isLockRegisteredToTransaction(lockKey)) {
                logger.debug("Lock {} is registered to transaction synchronization, will be released by transaction lifecycle in service: {}", 
                            lockKey, serviceName);
                return;
            }
            
            distributedLock.unlock(lockKey);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Successfully released distributed lock: {} after {} ms in service: {}", 
                       lockKey, duration, serviceName);
            
            // 記錄鎖持有時間指標
            if (metricsCollector != null) {
                metricsCollector.recordLockHold(lockKey, serviceName, 
                    java.time.Duration.ofMillis(duration));
            }
            
        } catch (Exception e) {
            logger.error("Failed to release distributed lock: {} in service: {}", lockKey, serviceName, e);
        } finally {
            // 從事務同步器中移除鎖註冊（如果存在）
            if (localTransactionSynchronization != null) {
                localTransactionSynchronization.unregisterLockFromTransaction(lockKey);
            }
        }
    }
}