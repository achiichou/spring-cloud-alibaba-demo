package com.atguigu.business.config;

import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.LockMonitorServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分布式鎖監控配置類
 * 
 * @author Kiro
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "distributed.lock", name = "enable-monitoring", havingValue = "true", matchIfMissing = true)
public class LockMonitoringConfiguration {
    
    /**
     * 配置鎖監控服務Bean
     * 只有在啟用監控時才創建
     */
    @Bean
    @ConditionalOnProperty(prefix = "distributed.lock", name = "enable-monitoring", havingValue = "true", matchIfMissing = true)
    public LockMonitorService lockMonitorService() {
        return new LockMonitorServiceImpl();
    }
}