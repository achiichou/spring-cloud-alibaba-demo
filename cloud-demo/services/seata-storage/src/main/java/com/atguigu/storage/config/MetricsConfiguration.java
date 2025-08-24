package com.atguigu.storage.config;

import com.atguigu.storage.lock.CrossServiceLockMetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指標配置類
 * 配置Spring Boot Actuator和Micrometer指標系統
 */
@Configuration
public class MetricsConfiguration {
    
    /**
     * 自定義MeterRegistry配置
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "seata-storage");
    }
    
    /**
     * 確保CrossServiceLockMetricsCollector被正確初始化
     */
    @Bean
    public CrossServiceLockMetricsCollector crossServiceLockMetricsCollector(MeterRegistry meterRegistry) {
        return new CrossServiceLockMetricsCollector();
    }
}