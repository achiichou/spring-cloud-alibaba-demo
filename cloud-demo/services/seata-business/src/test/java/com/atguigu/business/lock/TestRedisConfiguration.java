package com.atguigu.business.lock;

import com.atguigu.business.config.DistributedLockProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 測試用Redis配置
 * 為分布式鎖測試提供Redis連接配置
 */
@TestConfiguration
public class TestRedisConfiguration {

    @Bean
    @Primary
    public RedissonClient testRedissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://localhost:6370")
              .setConnectionMinimumIdleSize(1)
              .setConnectionPoolSize(2)
              .setTimeout(3000)
              .setRetryAttempts(3)
              .setRetryInterval(1500);
        
        return Redisson.create(config);
    }

    @Bean
    @Primary
    public DistributedLockProperties testDistributedLockProperties() {
        DistributedLockProperties properties = new DistributedLockProperties();
        
        // 設置Redis配置
        DistributedLockProperties.Redis redis = new DistributedLockProperties.Redis();
        redis.setHost("localhost");
        redis.setPort(6370);
        redis.setTimeout(3000);
        properties.setRedis(redis);
        
        // 設置鎖配置
        DistributedLockProperties.Lock lock = new DistributedLockProperties.Lock();
        lock.setDefaultWaitTime(5L);
        lock.setDefaultLeaseTime(30L);
        lock.setKeyPrefix("test:distributed:lock:");
        lock.setEnableMonitoring(true);
        lock.setCrossServiceLock(true);
        properties.setLock(lock);
        
        return properties;
    }
}