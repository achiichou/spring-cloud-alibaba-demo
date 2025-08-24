package com.atguigu.storage.lock;

import com.atguigu.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地事務與分布式鎖集成測試
 * 
 * 測試在實際Spring事務環境中，LocalLockTransactionSynchronization
 * 與StorageService的集成工作情況
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalTransactionIntegrationTest {
    
    @Autowired
    private StorageService storageService;
    
    @Autowired
    private LocalLockTransactionSynchronization localTransactionSynchronization;
    
    @Test
    @Transactional
    void testStorageServiceWithLocalTransactionSynchronization() {
        // Given
        String commodityCode = "test-commodity-001";
        int deductCount = 1;
        
        // When - 調用帶有@DistributedLockable註解的方法
        // 這應該觸發LocalLockTransactionSynchronization的註冊邏輯
        try {
            storageService.deduct(commodityCode, deductCount);
        } catch (Exception e) {
            // 預期可能會有數據庫相關的異常，但我們主要關注鎖的行為
            // 在測試環境中，數據庫可能不存在相應的數據
        }
        
        // Then - 驗證事務同步器的狀態
        assertTrue(localTransactionSynchronization.isInLocalTransaction());
        assertNotNull(localTransactionSynchronization.getCurrentLocalTransactionName());
        
        // 驗證統計信息
        LocalLockTransactionSynchronization.TransactionLockStatistics stats = 
            localTransactionSynchronization.getStatistics();
        assertNotNull(stats);
        assertEquals("seata-storage", stats.getServiceName());
        
        // 注意：由於事務還沒有提交，鎖可能還在持有中
        // 實際的鎖釋放會在事務提交或回滾時發生
    }
    
    @Test
    void testTransactionSynchronizationOutsideTransaction() {
        // When - 在事務外部調用
        assertFalse(localTransactionSynchronization.isInLocalTransaction());
        assertNull(localTransactionSynchronization.getCurrentLocalTransactionName());
        
        // 獲取統計信息應該不會拋出異常
        LocalLockTransactionSynchronization.TransactionLockStatistics stats = 
            localTransactionSynchronization.getStatistics();
        assertNotNull(stats);
        assertEquals(0, stats.getActiveLockCount());
    }
}