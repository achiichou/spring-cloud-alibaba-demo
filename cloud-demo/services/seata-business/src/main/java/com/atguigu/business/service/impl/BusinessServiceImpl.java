package com.atguigu.business.service.impl;

import com.atguigu.business.feign.OrderFeignClient;
import com.atguigu.business.feign.StorageFeignClient;
import com.atguigu.business.lock.DistributedLockable;
import com.atguigu.business.lock.LockFailStrategy;
import com.atguigu.business.service.BusinessService;
import com.atguigu.business.service.BusinessStorageService;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class BusinessServiceImpl implements BusinessService {
    
    private static final Logger logger = LoggerFactory.getLogger(BusinessServiceImpl.class);
    
    @Autowired
    private StorageFeignClient storageFeignClient;
    @Autowired
    private OrderFeignClient orderFeignClient;
    @Autowired
    private BusinessStorageService businessStorageService;

    @Override
    // 使用seata全域事務，分布式鎖會自動與事務生命週期同步
    @GlobalTransactional
    @DistributedLockable(
        key = "'purchase:' + #commodityCode", 
        waitTime = 10, 
        leaseTime = 60,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "global-transaction-purchase"
    )
    public void purchase(String userId, String commodityCode, int orderCount) {
        logger.info("開始全局事務購買流程 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
        
        try {
            // 1. 扣減庫存（通過Feign調用storage服務）
            logger.info("調用storage服務扣減庫存 - 商品: {}, 數量: {}", commodityCode, orderCount);
            storageFeignClient.deduct(commodityCode, orderCount);
            
            // 2. 建立訂單（通過Feign調用order服務）
            logger.info("調用order服務創建訂單 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
            orderFeignClient.create(userId, commodityCode, orderCount);
            
            logger.info("全局事務購買流程完成 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
            
        } catch (Exception e) {
            logger.error("全局事務購買流程失敗 - 用戶: {}, 商品: {}, 數量: {}, 錯誤: {}", 
                        userId, commodityCode, orderCount, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 使用業務服務直接操作庫存的購買流程
     * 這個方法演示了在全局事務中使用分布式鎖保護直接數據庫操作
     * 
     * @param userId 用戶ID
     * @param commodityCode 商品編碼
     * @param orderCount 訂單數量
     */
    @GlobalTransactional
    public void purchaseWithDirectStorage(String userId, String commodityCode, int orderCount) {
        logger.info("開始直接庫存操作的全局事務購買流程 - 用戶: {}, 商品: {}, 數量: {}", 
                   userId, commodityCode, orderCount);
        
        try {
            // 1. 直接扣減庫存（使用分布式鎖保護）
            logger.info("直接扣減庫存 - 商品: {}, 數量: {}", commodityCode, orderCount);
            businessStorageService.directDeduct(commodityCode, orderCount, 
                "global-transaction-direct-purchase");
            
            // 2. 建立訂單（通過Feign調用order服務）
            logger.info("調用order服務創建訂單 - 用戶: {}, 商品: {}, 數量: {}", userId, commodityCode, orderCount);
            orderFeignClient.create(userId, commodityCode, orderCount);
            
            logger.info("直接庫存操作的全局事務購買流程完成 - 用戶: {}, 商品: {}, 數量: {}", 
                       userId, commodityCode, orderCount);
            
        } catch (Exception e) {
            logger.error("直接庫存操作的全局事務購買流程失敗 - 用戶: {}, 商品: {}, 數量: {}, 錯誤: {}", 
                        userId, commodityCode, orderCount, e.getMessage(), e);
            throw e;
        }
    }
}
