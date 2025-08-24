package com.atguigu.storage.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CrossServiceLockMetricsCollector 單元測試
 */
class CrossServiceLockMetricsCollectorTest {
    
    private CrossServiceLockMetricsCollector metricsCollector;
    private MeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new CrossServiceLockMetricsCollector();
        
        // 注入依賴
        ReflectionTestUtils.setField(metricsCollector, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(metricsCollector, "serviceName", "test-service");
        
        // 初始化指標
        metricsCollector.initMetrics();
    }
    
    @Test
    void testRecordLockAcquireSuccess() {
        // 測試成功獲取鎖的記錄
        String lockKey = "test:lock:key";
        String serviceSource = "test-service";
        Duration duration = Duration.ofMillis(100);
        
        metricsCollector.recordLockAcquire(lockKey, serviceSource, true, duration);
        
        // 驗證指標是否正確記錄
        CrossServiceLockMetricsCollector.ServiceLockStats stats = 
                metricsCollector.getServiceStats(serviceSource);
        
        assertNotNull(stats);
        assertEquals(1, stats.getTotalRequests());
        assertEquals(1, stats.getSuccessfulRequests());
        assertEquals(1.0, stats.getSuccessRate(), 0.01);
        assertEquals(100.0, stats.getAverageWaitTime(), 0.01);
    }
    
    @Test
    void testRecordLockAcquireFailure() {
        // 測試獲取鎖失敗的記錄
        String lockKey = "test:lock:key";
        String serviceSource = "test-service";
        Duration duration = Duration.ofMillis(200);
        
        metricsCollector.recordLockAcquire(lockKey, serviceSource, false, duration);
        
        // 驗證指標是否正確記錄
        CrossServiceLockMetricsCollector.ServiceLockStats stats = 
                metricsCollector.getServiceStats(serviceSource);
        
        assertNotNull(stats);
        assertEquals(1, stats.getTotalRequests());
        assertEquals(0, stats.getSuccessfulRequests());
        assertEquals(0.0, stats.getSuccessRate(), 0.01);
        assertEquals(200.0, stats.getAverageWaitTime(), 0.01);
    }
    
    @Test
    void testRecordCrossServiceConflict() {
        // 測試跨服務衝突記錄
        String lockKey = "test:lock:key";
        String requestingService = "service-a";
        String holdingService = "service-b";
        
        assertDoesNotThrow(() -> {
            metricsCollector.recordCrossServiceConflict(lockKey, requestingService, holdingService);
        });
    }
    
    @Test
    void testRecordLockHold() {
        // 測試鎖持有時間記錄
        String lockKey = "test:lock:key";
        String serviceSource = "test-service";
        Duration holdDuration = Duration.ofMillis(500);
        
        metricsCollector.recordLockHold(lockKey, serviceSource, holdDuration);
        
        // 驗證持有時間統計
        CrossServiceLockMetricsCollector.ServiceLockStats stats = 
                metricsCollector.getServiceStats(serviceSource);
        
        assertNotNull(stats);
        assertEquals(500.0, stats.getAverageHoldTime(), 0.01);
    }
    
    @Test
    void testRecordLockTimeout() {
        // 測試鎖超時記錄
        String lockKey = "test:lock:key";
        String serviceSource = "test-service";
        Duration waitTime = Duration.ofSeconds(5);
        
        assertDoesNotThrow(() -> {
            metricsCollector.recordLockTimeout(lockKey, serviceSource, waitTime);
        });
    }
    
    @Test
    void testMultipleServiceStats() {
        // 測試多個服務的統計
        String service1 = "service-1";
        String service2 = "service-2";
        
        // 為service-1記錄數據
        metricsCollector.recordLockAcquire("key1", service1, true, Duration.ofMillis(100));
        metricsCollector.recordLockAcquire("key2", service1, false, Duration.ofMillis(200));
        
        // 為service-2記錄數據
        metricsCollector.recordLockAcquire("key3", service2, true, Duration.ofMillis(150));
        
        // 驗證各服務的統計
        CrossServiceLockMetricsCollector.ServiceLockStats stats1 = 
                metricsCollector.getServiceStats(service1);
        CrossServiceLockMetricsCollector.ServiceLockStats stats2 = 
                metricsCollector.getServiceStats(service2);
        
        assertNotNull(stats1);
        assertNotNull(stats2);
        
        assertEquals(2, stats1.getTotalRequests());
        assertEquals(1, stats1.getSuccessfulRequests());
        assertEquals(0.5, stats1.getSuccessRate(), 0.01);
        
        assertEquals(1, stats2.getTotalRequests());
        assertEquals(1, stats2.getSuccessfulRequests());
        assertEquals(1.0, stats2.getSuccessRate(), 0.01);
    }
    
    @Test
    void testResetStats() {
        // 測試統計重置
        String serviceSource = "test-service";
        
        // 記錄一些數據
        metricsCollector.recordLockAcquire("key1", serviceSource, true, Duration.ofMillis(100));
        metricsCollector.recordLockHold("key1", serviceSource, Duration.ofMillis(500));
        
        // 驗證數據存在
        CrossServiceLockMetricsCollector.ServiceLockStats stats = 
                metricsCollector.getServiceStats(serviceSource);
        assertNotNull(stats);
        assertEquals(1, stats.getTotalRequests());
        
        // 重置統計
        metricsCollector.resetStats();
        
        // 驗證數據已清空
        CrossServiceLockMetricsCollector.ServiceLockStats resetStats = 
                metricsCollector.getServiceStats(serviceSource);
        assertNull(resetStats);
    }
}