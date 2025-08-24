package com.atguigu.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式鎖配置屬性類
 * 支持從application.yml讀取Redis連接配置和跨服務鎖的特殊參數
 */
@Component
@ConfigurationProperties(prefix = "distributed.lock")
public class DistributedLockProperties {
    
    private Redis redis = new Redis();
    private Lock lock = new Lock();
    
    public Redis getRedis() {
        return redis;
    }
    
    public void setRedis(Redis redis) {
        this.redis = redis;
    }
    
    public Lock getLock() {
        return lock;
    }
    
    public void setLock(Lock lock) {
        this.lock = lock;
    }
    
    /**
     * Redis連接配置
     */
    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private int timeout = 3000;
        private int connectionPoolSize = 10;
        private int connectionMinimumIdleSize = 5;
        
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public int getDatabase() {
            return database;
        }
        
        public void setDatabase(int database) {
            this.database = database;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }
        
        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }
        
        public int getConnectionMinimumIdleSize() {
            return connectionMinimumIdleSize;
        }
        
        public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
            this.connectionMinimumIdleSize = connectionMinimumIdleSize;
        }
    }
    
    /**
     * 分布式鎖配置
     */
    public static class Lock {
        private long defaultWaitTime = 5;
        private long defaultLeaseTime = 30;
        private String keyPrefix = "distributed:lock:storage:";
        private boolean enableMonitoring = true;
        private boolean crossServiceLock = true;
        private String serviceIdentifier = "seata-storage";
        private int maxRetryTimes = 3;
        private long retryInterval = 1000;
        
        public long getDefaultWaitTime() {
            return defaultWaitTime;
        }
        
        public void setDefaultWaitTime(long defaultWaitTime) {
            this.defaultWaitTime = defaultWaitTime;
        }
        
        public long getDefaultLeaseTime() {
            return defaultLeaseTime;
        }
        
        public void setDefaultLeaseTime(long defaultLeaseTime) {
            this.defaultLeaseTime = defaultLeaseTime;
        }
        
        public String getKeyPrefix() {
            return keyPrefix;
        }
        
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
        
        public boolean isEnableMonitoring() {
            return enableMonitoring;
        }
        
        public void setEnableMonitoring(boolean enableMonitoring) {
            this.enableMonitoring = enableMonitoring;
        }
        
        public boolean isCrossServiceLock() {
            return crossServiceLock;
        }
        
        public void setCrossServiceLock(boolean crossServiceLock) {
            this.crossServiceLock = crossServiceLock;
        }
        
        public String getServiceIdentifier() {
            return serviceIdentifier;
        }
        
        public void setServiceIdentifier(String serviceIdentifier) {
            this.serviceIdentifier = serviceIdentifier;
        }
        
        public int getMaxRetryTimes() {
            return maxRetryTimes;
        }
        
        public void setMaxRetryTimes(int maxRetryTimes) {
            this.maxRetryTimes = maxRetryTimes;
        }
        
        public long getRetryInterval() {
            return retryInterval;
        }
        
        public void setRetryInterval(long retryInterval) {
            this.retryInterval = retryInterval;
        }
    }
}