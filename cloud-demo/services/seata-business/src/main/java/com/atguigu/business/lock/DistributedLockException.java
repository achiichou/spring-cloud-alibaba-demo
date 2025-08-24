package com.atguigu.business.lock;

/**
 * 分布式鎖異常類
 * 用於封裝分布式鎖操作中發生的各種異常情況
 */
public class DistributedLockException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final LockErrorCode errorCode;
    private final String lockKey;
    private final String serviceSource;
    private final long timestamp;
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     */
    public DistributedLockException(LockErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.lockKey = null;
        this.serviceSource = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param message 自定義錯誤消息
     */
    public DistributedLockException(LockErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.lockKey = null;
        this.serviceSource = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param lockKey 鎖鍵
     */
    public DistributedLockException(LockErrorCode errorCode, String lockKey, String serviceSource) {
        super(String.format("%s - LockKey: %s, Service: %s", 
              errorCode.getMessage(), lockKey, serviceSource));
        this.errorCode = errorCode;
        this.lockKey = lockKey;
        this.serviceSource = serviceSource;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param lockKey 鎖鍵
     * @param serviceSource 服務來源
     * @param message 自定義錯誤消息
     */
    public DistributedLockException(LockErrorCode errorCode, String lockKey, String serviceSource, String message) {
        super(message);
        this.errorCode = errorCode;
        this.lockKey = lockKey;
        this.serviceSource = serviceSource;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param cause 原始異常
     */
    public DistributedLockException(LockErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.lockKey = null;
        this.serviceSource = null;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param lockKey 鎖鍵
     * @param serviceSource 服務來源
     * @param cause 原始異常
     */
    public DistributedLockException(LockErrorCode errorCode, String lockKey, String serviceSource, Throwable cause) {
        super(String.format("%s - LockKey: %s, Service: %s", 
              errorCode.getMessage(), lockKey, serviceSource), cause);
        this.errorCode = errorCode;
        this.lockKey = lockKey;
        this.serviceSource = serviceSource;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 構造函數
     * @param errorCode 錯誤碼
     * @param lockKey 鎖鍵
     * @param serviceSource 服務來源
     * @param message 自定義錯誤消息
     * @param cause 原始異常
     */
    public DistributedLockException(LockErrorCode errorCode, String lockKey, String serviceSource, 
                                   String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.lockKey = lockKey;
        this.serviceSource = serviceSource;
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * 獲取錯誤碼
     */
    public LockErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * 獲取鎖鍵
     */
    public String getLockKey() {
        return lockKey;
    }
    
    /**
     * 獲取服務來源
     */
    public String getServiceSource() {
        return serviceSource;
    }
    
    /**
     * 獲取異常發生時間戳
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * 判斷是否為可重試的異常
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }
    
    /**
     * 判斷是否為嚴重異常
     */
    public boolean isCritical() {
        return errorCode.isCritical();
    }
    
    /**
     * 獲取詳細的錯誤信息
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("DistributedLockException: ");
        sb.append(errorCode.toString());
        
        if (lockKey != null) {
            sb.append(", LockKey: ").append(lockKey);
        }
        
        if (serviceSource != null) {
            sb.append(", ServiceSource: ").append(serviceSource);
        }
        
        sb.append(", Timestamp: ").append(timestamp);
        
        if (getCause() != null) {
            sb.append(", Cause: ").append(getCause().getMessage());
        }
        
        return sb.toString();
    }
    
    /**
     * 創建鎖獲取超時異常
     */
    public static DistributedLockException lockAcquireTimeout(String lockKey, String serviceSource, long waitTime) {
        String message = String.format("Failed to acquire lock within %d seconds", waitTime);
        return new DistributedLockException(LockErrorCode.LOCK_ACQUIRE_TIMEOUT, lockKey, serviceSource, message);
    }
    
    /**
     * 創建鎖釋放失敗異常
     */
    public static DistributedLockException lockReleaseFailed(String lockKey, String serviceSource, Throwable cause) {
        return new DistributedLockException(LockErrorCode.LOCK_RELEASE_FAILED, lockKey, serviceSource, cause);
    }
    
    /**
     * 創建Redis連接錯誤異常
     */
    public static DistributedLockException redisConnectionError(Throwable cause) {
        return new DistributedLockException(LockErrorCode.REDIS_CONNECTION_ERROR, cause);
    }
    
    /**
     * 創建無效鎖鍵異常
     */
    public static DistributedLockException invalidLockKey(String lockKey) {
        return new DistributedLockException(LockErrorCode.INVALID_LOCK_KEY, lockKey, null);
    }
    
    /**
     * 創建鎖未持有異常
     */
    public static DistributedLockException lockNotHeld(String lockKey, String serviceSource) {
        return new DistributedLockException(LockErrorCode.LOCK_NOT_HELD, lockKey, serviceSource);
    }
    
    /**
     * 創建跨服務鎖衝突異常
     */
    public static DistributedLockException crossServiceConflict(String lockKey, String currentService, String holderService) {
        String message = String.format("Cross-service lock conflict: current service %s, holder service %s", 
                                     currentService, holderService);
        return new DistributedLockException(LockErrorCode.CROSS_SERVICE_CONFLICT, lockKey, currentService, message);
    }
    
    /**
     * 創建SpEL表達式解析錯誤異常
     */
    public static DistributedLockException spelExpressionError(String expression, Throwable cause) {
        String message = String.format("Failed to parse SpEL expression: %s", expression);
        return new DistributedLockException(LockErrorCode.SPEL_EXPRESSION_ERROR, null, null, message, cause);
    }
    
    @Override
    public String toString() {
        return getDetailedMessage();
    }
}