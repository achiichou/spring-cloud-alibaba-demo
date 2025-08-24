package com.atguigu.business.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 鎖監控服務驗證器
 * 用於驗證監控服務的基本功能
 * 
 * @author Kiro
 */
@Component
public class LockMonitorServiceValidator implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(LockMonitorServiceValidator.class);
    
    @Autowired(required = false)
    private LockMonitorService lockMonitorService;
    
    @Override
    public void run(String... args) throws Exception {
        if (lockMonitorService != null) {
            logger.info("Lock Monitor Service is available and ready");
            
            // 驗證基本功能
            try {
                // 測試獲取統計信息
                LockStatistics stats = lockMonitorService.getLockStatistics();
                logger.info("Initial lock statistics: Total requests: {}, Success rate: {}%", 
                           stats.getTotalLockRequests(), stats.getSuccessRate());
                
                // 測試獲取活躍鎖數量
                int activeLocks = lockMonitorService.getActiveLockCount();
                logger.info("Current active locks: {}", activeLocks);
                
                // 測試記錄事件
                lockMonitorService.recordLockEvent("test:lock:validation", "seata-business", 
                    LockMonitorService.LockOperation.ACQUIRE, true, 100);
                
                logger.info("Lock Monitor Service validation completed successfully");
                
            } catch (Exception e) {
                logger.error("Lock Monitor Service validation failed", e);
            }
        } else {
            logger.warn("Lock Monitor Service is not available");
        }
    }
}