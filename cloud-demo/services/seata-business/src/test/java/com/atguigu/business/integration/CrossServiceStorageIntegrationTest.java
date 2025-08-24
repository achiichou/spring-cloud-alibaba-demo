package com.atguigu.business.integration;

import com.atguigu.business.bean.StorageOperation;
import com.atguigu.business.bean.StorageTbl;
import com.atguigu.business.config.DistributedLockProperties;
import com.atguigu.business.lock.DistributedLock;
import com.atguigu.business.lock.DistributedLockException;
import com.atguigu.business.lock.LockErrorCode;
import com.atguigu.business.lock.TestRedisConfiguration;
import com.atguigu.business.config.TestMyBatisConfiguration;
import com.atguigu.business.mapper.storage.StorageTblMapper;
import com.atguigu.business.service.BusinessStorageService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 跨服務業務邏輯集成測試
 * 測試seata-business和seata-storage同時操作庫存的場景
 * 驗證分布式鎖防止跨服務數據衝突的效果
 */
@Slf4j
@SpringBootTest(
    classes = com.atguigu.business.SeataBusinessMainApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataCoreAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration,com.alibaba.cloud.seata.SeataAutoConfiguration"
    }
)
@Import({TestRedisConfiguration.class, TestMyBatisConfiguration.class})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CrossServiceStorageIntegrationTest {

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

    private static final String TEST_COMMODITY_CODE = "CROSS_SERVICE_TEST_001";
    private static final String TEST_COMMODITY_CODE_2 = "CROSS_SERVICE_TEST_002";
    private static final String TEST_COMMODITY_CODE_3 = "CROSS_SERVICE_TEST_003";
    private static final int INITIAL_STOCK = 100;

    @BeforeEach
    void setUp() {
        // 確保測試數據庫和表結構存在
        ensureTestDatabaseExists();
        
        // 清理測試數據
        cleanupTestData();
        
        // 初始化測試庫存數據
        initializeTestData();
        
        // 清理Redis中的鎖
        cleanupRedisLocks();
    }

    @AfterEach
    void tearDown() {
        // 清理測試數據
        cleanupTestData();
        
        // 清理Redis中的鎖
        cleanupRedisLocks();
    }

    /**
     * 測試跨服務併發庫存扣減場景
     * 驗證分布式鎖防止數據衝突的效果
     */
    @Test
    @Order(1)
    @DisplayName("測試跨服務併發庫存扣減 - 分布式鎖保護")
    void testCrossServiceConcurrentDeduction() throws InterruptedException {
        log.info("開始測試跨服務併發庫存扣減");

        // 準備併發測試
        int threadCount = 10;
        int deductionPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // 模擬business服務和storage服務同時操作
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    if (threadIndex % 2 == 0) {
                        // 模擬business服務直接扣減
                        businessStorageService.directDeduct(
                            TEST_COMMODITY_CODE, 
                            deductionPerThread, 
                            "business-thread-" + threadIndex
                        );
                        log.info("Business服務扣減成功 - 線程: {}", threadIndex);
                    } else {
                        // 模擬storage服務扣減（通過直接調用mapper模擬）
                        simulateStorageServiceDeduction(TEST_COMMODITY_CODE, deductionPerThread, threadIndex);
                        log.info("Storage服務扣減成功 - 線程: {}", threadIndex);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("扣減失敗 - 線程: {}, 錯誤: {}", threadIndex, e.getMessage());
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有線程完成
        assertTrue(latch.await(30, TimeUnit.SECONDS), "測試超時");
        executor.shutdown();

        // 驗證結果
        log.info("併發測試完成 - 成功: {}, 失敗: {}", successCount.get(), failureCount.get());
        
        // 檢查最終庫存
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        assertNotNull(finalStorage, "庫存記錄不應為null");
        
        int expectedFinalStock = INITIAL_STOCK - (successCount.get() * deductionPerThread);
        assertEquals(expectedFinalStock, finalStorage.getCount(), 
            "最終庫存應該等於初始庫存減去成功扣減的總數量");
        
        // 驗證沒有超賣
        assertTrue(finalStorage.getCount() >= 0, "庫存不應該為負數");
        
        log.info("跨服務併發庫存扣減測試完成 - 最終庫存: {}", finalStorage.getCount());
    }

    /**
     * 測試跨服務事務與鎖的協同工作
     * 驗證事務回滾時鎖的正確釋放
     */
    @Test
    @Order(2)
    @DisplayName("測試跨服務事務與鎖的協同工作")
    void testCrossServiceTransactionLockCoordination() throws InterruptedException {
        log.info("開始測試跨服務事務與鎖的協同工作");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> businessException = new AtomicReference<>();
        AtomicReference<Exception> storageException = new AtomicReference<>();

        // 線程1：business服務操作（會因為業務邏輯異常回滾）
        executor.submit(() -> {
            try {
                // 嘗試扣減大量庫存，觸發業務異常
                businessStorageService.directDeduct(
                    TEST_COMMODITY_CODE_2, 
                    15, // 大於10會觸發異常
                    "business-transaction-test"
                );
            } catch (Exception e) {
                businessException.set(e);
                log.info("Business服務異常（預期）: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 稍微延遲，確保第一個線程先獲取鎖
        Thread.sleep(100);

        // 線程2：storage服務操作（應該能在第一個事務回滾後成功）
        executor.submit(() -> {
            try {
                // 等待一段時間，讓第一個事務有時間回滾
                Thread.sleep(1000);
                simulateStorageServiceDeduction(TEST_COMMODITY_CODE_2, 5, 999);
                log.info("Storage服務操作成功");
            } catch (Exception e) {
                storageException.set(e);
                log.error("Storage服務操作失敗: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 等待所有線程完成
        assertTrue(latch.await(15, TimeUnit.SECONDS), "測試超時");
        executor.shutdown();

        // 驗證結果
        assertNotNull(businessException.get(), "Business服務應該拋出異常");
        assertNull(storageException.get(), "Storage服務不應該拋出異常");

        // 檢查最終庫存（應該只被storage服務扣減了5）
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE_2);
        assertEquals(INITIAL_STOCK - 5, finalStorage.getCount(), 
            "最終庫存應該只被storage服務扣減");

        log.info("跨服務事務與鎖協同工作測試完成");
    }

    /**
     * 測試跨服務批量操作的鎖保護
     */
    @Test
    @Order(3)
    @DisplayName("測試跨服務批量操作的鎖保護")
    void testCrossServiceBatchOperationLockProtection() throws InterruptedException {
        log.info("開始測試跨服務批量操作的鎖保護");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // 準備批量操作
        List<StorageOperation> batchOperations = Arrays.asList(
            createStorageOperation(TEST_COMMODITY_CODE, StorageOperation.OperationType.DEDUCT, 10),
            createStorageOperation(TEST_COMMODITY_CODE_2, StorageOperation.OperationType.DEDUCT, 15),
            createStorageOperation(TEST_COMMODITY_CODE_3, StorageOperation.OperationType.DEDUCT, 20)
        );

        // 線程1：business服務批量操作
        executor.submit(() -> {
            try {
                businessStorageService.batchStorageOperation(batchOperations);
                successCount.incrementAndGet();
                log.info("Business服務批量操作成功");
            } catch (Exception e) {
                exceptions.add(e);
                log.warn("Business服務批量操作失敗: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // 線程2和3：模擬storage服務的單個操作
        for (int i = 0; i < 2; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    Thread.sleep(500); // 稍微延遲
                    simulateStorageServiceDeduction(TEST_COMMODITY_CODE, 5, threadIndex);
                    successCount.incrementAndGet();
                    log.info("Storage服務單個操作成功 - 線程: {}", threadIndex);
                } catch (Exception e) {
                    exceptions.add(e);
                    log.warn("Storage服務單個操作失敗 - 線程: {}, 錯誤: {}", threadIndex, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有線程完成
        assertTrue(latch.await(20, TimeUnit.SECONDS), "測試超時");
        executor.shutdown();

        // 驗證結果
        log.info("批量操作測試完成 - 成功操作數: {}, 異常數: {}", successCount.get(), exceptions.size());
        
        // 檢查庫存變化
        StorageTbl storage1 = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        StorageTbl storage2 = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE_2);
        StorageTbl storage3 = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE_3);

        assertNotNull(storage1);
        assertNotNull(storage2);
        assertNotNull(storage3);

        log.info("最終庫存 - 商品1: {}, 商品2: {}, 商品3: {}", 
            storage1.getCount(), storage2.getCount(), storage3.getCount());

        // 驗證庫存一致性（具體數值取決於哪些操作成功）
        assertTrue(storage1.getCount() >= 0, "庫存1不應為負");
        assertTrue(storage2.getCount() >= 0, "庫存2不應為負");
        assertTrue(storage3.getCount() >= 0, "庫存3不應為負");
    }

    /**
     * 測試跨服務鎖衝突檢測和處理
     */
    @Test
    @Order(4)
    @DisplayName("測試跨服務鎖衝突檢測和處理")
    void testCrossServiceLockConflictDetection() throws InterruptedException {
        log.info("開始測試跨服務鎖衝突檢測和處理");

        String lockKey = lockProperties.getLock().getKeyPrefix() + TEST_COMMODITY_CODE_3;
        
        // 手動獲取鎖模擬storage服務持有鎖
        assertTrue(distributedLock.tryLock(lockKey, 1, 10), "應該能夠獲取鎖");
        
        try {
            // 嘗試通過business服務操作（應該失敗）
            assertThrows(DistributedLockException.class, () -> {
                businessStorageService.directDeduct(TEST_COMMODITY_CODE_3, 5, "conflict-test");
            }, "應該拋出分布式鎖異常");
            
            log.info("跨服務鎖衝突檢測成功");
            
        } finally {
            // 釋放鎖
            distributedLock.unlock(lockKey);
        }

        // 驗證鎖釋放後可以正常操作
        assertDoesNotThrow(() -> {
            businessStorageService.directDeduct(TEST_COMMODITY_CODE_3, 5, "after-unlock-test");
        }, "鎖釋放後應該能正常操作");

        log.info("跨服務鎖衝突檢測和處理測試完成");
    }

    /**
     * 測試跨服務高併發場景下的系統穩定性
     */
    @Test
    @Order(5)
    @DisplayName("測試跨服務高併發場景下的系統穩定性")
    void testCrossServiceHighConcurrencyStability() throws InterruptedException {
        log.info("開始測試跨服務高併發場景下的系統穩定性");

        int threadCount = 50;
        int operationsPerThread = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFailure = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            if (threadIndex % 3 == 0) {
                                // business服務操作
                                businessStorageService.directDeduct(
                                    TEST_COMMODITY_CODE, 1, 
                                    "high-concurrency-business-" + threadIndex + "-" + j
                                );
                            } else {
                                // 模擬storage服務操作
                                simulateStorageServiceDeduction(TEST_COMMODITY_CODE, 1, threadIndex * 100 + j);
                            }
                            totalSuccess.incrementAndGet();
                        } catch (Exception e) {
                            totalFailure.incrementAndGet();
                            // 在高併發測試中，部分失敗是正常的
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有線程完成
        assertTrue(latch.await(60, TimeUnit.SECONDS), "高併發測試超時");
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("高併發測試完成 - 耗時: {}ms, 成功: {}, 失敗: {}", 
            duration, totalSuccess.get(), totalFailure.get());

        // 驗證系統穩定性
        assertTrue(totalSuccess.get() > 0, "應該有成功的操作");
        assertTrue(duration < 30000, "總耗時應該在30秒內"); // 性能要求

        // 檢查最終數據一致性
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        assertNotNull(finalStorage);
        assertTrue(finalStorage.getCount() >= 0, "最終庫存不應為負");
        assertEquals(INITIAL_STOCK - totalSuccess.get(), finalStorage.getCount(), 
            "最終庫存應該等於初始庫存減去成功操作數");

        log.info("跨服務高併發穩定性測試完成 - 最終庫存: {}", finalStorage.getCount());
    }

    // ==================== 輔助方法 ====================

    /**
     * 確保測試數據庫存在
     */
    private void ensureTestDatabaseExists() {
        try {
            // 檢查表是否存在，如果不存在則創建
            StorageTbl testRecord = storageTblMapper.selectByCommodityCode("TEST_CHECK");
            log.debug("測試數據庫表已存在");
        } catch (Exception e) {
            log.info("測試數據庫表不存在或需要初始化，錯誤: {}", e.getMessage());
            // 這裡可以添加額外的數據庫初始化邏輯，但通常Spring會自動執行schema-test.sql
        }
    }

    /**
     * 模擬storage服務的庫存扣減操作
     */
    private void simulateStorageServiceDeduction(String commodityCode, int count, int threadIndex) {
        String lockKey = lockProperties.getLock().getKeyPrefix() + commodityCode;
        
        // 模擬storage服務的分布式鎖邏輯
        if (distributedLock.tryLock(lockKey, 5, 30)) {
            try {
                // 模擬storage服務的業務邏輯
                StorageTbl storage = storageTblMapper.selectByCommodityCode(commodityCode);
                if (storage == null) {
                    throw new RuntimeException("商品不存在: " + commodityCode);
                }
                if (storage.getCount() < count) {
                    throw new RuntimeException("庫存不足");
                }
                
                storageTblMapper.deduct(commodityCode, count);
                log.debug("Storage服務模擬扣減成功 - 商品: {}, 數量: {}, 線程: {}", 
                    commodityCode, count, threadIndex);
                
            } finally {
                distributedLock.unlock(lockKey);
            }
        } else {
            throw new DistributedLockException(LockErrorCode.LOCK_ACQUIRE_TIMEOUT, "獲取分布式鎖失敗");
        }
    }

    /**
     * 創建庫存操作對象
     */
    private StorageOperation createStorageOperation(String commodityCode, String operationType, int count) {
        StorageOperation operation = new StorageOperation();
        operation.setCommodityCode(commodityCode);
        operation.setOperationType(operationType);
        operation.setCount(count);
        operation.setServiceSource("business");
        operation.setBusinessContext("batch-test");
        return operation;
    }

    /**
     * 初始化測試數據
     */
    private void initializeTestData() {
        try {
            // 刪除可能存在的測試數據
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_2);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_3);
            
            // 插入測試數據
            StorageTbl storage1 = new StorageTbl();
            storage1.setCommodityCode(TEST_COMMODITY_CODE);
            storage1.setCount(INITIAL_STOCK);
            storageTblMapper.insert(storage1);

            StorageTbl storage2 = new StorageTbl();
            storage2.setCommodityCode(TEST_COMMODITY_CODE_2);
            storage2.setCount(INITIAL_STOCK);
            storageTblMapper.insert(storage2);

            StorageTbl storage3 = new StorageTbl();
            storage3.setCommodityCode(TEST_COMMODITY_CODE_3);
            storage3.setCount(INITIAL_STOCK);
            storageTblMapper.insert(storage3);
            
            log.info("測試數據初始化完成");
        } catch (Exception e) {
            log.warn("初始化測試數據時出現異常: {}", e.getMessage());
        }
    }

    /**
     * 清理測試數據
     */
    private void cleanupTestData() {
        try {
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_2);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_3);
            log.debug("測試數據清理完成");
        } catch (Exception e) {
            log.warn("清理測試數據時出現異常: {}", e.getMessage());
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
}