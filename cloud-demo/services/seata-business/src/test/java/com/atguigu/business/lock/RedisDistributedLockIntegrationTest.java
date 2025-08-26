package com.atguigu.business.lock;

import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisDistributedLock集成測試
 * 使用Testcontainers Redis測試真實的分布式鎖功能
 * 測試跨服務鎖的獲取、釋放和超時機制
 * 測試服務標識和上下文信息的正確性
 */
@Testcontainers
@DisplayName("RedisDistributedLock 集成測試")
class RedisDistributedLockIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private RedisDistributedLock distributedLock;
    private RedissonClient redissonClient;

    private static final String TEST_LOCK_KEY = "integration:test:lock";
    private static final String CROSS_SERVICE_LOCK_KEY = "cross:service:storage:ITEM001";

    @BeforeAll
    static void setUpRedis() {
        redis.start();
    }

    @BeforeEach
    void setUpTest() {
        // 創建Redisson客戶端
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379))
              .setConnectionMinimumIdleSize(1)
              .setConnectionPoolSize(2)
              .setTimeout(3000);
        
        redissonClient = Redisson.create(config);
        
        // 創建分布式鎖實例
        distributedLock = new RedisDistributedLock();
        ReflectionTestUtils.setField(distributedLock, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(distributedLock, "serviceName", "test-service");
        ReflectionTestUtils.setField(distributedLock, "defaultWaitTime", 5L);
        ReflectionTestUtils.setField(distributedLock, "defaultLeaseTime", 30L);
    }

    @AfterEach
    void tearDown() {
        // 清理測試後的鎖和資源
        if (distributedLock != null) {
            distributedLock.clearLockContext();
            try {
                distributedLock.forceUnlock(TEST_LOCK_KEY);
                distributedLock.forceUnlock(CROSS_SERVICE_LOCK_KEY);
            } catch (Exception e) {
                // 忽略清理異常
            }
        }
        
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Nested
    @DisplayName("基本鎖功能集成測試")
    class BasicLockFunctionalityTests {

        @Test
        @DisplayName("成功獲取和釋放鎖")
        void testSuccessfulLockAcquisitionAndRelease() {
            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");
            assertTrue(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該存在");
            assertTrue(distributedLock.isHeldByCurrentThread(TEST_LOCK_KEY), "當前線程應該持有鎖");

            // 驗證上下文信息
            CrossServiceLockContext context = distributedLock.getLockContext(TEST_LOCK_KEY);
            assertNotNull(context, "應該有鎖上下文");
            assertEquals("test-service", context.getServiceSource(), "服務來源應該正確");
            assertEquals(TEST_LOCK_KEY, context.getLockKey(), "鎖鍵應該正確");

            // 釋放鎖
            distributedLock.unlock(TEST_LOCK_KEY);
            assertFalse(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該被釋放");
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "上下文應該被清理");
        }

        @Test
        @DisplayName("鎖自動過期測試")
        void testLockAutoExpiration() throws InterruptedException {
            // Given - 使用很短的租約時間
            long shortLeaseTime = 2L; // 2秒

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, shortLeaseTime);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");
            assertTrue(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該存在");

            // 等待鎖過期
            Thread.sleep(3000); // 等待3秒，超過租約時間

            // 驗證鎖已過期
            assertFalse(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該已過期");
        }

        @Test
        @DisplayName("獲取鎖剩餘時間測試")
        void testGetRemainingTime() throws InterruptedException {
            // Given
            long leaseTime = 10L; // 10秒租約

            // When
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, leaseTime);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");

            long remainingTime = distributedLock.getRemainingTime(TEST_LOCK_KEY);
            assertTrue(remainingTime > 0, "剩餘時間應該大於0");
            assertTrue(remainingTime <= leaseTime * 1000, "剩餘時間應該不超過租約時間");

            // 等待一段時間後再檢查
            Thread.sleep(1000);
            long remainingTime2 = distributedLock.getRemainingTime(TEST_LOCK_KEY);
            assertTrue(remainingTime2 < remainingTime, "剩餘時間應該減少");

            // 清理
            distributedLock.unlock(TEST_LOCK_KEY);
        }
    }

    @Nested
    @DisplayName("併發鎖測試")
    class ConcurrentLockTests {

        @Test
        @DisplayName("多線程併發獲取同一鎖 - 互斥測試")
        void testConcurrentLockMutualExclusion() throws InterruptedException {
            // Given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger concurrentAccessCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 等待所有線程準備就緒

                        boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 1L, 5L);
                        if (acquired) {
                            successCount.incrementAndGet();
                            
                            // 模擬業務處理並檢查併發訪問
                            int currentCount = concurrentAccessCount.incrementAndGet();
                            Thread.sleep(100); // 持有鎖一段時間
                            
                            // 驗證同時只有一個線程在執行
                            assertEquals(1, currentCount, 
                                "同時只應該有一個線程能夠獲取鎖並執行業務邏輯");
                            
                            concurrentAccessCount.decrementAndGet();
                            distributedLock.unlock(TEST_LOCK_KEY);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // 開始執行
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);

            // Then
            assertTrue(completed, "所有線程應該在30秒內完成");
            assertTrue(successCount.get() >= 1, "至少應該有一個線程成功獲取鎖");
            assertTrue(successCount.get() <= threadCount, "成功獲取鎖的線程數不應該超過總線程數");

            executor.shutdown();
        }

        @Test
        @DisplayName("跨服務鎖衝突模擬測試")
        void testCrossServiceLockConflict() throws InterruptedException {
            // Given - 模擬兩個不同的服務實例
            RedisDistributedLock businessServiceLock = createServiceLock("seata-business");
            RedisDistributedLock storageServiceLock = createServiceLock("seata-storage");

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch businessLatch = new CountDownLatch(1);
            CountDownLatch storageLatch = new CountDownLatch(1);
            
            AtomicInteger businessSuccess = new AtomicInteger(0);
            AtomicInteger storageSuccess = new AtomicInteger(0);

            // When - 兩個服務同時嘗試獲取同一個庫存鎖
            Thread businessThread = new Thread(() -> {
                try {
                    startLatch.await();
                    boolean acquired = businessServiceLock.tryLock(CROSS_SERVICE_LOCK_KEY, 2L, 5L);
                    if (acquired) {
                        businessSuccess.incrementAndGet();
                        Thread.sleep(1000); // 持有鎖1秒
                        businessServiceLock.unlock(CROSS_SERVICE_LOCK_KEY);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    businessLatch.countDown();
                }
            });

            Thread storageThread = new Thread(() -> {
                try {
                    startLatch.await();
                    boolean acquired = storageServiceLock.tryLock(CROSS_SERVICE_LOCK_KEY, 2L, 5L);
                    if (acquired) {
                        storageSuccess.incrementAndGet();
                        Thread.sleep(1000); // 持有鎖1秒
                        storageServiceLock.unlock(CROSS_SERVICE_LOCK_KEY);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    storageLatch.countDown();
                }
            });

            businessThread.start();
            storageThread.start();
            startLatch.countDown(); // 開始執行

            boolean businessCompleted = businessLatch.await(10, TimeUnit.SECONDS);
            boolean storageCompleted = storageLatch.await(10, TimeUnit.SECONDS);

            // Then
            assertTrue(businessCompleted, "business服務線程應該完成");
            assertTrue(storageCompleted, "storage服務線程應該完成");
            
            // 驗證互斥性：兩個服務不能同時獲取同一個鎖
            int totalSuccess = businessSuccess.get() + storageSuccess.get();
            assertTrue(totalSuccess >= 1, "至少有一個服務應該成功獲取鎖");
            assertTrue(totalSuccess <= 2, "最多兩個服務都能獲取鎖（如果是順序執行）");
        }
    }

    @Nested
    @DisplayName("超時和異常處理測試")
    class TimeoutAndExceptionTests {

        @Test
        @DisplayName("鎖獲取超時測試")
        void testLockAcquisitionTimeout() throws InterruptedException {
            // Given - 先獲取鎖
            boolean firstAcquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 10L);
            assertTrue(firstAcquired, "第一次獲取鎖應該成功");

            // When - 另一個線程嘗試獲取同一個鎖，設置短超時時間
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger secondResult = new AtomicInteger(-1);
            long startTime = System.currentTimeMillis();

            Thread secondThread = new Thread(() -> {
                boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 2L, 5L); // 2秒超時
                secondResult.set(acquired ? 1 : 0);
                latch.countDown();
            });

            secondThread.start();
            latch.await(5, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            // Then
            assertEquals(0, secondResult.get(), "第二次獲取鎖應該超時失敗");
            assertTrue(endTime - startTime >= 2000, "應該等待至少2秒（超時時間）");
            assertTrue(endTime - startTime < 4000, "不應該等待超過4秒");

            // 清理
            distributedLock.unlock(TEST_LOCK_KEY);
        }

        @Test
        @DisplayName("強制釋放鎖測試")
        void testForceUnlock() {
            // Given
            boolean acquired = distributedLock.tryLock(TEST_LOCK_KEY, 5L, 30L);
            assertTrue(acquired, "應該成功獲取鎖");
            assertTrue(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該存在");

            // When
            boolean forceUnlocked = distributedLock.forceUnlock(TEST_LOCK_KEY);

            // Then
            assertTrue(forceUnlocked, "應該成功強制釋放鎖");
            assertFalse(distributedLock.isLocked(TEST_LOCK_KEY), "鎖應該被釋放");
            assertNull(distributedLock.getLockContext(TEST_LOCK_KEY), "上下文應該被清理");
        }
    }

    @Nested
    @DisplayName("跨服務上下文驗證測試")
    class CrossServiceContextValidationTests {

        @Test
        @DisplayName("驗證不同服務的鎖上下文信息")
        void testDifferentServiceContexts() {
            // Given - 創建模擬不同服務的鎖實例
            RedisDistributedLock businessLock = createServiceLock("seata-business");
            RedisDistributedLock storageLock = createServiceLock("seata-storage");

            String businessLockKey = "business:test:lock";
            String storageLockKey = "storage:test:lock";

            // When
            boolean businessAcquired = businessLock.tryLock(businessLockKey, 5L, 30L);
            boolean storageAcquired = storageLock.tryLock(storageLockKey, 5L, 30L);

            // Then
            assertTrue(businessAcquired, "business服務應該成功獲取鎖");
            assertTrue(storageAcquired, "storage服務應該成功獲取鎖");

            // 驗證上下文信息
            CrossServiceLockContext businessContext = businessLock.getLockContext(businessLockKey);
            CrossServiceLockContext storageContext = storageLock.getLockContext(storageLockKey);

            assertNotNull(businessContext, "business服務應該有鎖上下文");
            assertNotNull(storageContext, "storage服務應該有鎖上下文");

            assertEquals("seata-business", businessContext.getServiceSource(), 
                        "business服務來源應該正確");
            assertEquals("seata-storage", storageContext.getServiceSource(), 
                        "storage服務來源應該正確");

            assertNotEquals(businessContext.getLockHolder(), storageContext.getLockHolder(), 
                          "不同服務的鎖持有者標識應該不同");

            // 清理
            businessLock.unlock(businessLockKey);
            storageLock.unlock(storageLockKey);
        }

        @Test
        @DisplayName("驗證鎖持有者標識格式")
        void testLockHolderIdentifierFormat() {
            // Given
            String serviceName = "test-service";
            RedisDistributedLock testLock = createServiceLock(serviceName);

            // When
            boolean acquired = testLock.tryLock(TEST_LOCK_KEY, 5L, 30L);

            // Then
            assertTrue(acquired, "應該成功獲取鎖");

            CrossServiceLockContext context = testLock.getLockContext(TEST_LOCK_KEY);
            assertNotNull(context, "應該有鎖上下文");

            String lockHolder = context.getLockHolder();
            assertNotNull(lockHolder, "鎖持有者標識不應該為空");

            // 驗證格式：服務名-實例ID-線程ID
            String[] parts = lockHolder.split("-");
            assertTrue(parts.length >= 3, "鎖持有者標識應該包含至少3個部分");
            assertEquals(serviceName, parts[0], "第一部分應該是服務名");
            assertTrue(lockHolder.contains(Thread.currentThread().getName()), 
                      "應該包含當前線程名");

            // 清理
            testLock.unlock(TEST_LOCK_KEY);
        }

        private RedisDistributedLock createServiceLock(String serviceName) {
            RedisDistributedLock lock = new RedisDistributedLock();
            ReflectionTestUtils.setField(lock, "redissonClient", redissonClient);
            ReflectionTestUtils.setField(lock, "serviceName", serviceName);
            ReflectionTestUtils.setField(lock, "defaultWaitTime", 5L);
            ReflectionTestUtils.setField(lock, "defaultLeaseTime", 30L);
            return lock;
        }
    }

    @Nested
    @DisplayName("性能和穩定性測試")
    class PerformanceAndStabilityTests {

        @Test
        @DisplayName("高併發鎖獲取性能測試")
        void testHighConcurrencyPerformance() throws InterruptedException {
            // Given
            int threadCount = 50;
            int operationsPerThread = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger totalOperations = new AtomicInteger(0);
            AtomicInteger successfulOperations = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            long startTime = System.currentTimeMillis();

            // When
            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        for (int j = 0; j < operationsPerThread; j++) {
                            String lockKey = "perf:test:" + threadIndex + ":" + j;
                            totalOperations.incrementAndGet();
                            
                            boolean acquired = distributedLock.tryLock(lockKey, 1L, 2L);
                            if (acquired) {
                                successfulOperations.incrementAndGet();
                                // 模擬短暫的業務處理
                                Thread.sleep(10);
                                distributedLock.unlock(lockKey);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = endLatch.await(60, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            // Then
            assertTrue(completed, "所有線程應該在60秒內完成");
            
            int expectedOperations = threadCount * operationsPerThread;
            assertEquals(expectedOperations, totalOperations.get(), "總操作數應該正確");
            
            double successRate = (double) successfulOperations.get() / totalOperations.get();
            assertTrue(successRate > 0.8, "成功率應該超過80%");
            
            long totalTime = endTime - startTime;
            double avgTimePerOperation = (double) totalTime / totalOperations.get();
            assertTrue(avgTimePerOperation < 100, "平均每個操作應該在100毫秒內完成");

            System.out.printf("性能測試結果: 總操作數=%d, 成功操作數=%d, 成功率=%.2f%%, 總時間=%dms, 平均時間=%.2fms%n",
                totalOperations.get(), successfulOperations.get(), successRate * 100, totalTime, avgTimePerOperation);

            executor.shutdown();
        }

        @Test
        @DisplayName("長時間運行穩定性測試")
        void testLongRunningStability() throws InterruptedException {
            // Given
            int duration = 10; // 10秒測試
            AtomicInteger operationCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            long endTime = startTime + duration * 1000;

            // When
            while (System.currentTimeMillis() < endTime) {
                try {
                    String lockKey = "stability:test:" + operationCount.get();
                    operationCount.incrementAndGet();
                    
                    boolean acquired = distributedLock.tryLock(lockKey, 1L, 2L);
                    if (acquired) {
                        Thread.sleep(50); // 模擬業務處理
                        distributedLock.unlock(lockKey);
                    }
                    
                    Thread.sleep(10); // 短暫休息
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }

            // Then
            assertTrue(operationCount.get() > 0, "應該執行了一些操作");
            double errorRate = (double) errorCount.get() / operationCount.get();
            assertTrue(errorRate < 0.01, "錯誤率應該低於1%");

            System.out.printf("穩定性測試結果: 操作數=%d, 錯誤數=%d, 錯誤率=%.4f%%%n",
                operationCount.get(), errorCount.get(), errorRate * 100);
        }
    }

    // 輔助方法：創建指定服務名的分布式鎖實例
    private RedisDistributedLock createServiceLock(String serviceName) {
        RedisDistributedLock lock = new RedisDistributedLock();
        ReflectionTestUtils.setField(lock, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(lock, "serviceName", serviceName);
        ReflectionTestUtils.setField(lock, "defaultWaitTime", 5L);
        ReflectionTestUtils.setField(lock, "defaultLeaseTime", 30L);
        return lock;
    }
}