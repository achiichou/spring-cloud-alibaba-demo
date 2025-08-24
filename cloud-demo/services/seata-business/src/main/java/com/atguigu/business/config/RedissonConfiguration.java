package com.atguigu.business.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置類
 * 設置Redisson客戶端，支持跨服務分布式鎖
 */
@Configuration
@EnableConfigurationProperties(DistributedLockProperties.class)
public class RedissonConfiguration {
    
    @Autowired
    private DistributedLockProperties distributedLockProperties;
    
    /**
     * 創建Redisson客戶端Bean
     * 支持從application.yml讀取Redis連接配置
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        
        DistributedLockProperties.Redis redisProps = distributedLockProperties.getRedis();
        
        // 配置單機模式Redis連接
        config.useSingleServer()
              .setAddress("redis://" + redisProps.getHost() + ":" + redisProps.getPort())
              .setDatabase(redisProps.getDatabase())
              .setTimeout(redisProps.getTimeout())
              .setConnectionPoolSize(redisProps.getConnectionPoolSize())
              .setConnectionMinimumIdleSize(redisProps.getConnectionMinimumIdleSize());
        
        // 如果配置了密碼，則設置密碼
        if (redisProps.getPassword() != null && !redisProps.getPassword().trim().isEmpty()) {
            config.useSingleServer().setPassword(redisProps.getPassword());
        }
        
        // 設置編碼器
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());
        
        // 設置線程池大小
        config.setThreads(16);
        config.setNettyThreads(32);
        
        return Redisson.create(config);
    }
    
    /**
     * 獲取分布式鎖配置屬性
     */
    @Bean
    public DistributedLockProperties distributedLockProperties() {
        return distributedLockProperties;
    }
}