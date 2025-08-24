package com.atguigu.business.performance;

import com.atguigu.business.bean.StorageTbl;
import com.atguigu.business.config.DistributedLockProperties;

import com.atguigu.business.lock.DistributedLock;
import com.atguigu.business.lock.DistributedLockException;
import com.atguigu.business.lock.LockErrorCode;
import com.atguigu.business.mapper.storage.StorageTblMapper;
import com.atguigu.business.service.BusinessStorageService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import redis.embedded.RedisServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跨服務併發性能測試
 * 模擬兩個服務同時發起1000併發請求
 * 驗證跨服務鎖獲取響應時間在100毫秒內
 * 測試高併發場景下跨服務系統的穩定性
 */
@Slf4j
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataCoreAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration,com.alibaba.cloud.seata.SeataAutoConfiguration"
})
@Import({CrossServiceConcurrentTest.PerformanceTestRedisConfiguration.class})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrossServiceConcurrentTest {

    private static RedisServer redisServer;

    @Autowired
    private BusinessStorageService businessStorageService;

    @Autowired
    private StorageTblMapper storageTblMapper;

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private DistributedLockProperties lockProperties;

    // 測試常量
    private static final String PERFORMANCE_TEST_COMMODITY = "PERF_TEST_COMMODITY";
    private static final int INITIAL_STOCK = 10000; // 足夠大的初始庫存
    private static final int TOTAL_CONCURRENT_REQUESTS = 1000;
    private static final int THREAD_POOL_SIZE = 100;
    private static final long MAX_RESPONSE_TIME_MS = 100; // 100毫秒響應時間要求
    private static final int REDIS_PORT = 6370;

    @BeforeAll
    static void setUpRedisServer() throws Exception {
        log.info("啟動嵌入式Redis服務器");
        redisServer = new RedisServer(REDIS_PORT);
        redisServer.start();
        log.info("嵌入式Redis服務器已啟動在端口: {}", REDIS_PORT);
    }

    @AfterAll
    static void tearDownRedisServer() {
        if (redisServer != null) {
            log.info("關閉嵌入式Redis服務器");
            redisServer.stop();
            log.info("嵌入式Redis服務器已關閉");
        }
    }

    @BeforeEach
    void setUp() {
        log.info("開始設置性能測試環境");
        
        // 清理測試數據
        cleanupTestData();
        
        // 初始化測試數據
        initializePerformanceTestData();
        
        // 清理Redis中的鎖
        cleanupRedisLocks();
        
        log.info("性能測試環境設置完成");
    }

    @AfterEach
    void tearDown() {
        log.info("開始清理性能測試環境");
        
        // 清理測試數據
        cleanupTestData();
        
        // 清理Redis中的鎖
        cleanupRedisLocks();
        
        log.info("性能測試環境清理完成");
    }

    /**
     * 測試1000併發請求的性能表現
     * 驗證響應時間和系統穩定性
     */
    @Test
    @Order(1)
    @DisplayName("測試1000併發請求的跨服務性能表現")
    void testThousandConcurrentRequestsPerformance() throws InterruptedException {
        log.info("開始1000併發請求性能測試");

        // 性能指標收集器
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        // 創建線程池
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(TOTAL_CONCURRENT_REQUESTS);
        
        // 記錄開始時間
        long testStartTime = System.currentTimeMillis();
        
        // 提交所有任務
        for (int i = 0; i < TOTAL_CONCURRENT_REQUESTS; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // 等待統一開始信號
                    startLatch.await();
                    
                    // 記錄請求開始時間
                    long requestStartTime = System.nanoTime();
                    
                    try {
                        if (requestId % 2 == 0) {
                            // 模擬business服務請求
                            executeBusinessServiceRequest(requestId, metrics);
                        } else {
                            // 模擬storage服務請求
                            executeStorageServiceRequest(requestId, metrics);
                        }
                        
                        // 記錄成功請求的響應時間
                        long responseTime = (System.nanoTime() - requestStartTime) / 1_000_000; // 轉換為毫秒
                        metrics.recordSuccessfulRequest(responseTime);
                        
                    } catch (Exception e) {
                        // 記錄失敗請求
                        long responseTime = (System.nanoTime() - requestStartTime) / 1_000_000;
                        metrics.recordFailedRequest(responseTime, e);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    metrics.recordFailedRequest(0, e);
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 發出開始信號
        log.info("發出併發測試開始信號");
        startLatch.countDown();
        
        // 等待所有請求完成，設置合理的超時時間
        boolean completed = completeLatch.await(120, TimeUnit.SECONDS);
        assertTrue(completed, "併發測試應該在120秒內完成");
        
        // 關閉線程池
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "線程池應該在30秒內關閉");
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        // 輸出性能測試結果
        logPerformanceResults(metrics, totalTestTime);
        
        // 驗證性能要求
        validatePerformanceRequirements(metrics, totalTestTime);
        
        // 驗證數據一致性
        validateDataConsistency(metrics);
        
        log.info("1000併發請求性能測試完成");
    }

    /**
     * 測試跨服務鎖獲取響應時間
     * 專門測試鎖操作的性能
     */
    @Test
    @Order(2)
    @DisplayName("測試跨服務鎖獲取響應時間")
    void testCrossServiceLockAcquisitionResponseTime() throws InterruptedException {
        log.info("開始跨服務鎖獲取響應時間測試");

        int lockTestRequests = 500;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(lockTestRequests);
        
        List<Long> lockAcquisitionTimes = new CopyOnWriteArrayList<>();
        AtomicInteger successfulLockAcquisitions = new AtomicInteger(0);
        AtomicInteger failedLockAcquisitions = new AtomicInteger(0);
        
        for (int i = 0; i < lockTestRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    String lockKey = lockProperties.getLock().getKeyPrefix() + "lock_perf_test_" + (requestId % 10);
                    
                    long startTime = System.nanoTime();
                    boolean acquired = distributedLock.tryLock(lockKey, 5, 10);
                    long acquisitionTime = (System.nanoTime() - startTime) / 1_000_000; // 轉換為毫秒
                    
                    lockAcquisitionTimes.add(acquisitionTime);
                    
                    if (acquired) {
                        successfulLockAcquisitions.incrementAndGet();
                        try {
                            // 模擬短暫的業務處理
                            Thread.sleep(1);
                        } finally {
                            distributedLock.unlock(lockKey);
                        }
                    } else {
                        failedLockAcquisitions.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    failedLockAcquisitions.incrementAndGet();
                    log.warn("鎖獲取測試異常 - 請求ID: {}, 錯誤: {}", requestId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(60, TimeUnit.SECONDS), "鎖性能測試應該在60秒內完成");
        executor.shutdown();
        
        // 計算鎖獲取性能統計
        double averageLockTime = lockAcquisitionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        
        long maxLockTime = lockAcquisitionTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0L);
        
        long minLockTime = lockAcquisitionTimes.stream()
            .mapToLong(Long::longValue)
            .min()
            .orElse(0L);
        
        log.info("鎖獲取性能統計:");
        log.info("  總請求數: {}", lockTestRequests);
        log.info("  成功獲取鎖: {}", successfulLockAcquisitions.get());
        log.info("  獲取鎖失敗: {}", failedLockAcquisitions.get());
        log.info("  平均鎖獲取時間: {:.2f}ms", averageLockTime);
        log.info("  最大鎖獲取時間: {}ms", maxLockTime);
        log.info("  最小鎖獲取時間: {}ms", minLockTime);
        
        // 驗證鎖獲取性能要求
        assertTrue(averageLockTime <= MAX_RESPONSE_TIME_MS, 
            String.format("平均鎖獲取時間(%.2fms)應該在%dms以內", averageLockTime, MAX_RESPONSE_TIME_MS));
        
        assertTrue(successfulLockAcquisitions.get() > 0, "應該有成功獲取的鎖");
        
        log.info("跨服務鎖獲取響應時間測試完成");
    }

    /**
     * 測試高併發場景下的系統穩定性
     * 長時間運行測試
     */
    @Test
    @Order(3)
    @DisplayName("測試高併發場景下跨服務系統穩定性")
    void testHighConcurrencySystemStability() throws InterruptedException {
        log.info("開始高併發系統穩定性測試");

        int stabilityTestDuration = 30; // 30秒穩定性測試
        int requestsPerSecond = 50;
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        long testStartTime = System.currentTimeMillis();
        long testEndTime = testStartTime + (stabilityTestDuration * 1000L);
        
        // 持續發送請求直到測試時間結束
        while (System.currentTimeMillis() < testEndTime) {
            for (int i = 0; i < requestsPerSecond && System.currentTimeMillis() < testEndTime; i++) {
                final int requestId = totalRequests.incrementAndGet();
                
                executor.submit(() -> {
                    long requestStartTime = System.nanoTime();
                    try {
                        if (requestId % 2 == 0) {
                            businessStorageService.directDeduct(
                                PERFORMANCE_TEST_COMMODITY, 
                                1, 
                                "stability-test-business-" + requestId
                            );
                        } else {
                            simulateStorageServiceDeduction(PERFORMANCE_TEST_COMMODITY, 1, requestId);
                        }
                        
                        successfulRequests.incrementAndGet();
                        
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                        // 在穩定性測試中，部分失敗是可以接受的
                    } finally {
                        long responseTime = (System.nanoTime() - requestStartTime) / 1_000_000;
                        totalResponseTime.addAndGet(responseTime);
                    }
                });
            }
            
            // 控制請求頻率
            Thread.sleep(1000);
        }
        
        // 等待所有請求完成
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "穩定性測試應該在30秒內完成所有請求");
        
        long actualTestDuration = System.currentTimeMillis() - testStartTime;
        double averageResponseTime = totalRequests.get() > 0 ? 
            (double) totalResponseTime.get() / totalRequests.get() : 0.0;
        
        log.info("系統穩定性測試結果:");
        log.info("  測試持續時間: {}ms", actualTestDuration);
        log.info("  總請求數: {}", totalRequests.get());
        log.info("  成功請求數: {}", successfulRequests.get());
        log.info("  失敗請求數: {}", failedRequests.get());
        log.info("  成功率: {:.2f}%", (double) successfulRequests.get() / totalRequests.get() * 100);
        log.info("  平均響應時間: {:.2f}ms", averageResponseTime);
        
        // 驗證系統穩定性要求
        assertTrue(totalRequests.get() > 0, "應該有發送請求");
        assertTrue(successfulRequests.get() > 0, "應該有成功的請求");
        
        double successRate = (double) successfulRequests.get() / totalRequests.get();
        assertTrue(successRate > 0.8, "成功率應該大於80%"); // 允許20%的失敗率
        
        assertTrue(averageResponseTime <= MAX_RESPONSE_TIME_MS * 2, 
            "平均響應時間應該在合理範圍內"); // 穩定性測試允許更寬鬆的響應時間
        
        log.info("高併發系統穩定性測試完成");
    }

    // ==================== 輔助方法 ====================

    /**
     * 執行business服務請求
     */
    private void executeBusinessServiceRequest(int requestId, PerformanceMetrics metrics) {
        try {
            businessStorageService.directDeduct(
                PERFORMANCE_TEST_COMMODITY, 
                1, 
                "perf-test-business-" + requestId
            );
            metrics.incrementBusinessServiceSuccess();
        } catch (Exception e) {
            metrics.incrementBusinessServiceFailure();
            throw e;
        }
    }

    /**
     * 執行storage服務請求（模擬）
     */
    private void executeStorageServiceRequest(int requestId, PerformanceMetrics metrics) {
        try {
            simulateStorageServiceDeduction(PERFORMANCE_TEST_COMMODITY, 1, requestId);
            metrics.incrementStorageServiceSuccess();
        } catch (Exception e) {
            metrics.incrementStorageServiceFailure();
            throw e;
        }
    }

    /**
     * 模擬storage服務的庫存扣減操作
     */
    private void simulateStorageServiceDeduction(String commodityCode, int count, int requestId) {
        String lockKey = lockProperties.getLock().getKeyPrefix() + commodityCode;
        
        if (distributedLock.tryLock(lockKey, 5, 30)) {
            try {
                StorageTbl storage = storageTblMapper.selectByCommodityCode(commodityCode);
                if (storage == null) {
                    throw new RuntimeException("商品不存在: " + commodityCode);
                }
                if (storage.getCount() < count) {
                    throw new RuntimeException("庫存不足");
                }
                
                storageTblMapper.deduct(commodityCode, count);
                
            } finally {
                distributedLock.unlock(lockKey);
            }
        } else {
            throw new DistributedLockException(LockErrorCode.LOCK_ACQUIRE_TIMEOUT, 
                "Storage服務獲取分布式鎖失敗 - 請求ID: " + requestId);
        }
    }

    /**
     * 記錄性能測試結果
     */
    private void logPerformanceResults(PerformanceMetrics metrics, long totalTestTime) {
        log.info("=== 性能測試結果 ===");
        log.info("總測試時間: {}ms", totalTestTime);
        log.info("總請求數: {}", TOTAL_CONCURRENT_REQUESTS);
        log.info("成功請求數: {}", metrics.getSuccessfulRequests());
        log.info("失敗請求數: {}", metrics.getFailedRequests());
        log.info("成功率: {:.2f}%", metrics.getSuccessRate());
        log.info("Business服務成功: {}", metrics.getBusinessServiceSuccess());
        log.info("Business服務失敗: {}", metrics.getBusinessServiceFailure());
        log.info("Storage服務成功: {}", metrics.getStorageServiceSuccess());
        log.info("Storage服務失敗: {}", metrics.getStorageServiceFailure());
        log.info("平均響應時間: {:.2f}ms", metrics.getAverageResponseTime());
        log.info("最大響應時間: {}ms", metrics.getMaxResponseTime());
        log.info("最小響應時間: {}ms", metrics.getMinResponseTime());
        log.info("吞吐量: {:.2f} requests/second", (double) TOTAL_CONCURRENT_REQUESTS / totalTestTime * 1000);
    }

    /**
     * 驗證性能要求
     */
    private void validatePerformanceRequirements(PerformanceMetrics metrics, long totalTestTime) {
        // 驗證響應時間要求
        assertTrue(metrics.getAverageResponseTime() <= MAX_RESPONSE_TIME_MS, 
            String.format("平均響應時間(%.2fms)應該在%dms以內", 
                metrics.getAverageResponseTime(), MAX_RESPONSE_TIME_MS));
        
        // 驗證成功率要求
        assertTrue(metrics.getSuccessRate() > 70.0, 
            String.format("成功率(%.2f%%)應該大於70%%", metrics.getSuccessRate()));
        
        // 驗證總測試時間合理性
        assertTrue(totalTestTime < 60000, "總測試時間應該在60秒內");
        
        // 驗證兩個服務都有成功的請求
        assertTrue(metrics.getBusinessServiceSuccess() > 0, "Business服務應該有成功的請求");
        assertTrue(metrics.getStorageServiceSuccess() > 0, "Storage服務應該有成功的請求");
    }

    /**
     * 驗證數據一致性
     */
    private void validateDataConsistency(PerformanceMetrics metrics) {
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(PERFORMANCE_TEST_COMMODITY);
        assertNotNull(finalStorage, "庫存記錄不應為null");
        
        int expectedFinalStock = INITIAL_STOCK - metrics.getSuccessfulRequests();
        assertEquals(expectedFinalStock, finalStorage.getCount(), 
            "最終庫存應該等於初始庫存減去成功請求數");
        
        assertTrue(finalStorage.getCount() >= 0, "庫存不應該為負數");
        
        log.info("數據一致性驗證通過 - 最終庫存: {}, 預期庫存: {}", 
            finalStorage.getCount(), expectedFinalStock);
    }

    /**
     * 初始化性能測試數據
     */
    private void initializePerformanceTestData() {
        try {
            StorageTbl storage = new StorageTbl();
            storage.setCommodityCode(PERFORMANCE_TEST_COMMODITY);
            storage.setCount(INITIAL_STOCK);
            storageTblMapper.insert(storage);
            
            log.info("性能測試數據初始化完成 - 商品: {}, 初始庫存: {}", 
                PERFORMANCE_TEST_COMMODITY, INITIAL_STOCK);
        } catch (Exception e) {
            log.warn("初始化性能測試數據時出現異常: {}", e.getMessage());
        }
    }

    /**
     * 清理測試數據
     */
    private void cleanupTestData() {
        try {
            storageTblMapper.deleteByCommodityCode(PERFORMANCE_TEST_COMMODITY);
            log.debug("性能測試數據清理完成");
        } catch (Exception e) {
            log.warn("清理性能測試數據時出現異常: {}", e.getMessage());
        }
    }

    /**
     * 清理Redis中的鎖
     */
    private void cleanupRedisLocks() {
        try {
            String keyPattern = lockProperties.getLock().getKeyPrefix() + "*";
            redissonClient.getKeys().deleteByPattern(keyPattern);
            log.debug("Redis鎖清理完成");
        } catch (Exception e) {
            log.warn("清理Redis鎖時出現異常: {}", e.getMessage());
        }
    }

    /**
     * 性能指標收集器
     */
    private static class PerformanceMetrics {
        private final AtomicInteger successfulRequests = new AtomicInteger(0);
        private final AtomicInteger failedRequests = new AtomicInteger(0);
        private final AtomicInteger businessServiceSuccess = new AtomicInteger(0);
        private final AtomicInteger businessServiceFailure = new AtomicInteger(0);
        private final AtomicInteger storageServiceSuccess = new AtomicInteger(0);
        private final AtomicInteger storageServiceFailure = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong maxResponseTime = new AtomicLong(0);
        private final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        private final List<Exception> exceptions = new CopyOnWriteArrayList<>();

        public void recordSuccessfulRequest(long responseTime) {
            successfulRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            updateResponseTimeStats(responseTime);
        }

        public void recordFailedRequest(long responseTime, Exception exception) {
            failedRequests.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            updateResponseTimeStats(responseTime);
            exceptions.add(exception);
        }

        private void updateResponseTimeStats(long responseTime) {
            maxResponseTime.updateAndGet(current -> Math.max(current, responseTime));
            minResponseTime.updateAndGet(current -> Math.min(current, responseTime));
        }

        public void incrementBusinessServiceSuccess() { businessServiceSuccess.incrementAndGet(); }
        public void incrementBusinessServiceFailure() { businessServiceFailure.incrementAndGet(); }
        public void incrementStorageServiceSuccess() { storageServiceSuccess.incrementAndGet(); }
        public void incrementStorageServiceFailure() { storageServiceFailure.incrementAndGet(); }

        public int getSuccessfulRequests() { return successfulRequests.get(); }
        public int getFailedRequests() { return failedRequests.get(); }
        public int getBusinessServiceSuccess() { return businessServiceSuccess.get(); }
        public int getBusinessServiceFailure() { return businessServiceFailure.get(); }
        public int getStorageServiceSuccess() { return storageServiceSuccess.get(); }
        public int getStorageServiceFailure() { return storageServiceFailure.get(); }
        
        public double getSuccessRate() {
            int total = successfulRequests.get() + failedRequests.get();
            return total > 0 ? (double) successfulRequests.get() / total * 100 : 0.0;
        }
        
        public double getAverageResponseTime() {
            int total = successfulRequests.get() + failedRequests.get();
            return total > 0 ? (double) totalResponseTime.get() / total : 0.0;
        }
        
        public long getMaxResponseTime() { 
            return maxResponseTime.get() == 0 ? 0 : maxResponseTime.get(); 
        }
        
        public long getMinResponseTime() { 
            return minResponseTime.get() == Long.MAX_VALUE ? 0 : minResponseTime.get(); 
        }
    }

    /**
     * 性能測試專用Redis配置
     */
    @org.springframework.boot.test.context.TestConfiguration
    public static class PerformanceTestRedisConfiguration {

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public RedissonClient performanceTestRedissonClient() {
            org.redisson.config.Config config = new org.redisson.config.Config();
            config.useSingleServer()
                  .setAddress("redis://localhost:" + REDIS_PORT)
                  .setConnectionMinimumIdleSize(1)
                  .setConnectionPoolSize(10)
                  .setTimeout(3000)
                  .setRetryAttempts(3)
                  .setRetryInterval(1500);
            
            return org.redisson.Redisson.create(config);
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public DistributedLockProperties performanceTestDistributedLockProperties() {
            DistributedLockProperties properties = new DistributedLockProperties();
            
            // 設置Redis配置
            DistributedLockProperties.Redis redis = new DistributedLockProperties.Redis();
            redis.setHost("localhost");
            redis.setPort(REDIS_PORT);
            redis.setTimeout(3000);
            properties.setRedis(redis);
            
            // 設置鎖配置
            DistributedLockProperties.Lock lock = new DistributedLockProperties.Lock();
            lock.setDefaultWaitTime(5L);
            lock.setDefaultLeaseTime(30L);
            lock.setKeyPrefix("perf:test:distributed:lock:");
            lock.setEnableMonitoring(true);
            lock.setCrossServiceLock(true);
            properties.setLock(lock);
            
            return properties;
        }
    }
}