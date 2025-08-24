package com.atguigu.storage.lock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 跨服務分布式鎖指標收集器
 * 集成Spring Boot Actuator指標系統，記錄按服務分組的鎖獲取成功率、
 * 跨服務鎖衝突次數和平均等待時間
 */
@Component
public class CrossServiceLockMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(CrossServiceLockMetricsCollector.class);

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${spring.application.name:seata-storage}")
    private String serviceName;

    // 計數器
    private Counter lockAcquireSuccessCounter;
    private Counter lockAcquireFailureCounter;
    private Counter crossServiceConflictCounter;
    private Counter lockTimeoutCounter;

    // 計時器
    private Timer lockAcquireTimer;
    private Timer lockHoldTimer;

    // 統計數據存儲
    private final ConcurrentHashMap<String, ServiceLockStats> serviceStatsMap = new ConcurrentHashMap<>();
    private final AtomicLong totalLockRequests = new AtomicLong(0);
    private final AtomicLong totalSuccessfulLocks = new AtomicLong(0);
    private final AtomicLong totalFailedLocks = new AtomicLong(0);
    private final AtomicLong totalCrossServiceConflicts = new AtomicLong(0);

    @PostConstruct
    public void initMetrics() {
        // 初始化計數器
        lockAcquireSuccessCounter = Counter.builder("distributed.lock.acquire.success")
                .description("成功獲取分布式鎖的次數")
                .tag("service", serviceName)
                .register(meterRegistry);

        lockAcquireFailureCounter = Counter.builder("distributed.lock.acquire.failure")
                .description("獲取分布式鎖失敗的次數")
                .tag("service", serviceName)
                .register(meterRegistry);

        crossServiceConflictCounter = Counter.builder("distributed.lock.cross.service.conflict")
                .description("跨服務鎖衝突次數")
                .tag("service", serviceName)
                .register(meterRegistry);

        lockTimeoutCounter = Counter.builder("distributed.lock.timeout")
                .description("鎖超時次數")
                .tag("service", serviceName)
                .register(meterRegistry);

        // 初始化計時器
        lockAcquireTimer = Timer.builder("distributed.lock.acquire.duration")
                .description("獲取鎖的耗時")
                .tag("service", serviceName)
                .register(meterRegistry);

        lockHoldTimer = Timer.builder("distributed.lock.hold.duration")
                .description("持有鎖的時間")
                .tag("service", serviceName)
                .register(meterRegistry);

        // 註冊Gauge指標
        Gauge.builder("distributed.lock.success.rate", this::calculateSuccessRate)
                .description("鎖獲取成功率")
                .tag("service", serviceName)
                .register(meterRegistry);

        Gauge.builder("distributed.lock.active.count", this::getActiveLockCount)
                .description("當前活躍鎖數量")
                .tag("service", serviceName)
                .register(meterRegistry);

        Gauge.builder("distributed.lock.cross.service.conflict.rate", this::calculateConflictRate)
                .description("跨服務鎖衝突率")
                .tag("service", serviceName)
                .register(meterRegistry);

        logger.info("CrossServiceLockMetricsCollector initialized for service: {}", serviceName);
    }

    /**
     * 記錄鎖獲取操作
     * 
     * @param lockKey       鎖鍵
     * @param serviceSource 服務來源
     * @param success       是否成功
     * @param duration      耗時
     */
    public void recordLockAcquire(String lockKey, String serviceSource, boolean success, Duration duration) {
        totalLockRequests.incrementAndGet();

        if (success) {
            lockAcquireSuccessCounter.increment();
            totalSuccessfulLocks.incrementAndGet();
            lockAcquireTimer.record(duration);

            // 更新服務統計
            updateServiceStats(serviceSource, true, duration.toMillis());

            logger.debug("Lock acquire success recorded: key={}, service={}, duration={}ms",
                    lockKey, serviceSource, duration.toMillis());
        } else {
            lockAcquireFailureCounter.increment();
            totalFailedLocks.incrementAndGet();

            // 更新服務統計
            updateServiceStats(serviceSource, false, duration.toMillis());

            logger.debug("Lock acquire failure recorded: key={}, service={}, duration={}ms",
                    lockKey, serviceSource, duration.toMillis());
        }
    }

    /**
     * 記錄跨服務鎖衝突
     * 
     * @param lockKey           鎖鍵
     * @param requestingService 請求服務
     * @param holdingService    持有鎖的服務
     */
    public void recordCrossServiceConflict(String lockKey, String requestingService, String holdingService) {
        crossServiceConflictCounter.increment();
        totalCrossServiceConflicts.incrementAndGet();

        // 為請求服務和持有服務都記錄衝突
        Counter.builder("distributed.lock.cross.service.conflict.detail")
                .description("詳細的跨服務鎖衝突記錄")
                .tag("requesting.service", requestingService)
                .tag("holding.service", holdingService)
                .register(meterRegistry)
                .increment();

        logger.warn("Cross-service lock conflict recorded: key={}, requesting={}, holding={}",
                lockKey, requestingService, holdingService);
    }

    /**
     * 記錄鎖持有時間
     * 
     * @param lockKey       鎖鍵
     * @param serviceSource 服務來源
     * @param holdDuration  持有時間
     */
    public void recordLockHold(String lockKey, String serviceSource, Duration holdDuration) {
        lockHoldTimer.record(holdDuration);

        // 更新服務統計中的平均持有時間
        ServiceLockStats stats = serviceStatsMap.computeIfAbsent(serviceSource, k -> new ServiceLockStats());
        stats.updateHoldTime(holdDuration.toMillis());

        logger.debug("Lock hold time recorded: key={}, service={}, duration={}ms",
                lockKey, serviceSource, holdDuration.toMillis());
    }

    /**
     * 記錄鎖超時
     * 
     * @param lockKey       鎖鍵
     * @param serviceSource 服務來源
     * @param waitTime      等待時間
     */
    public void recordLockTimeout(String lockKey, String serviceSource, Duration waitTime) {
        lockTimeoutCounter.increment();

        Counter.builder("distributed.lock.timeout.by.service")
                .description("按服務分組的鎖超時次數")
                .tag("service", serviceSource)
                .register(meterRegistry)
                .increment();

        logger.warn("Lock timeout recorded: key={}, service={}, waitTime={}ms",
                lockKey, serviceSource, waitTime.toMillis());
    }

    /**
     * 更新服務統計信息
     */
    private void updateServiceStats(String serviceSource, boolean success, long duration) {
        ServiceLockStats stats = serviceStatsMap.computeIfAbsent(serviceSource, k -> new ServiceLockStats());
        stats.updateStats(success, duration);
    }

    /**
     * 計算總體成功率
     */
    private double calculateSuccessRate() {
        long total = totalLockRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalSuccessfulLocks.get() / total;
    }

    /**
     * 獲取當前活躍鎖數量（這裡返回一個估算值）
     */
    private double getActiveLockCount() {
        // 這裡可以通過Redis查詢實際的活躍鎖數量
        // 為了簡化，返回一個基於統計的估算值
        return serviceStatsMap.values().stream()
                .mapToLong(ServiceLockStats::getEstimatedActiveLocks)
                .sum();
    }

    /**
     * 計算跨服務衝突率
     */
    private double calculateConflictRate() {
        long total = totalLockRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) totalCrossServiceConflicts.get() / total;
    }

    /**
     * 獲取按服務分組的統計信息
     */
    public ServiceLockStats getServiceStats(String serviceSource) {
        return serviceStatsMap.get(serviceSource);
    }

    /**
     * 獲取所有服務的統計信息
     */
    public ConcurrentHashMap<String, ServiceLockStats> getAllServiceStats() {
        return new ConcurrentHashMap<>(serviceStatsMap);
    }

    /**
     * 記錄事務鎖持有時間
     * 
     * @param lockKey       鎖鍵
     * @param serviceSource 服務來源
     * @param transactionId 事務ID
     * @param holdDuration  持有時間
     */
    public void recordTransactionLockHoldTime(String lockKey, String serviceSource, String transactionId,
            Duration holdDuration) {
        Timer.builder("distributed.lock.transaction.hold.time")
                .description("事務鎖持有時間")
                .tag("service", serviceSource)
                .tag("transaction", transactionId != null ? transactionId : "unknown")
                .register(meterRegistry)
                .record(holdDuration);

        logger.debug("Transaction lock hold time recorded: key={}, service={}, transaction={}, holdTime={}ms",
                lockKey, serviceSource, transactionId, holdDuration.toMillis());
    }

    /**
     * 記錄鎖丟失事件
     * 
     * @param lockKey       鎖鍵
     * @param serviceSource 服務來源
     * @param transactionId 事務ID
     */
    public void recordLockLost(String lockKey, String serviceSource, String transactionId) {
        Counter.builder("distributed.lock.lost")
                .description("鎖丟失次數")
                .tag("service", serviceSource)
                .tag("transaction", transactionId != null ? transactionId : "unknown")
                .register(meterRegistry)
                .increment();

        logger.error("Lock lost event recorded: key={}, service={}, transaction={}",
                lockKey, serviceSource, transactionId);
    }

    /**
     * 記錄批量鎖釋放
     * 
     * @param serviceSource 服務來源
     * @param transactionId 事務ID
     * @param reason        釋放原因
     * @param successCount  成功數量
     * @param failureCount  失敗數量
     * @param totalTime     總耗時
     */
    public void recordBatchLockRelease(String serviceSource, String transactionId, String reason,
            int successCount, int failureCount, Duration totalTime) {
        Counter.builder("distributed.lock.batch.release.success")
                .description("批量鎖釋放成功次數")
                .tag("service", serviceSource)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment(successCount);

        if (failureCount > 0) {
            Counter.builder("distributed.lock.batch.release.failure")
                    .description("批量鎖釋放失敗次數")
                    .tag("service", serviceSource)
                    .tag("reason", reason)
                    .register(meterRegistry)
                    .increment(failureCount);
        }

        Timer.builder("distributed.lock.batch.release.time")
                .description("批量鎖釋放耗時")
                .tag("service", serviceSource)
                .tag("reason", reason)
                .register(meterRegistry)
                .record(totalTime);

        logger.info(
                "Batch lock release recorded: service={}, transaction={}, reason={}, success={}, failure={}, time={}ms",
                serviceSource, transactionId, reason, successCount, failureCount, totalTime.toMillis());
    }

    /**
     * 重置統計信息
     */
    public void resetStats() {
        serviceStatsMap.clear();
        totalLockRequests.set(0);
        totalSuccessfulLocks.set(0);
        totalFailedLocks.set(0);
        totalCrossServiceConflicts.set(0);

        logger.info("Lock metrics statistics reset for service: {}", serviceName);
    }

    /**
     * 服務鎖統計信息內部類
     */
    public static class ServiceLockStats {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong successfulRequests = new AtomicLong(0);
        private final AtomicLong totalWaitTime = new AtomicLong(0);
        private final AtomicLong totalHoldTime = new AtomicLong(0);
        private final AtomicLong holdCount = new AtomicLong(0);

        public void updateStats(boolean success, long waitTime) {
            totalRequests.incrementAndGet();
            totalWaitTime.addAndGet(waitTime);

            if (success) {
                successfulRequests.incrementAndGet();
            }
        }

        public void updateHoldTime(long holdTime) {
            totalHoldTime.addAndGet(holdTime);
            holdCount.incrementAndGet();
        }

        public double getSuccessRate() {
            long total = totalRequests.get();
            return total == 0 ? 0.0 : (double) successfulRequests.get() / total;
        }

        public double getAverageWaitTime() {
            long total = totalRequests.get();
            return total == 0 ? 0.0 : (double) totalWaitTime.get() / total;
        }

        public double getAverageHoldTime() {
            long count = holdCount.get();
            return count == 0 ? 0.0 : (double) totalHoldTime.get() / count;
        }

        public long getTotalRequests() {
            return totalRequests.get();
        }

        public long getSuccessfulRequests() {
            return successfulRequests.get();
        }

        public long getEstimatedActiveLocks() {
            // 基於最近的成功請求數估算活躍鎖數量
            return Math.max(0, successfulRequests.get() - (totalRequests.get() - successfulRequests.get()));
        }
    }
}