package com.atguigu.business.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分布式鎖配置測試類
 * 驗證配置類是否正確加載和工作
 */
public class DistributedLockConfigurationTest {
    
    @Test
    public void testDistributedLockPropertiesCreation() {
        DistributedLockProperties properties = new DistributedLockProperties();
        
        // 測試默認Redis配置
        DistributedLockProperties.Redis redis = properties.getRedis();
        assertEquals("localhost", redis.getHost());
        assertEquals(6379, redis.getPort());
        assertEquals(0, redis.getDatabase());
        assertEquals(3000, redis.getTimeout());
        assertEquals(10, redis.getConnectionPoolSize());
        assertEquals(5, redis.getConnectionMinimumIdleSize());
        
        // 測試默認Lock配置
        DistributedLockProperties.Lock lock = properties.getLock();
        assertEquals(5, lock.getDefaultWaitTime());
        assertEquals(30, lock.getDefaultLeaseTime());
        assertEquals("distributed:lock:storage:", lock.getKeyPrefix());
        assertTrue(lock.isEnableMonitoring());
        assertTrue(lock.isCrossServiceLock());
        assertEquals("seata-business", lock.getServiceIdentifier());
        assertEquals(3, lock.getMaxRetryTimes());
        assertEquals(1000, lock.getRetryInterval());
    }
    
    @Test
    public void testDistributedLockPropertiesSetters() {
        DistributedLockProperties properties = new DistributedLockProperties();
        
        // 測試Redis配置設置
        DistributedLockProperties.Redis redis = properties.getRedis();
        redis.setHost("redis-server");
        redis.setPort(6380);
        redis.setPassword("test-password");
        redis.setDatabase(1);
        redis.setTimeout(5000);
        
        assertEquals("redis-server", redis.getHost());
        assertEquals(6380, redis.getPort());
        assertEquals("test-password", redis.getPassword());
        assertEquals(1, redis.getDatabase());
        assertEquals(5000, redis.getTimeout());
        
        // 測試Lock配置設置
        DistributedLockProperties.Lock lock = properties.getLock();
        lock.setDefaultWaitTime(10);
        lock.setDefaultLeaseTime(60);
        lock.setKeyPrefix("test:lock:");
        lock.setServiceIdentifier("test-service");
        lock.setMaxRetryTimes(5);
        
        assertEquals(10, lock.getDefaultWaitTime());
        assertEquals(60, lock.getDefaultLeaseTime());
        assertEquals("test:lock:", lock.getKeyPrefix());
        assertEquals("test-service", lock.getServiceIdentifier());
        assertEquals(5, lock.getMaxRetryTimes());
    }
    
    @Test
    public void testRedissonConfigurationCreation() {
        // 測試RedissonConfiguration類可以正常創建
        RedissonConfiguration config = new RedissonConfiguration();
        assertNotNull(config);
    }
}