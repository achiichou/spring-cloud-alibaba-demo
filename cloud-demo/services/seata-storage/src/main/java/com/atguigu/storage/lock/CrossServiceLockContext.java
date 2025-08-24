package com.atguigu.storage.lock;

/**
 * 跨服務鎖上下文
 * 記錄跨服務分布式鎖的上下文信息，用於識別鎖持有者的服務來源
 */
public class CrossServiceLockContext {
    
    private String lockKey;
    private String serviceSource;
    private String businessContext;
    private long timestamp;
    private String threadId;
    private String instanceId;
    
    public CrossServiceLockContext() {
    }
    
    public CrossServiceLockContext(String lockKey, String serviceSource, String businessContext) {
        this.lockKey = lockKey;
        this.serviceSource = serviceSource;
        this.businessContext = businessContext;
        this.timestamp = System.currentTimeMillis();
        this.threadId = Thread.currentThread().getName();
        this.instanceId = generateInstanceId();
    }
    
    /**
     * 生成實例ID，用於標識具體的服務實例
     */
    private String generateInstanceId() {
        return serviceSource + "-" + System.getProperty("server.port", "unknown") + "-" + 
               System.currentTimeMillis() % 10000;
    }
    
    /**
     * 獲取鎖持有者標識
     * 格式：服務名-實例ID-線程ID
     */
    public String getLockHolder() {
        return serviceSource + "-" + instanceId + "-" + threadId;
    }
    
    // Getters and Setters
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public String getServiceSource() {
        return serviceSource;
    }
    
    public void setServiceSource(String serviceSource) {
        this.serviceSource = serviceSource;
    }
    
    public String getBusinessContext() {
        return businessContext;
    }
    
    public void setBusinessContext(String businessContext) {
        this.businessContext = businessContext;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getThreadId() {
        return threadId;
    }
    
    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    @Override
    public String toString() {
        return "CrossServiceLockContext{" +
                "lockKey='" + lockKey + '\'' +
                ", serviceSource='" + serviceSource + '\'' +
                ", businessContext='" + businessContext + '\'' +
                ", timestamp=" + timestamp +
                ", threadId='" + threadId + '\'' +
                ", instanceId='" + instanceId + '\'' +
                '}';
    }
}