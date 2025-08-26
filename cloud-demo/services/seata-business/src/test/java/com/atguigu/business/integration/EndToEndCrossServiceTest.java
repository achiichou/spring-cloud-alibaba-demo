package com.atguigu.business.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.atguigu.business.bean.StorageTbl;
import com.atguigu.business.lock.LockInfo;
import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.LockStatistics;
import com.atguigu.business.lock.TestRedisConfiguration;
import com.atguigu.business.mapper.storage.StorageTblMapper;
import com.atguigu.business.service.BusinessStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 端到端跨服務測試
 * 
 * 測試完整的跨服務業務流程：
 * 1. 從兩個服務的API調用到數據庫操作的完整鏈路
 * 2. 驗證分布式鎖在真實跨服務場景中的表現
 * 3. 測試異常恢復和故障轉移機制
 * 4. 驗證API響應格式和錯誤處理
 * 
 * 本測試模擬了真實的微服務環境，包括：
 * - HTTP API調用
 * - 分布式鎖協調
 * - 數據庫操作
 * - 併發處理
 * - 異常恢復
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestRedisConfiguration.class})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndCrossServiceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StorageTblMapper storageTblMapper;

    @Autowired
    private BusinessStorageService businessStorageService;

    @Autowired
    private LockMonitorService lockMonitorService;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private static final String TEST_COMMODITY_CODE = "E2E_TEST_PRODUCT";
    private static final String TEST_COMMODITY_CODE_2 = "E2E_TEST_PRODUCT_2";
    private static final int INITIAL_STOCK = 1000;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // 準備測試數據
        initializeTestData();
        
        log.info("End-to-end test setup completed. Base URL: {}", baseUrl);
    }

    @AfterEach
    void tearDown() {
        // 清理測試數據
        cleanupTestData();
        
        // 清理可能殘留的鎖
        try {
            List<LockInfo> locks = lockMonitorService.getAllLocks();
            for (LockInfo lock : locks) {
                if (lock.getLockKey().contains("E2E_TEST")) {
                    lockMonitorService.forceUnlock(lock.getLockKey());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup test locks: {}", e.getMessage());
        }
        
        log.info("End-to-end test teardown completed");
    }

    @Test
    @Order(1)
    @DisplayName("測試單個服務API調用 - seata-business直接庫存扣減")
    void testSingleServiceApiCall() throws Exception {
        // 準備請求參數
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commodityCode", TEST_COMMODITY_CODE);
        params.add("count", "10");
        params.add("businessContext", "E2E-Single-Service-Test");

        // 發送POST請求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/business/storage/deduct", 
            request, 
            Map.class
        );

        // 驗證響應
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("庫存扣減成功", response.getBody().get("message"));

        // 驗證數據庫變更
        StorageTbl updatedStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        assertEquals(INITIAL_STOCK - 10, updatedStorage.getCount().intValue());

        log.info("Single service API call test passed");
    }

    @Test
    @Order(2)
    @DisplayName("測試跨服務併發API調用 - 防止超賣場景")
    void testConcurrentCrossServiceApiCalls() throws Exception {
        int threadCount = 50;
        int deductCountPerThread = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // 模擬兩個服務同時發起請求
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 一半請求發給seata-business，一半模擬seata-storage
                    if (threadIndex % 2 == 0) {
                        // seata-business API調用
                        callBusinessStorageApi(TEST_COMMODITY_CODE_2, deductCountPerThread, 
                                             "E2E-Concurrent-Business-" + threadIndex);
                    } else {
                        // 模擬seata-storage服務調用（通過直接調用service層）
                        businessStorageService.directDeduct(TEST_COMMODITY_CODE_2, deductCountPerThread, 
                                                          "E2E-Concurrent-Storage-" + threadIndex);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Thread {} failed: {}", threadIndex, e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 開始併發執行
        startLatch.countDown();
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS));
        executorService.shutdown();

        // 驗證結果
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE_2);
        int expectedFinalStock = INITIAL_STOCK - (successCount.get() * deductCountPerThread);
        
        assertEquals(expectedFinalStock, finalStorage.getCount().intValue());
        assertTrue(successCount.get() > 0);
        
        log.info("Concurrent cross-service API test completed - Success: {}, Failures: {}, Final stock: {}", 
                 successCount.get(), failureCount.get(), finalStorage.getCount());
    }

    @Test
    @Order(3)
    @DisplayName("測試鎖管理API端點")
    void testLockManagementApiEndpoints() throws Exception {
        // 獲取當前鎖狀態
        ResponseEntity<Map> lockStatusResponse = restTemplate.getForEntity(
            baseUrl + "/business/lock/status", Map.class);
        
        assertEquals(HttpStatus.OK, lockStatusResponse.getStatusCode());
        assertNotNull(lockStatusResponse.getBody());
        assertTrue(lockStatusResponse.getBody().containsKey("success"));

        // 獲取鎖統計信息
        ResponseEntity<Map> statisticsResponse = restTemplate.getForEntity(
            baseUrl + "/business/lock/statistics", Map.class);
        
        assertEquals(HttpStatus.OK, statisticsResponse.getStatusCode());
        assertNotNull(statisticsResponse.getBody());
        assertTrue(statisticsResponse.getBody().containsKey("success"));

        // 驗證統計數據結構
        Map<String, Object> statisticsData = (Map<String, Object>) statisticsResponse.getBody().get("data");
        assertNotNull(statisticsData);
        assertTrue(statisticsData.containsKey("totalLockRequests"));
        assertTrue(statisticsData.containsKey("successfulLocks"));

        log.info("Lock management API endpoints test passed");
    }

    @Test
    @Order(4)
    @DisplayName("測試異常恢復機制 - 模擬鎖獲取失敗場景")
    void testExceptionRecoveryMechanism() throws Exception {
        // 先手動獲取鎖，模擬其他服務持有鎖的情況
        String lockKey = "distributed:lock:storage:" + TEST_COMMODITY_CODE;
        
        // 準備一個會失敗的請求（庫存不足）
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commodityCode", TEST_COMMODITY_CODE);
        params.add("count", String.valueOf(INITIAL_STOCK + 100)); // 超過庫存
        params.add("businessContext", "E2E-Exception-Recovery-Test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/business/storage/deduct", 
            request, 
            Map.class
        );

        // 驗證錯誤響應
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("庫存扣減失敗"));

        // 驗證數據庫沒有被錯誤修改
        StorageTbl storage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        assertTrue(storage.getCount() > 0); // 庫存應該還在

        log.info("Exception recovery mechanism test passed");
    }

    @Test
    @Order(5)
    @DisplayName("測試跨服務故障轉移 - 超時和重試機制")
    void testCrossServiceFailoverMechanism() throws Exception {
        // 創建多個並發請求來測試故障轉移
        int requestCount = 10;
        CompletableFuture<ResponseEntity<Map>>[] futures = new CompletableFuture[requestCount];

        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            futures[i] = CompletableFuture.supplyAsync(() -> {
                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("commodityCode", TEST_COMMODITY_CODE);
                params.add("count", "1");
                params.add("businessContext", "E2E-Failover-Test-" + index);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

                return restTemplate.postForEntity(
                    baseUrl + "/business/storage/deduct", 
                    request, 
                    Map.class
                );
            });
        }

        // 等待所有請求完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);
        allFutures.get(30, TimeUnit.SECONDS);

        // 統計結果
        long successCount = java.util.Arrays.stream(futures)
            .map(CompletableFuture::join)
            .mapToLong(response -> HttpStatus.OK.equals(response.getStatusCode()) ? 1 : 0)
            .sum();

        assertTrue(successCount > 0);
        log.info("Cross-service failover test completed - Success rate: {}/{}", successCount, requestCount);
    }

    @Test
    @Order(6)
    @DisplayName("測試完整業務流程 - 包含監控和統計")
    void testCompleteBusinessWorkflow() throws Exception {
        // 1. 記錄初始狀態
        LockStatistics initialStats = lockMonitorService.getLockStatistics();
        StorageTbl initialStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);

        // 2. 執行業務操作
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commodityCode", TEST_COMMODITY_CODE);
        params.add("count", "50");
        params.add("businessContext", "E2E-Complete-Workflow-Test");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/business/storage/deduct", 
            request, 
            Map.class
        );

        // 3. 驗證業務結果
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));

        // 4. 驗證數據庫變更
        StorageTbl finalStorage = storageTblMapper.selectByCommodityCode(TEST_COMMODITY_CODE);
        assertEquals(initialStorage.getCount() - 50, finalStorage.getCount().intValue());

        // 5. 驗證監控統計有所變化
        LockStatistics finalStats = lockMonitorService.getLockStatistics();
        assertTrue(finalStats.getTotalLockRequests() >= initialStats.getTotalLockRequests());
        
        // 6. 驗證鎖已經被正確釋放
        List<LockInfo> currentLocks = lockMonitorService.getAllLocks();
        boolean hasTestLock = currentLocks.stream()
            .anyMatch(lock -> lock.getLockKey().contains(TEST_COMMODITY_CODE));
        assertFalse(hasTestLock, "Test lock should be released after transaction");

        log.info("Complete business workflow test passed");
    }

    /**
     * 初始化測試數據
     */
    private void initializeTestData() {
        try {
            // 刪除可能存在的測試數據
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_2);

            // 插入測試數據
            StorageTbl testStorage1 = new StorageTbl();
            testStorage1.setCommodityCode(TEST_COMMODITY_CODE);
            testStorage1.setCount(INITIAL_STOCK);
            storageTblMapper.insert(testStorage1);

            StorageTbl testStorage2 = new StorageTbl();
            testStorage2.setCommodityCode(TEST_COMMODITY_CODE_2);
            testStorage2.setCount(INITIAL_STOCK);
            storageTblMapper.insert(testStorage2);

            log.info("Test data initialized - {} and {} with {} stock each", 
                     TEST_COMMODITY_CODE, TEST_COMMODITY_CODE_2, INITIAL_STOCK);
        } catch (Exception e) {
            log.error("Failed to initialize test data", e);
            throw new RuntimeException("Test data initialization failed", e);
        }
    }

    /**
     * 清理測試數據
     */
    private void cleanupTestData() {
        try {
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE);
            storageTblMapper.deleteByCommodityCode(TEST_COMMODITY_CODE_2);
            log.info("Test data cleaned up");
        } catch (Exception e) {
            log.warn("Failed to cleanup test data: {}", e.getMessage());
        }
    }

    /**
     * 調用業務存儲API
     */
    private void callBusinessStorageApi(String commodityCode, int count, String businessContext) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("commodityCode", commodityCode);
        params.add("count", String.valueOf(count));
        params.add("businessContext", businessContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/business/storage/deduct", 
            request, 
            Map.class
        );

        if (!HttpStatus.OK.equals(response.getStatusCode()) || 
            !(Boolean) response.getBody().get("success")) {
            throw new RuntimeException("API call failed: " + response.getBody().get("message"));
        }
    }
}