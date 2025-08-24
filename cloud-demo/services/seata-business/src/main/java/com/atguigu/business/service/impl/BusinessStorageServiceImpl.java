package com.atguigu.business.service.impl;

import com.atguigu.business.bean.StorageOperation;
import com.atguigu.business.bean.StorageTbl;
import com.atguigu.business.lock.DistributedLockable;
import com.atguigu.business.lock.LockFailStrategy;
import com.atguigu.business.mapper.storage.StorageTblMapper;
import com.atguigu.business.service.BusinessStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 業務服務庫存操作實現類
 * 提供直接操作storage_db的功能
 */
@Slf4j
@Service
public class BusinessStorageServiceImpl implements BusinessStorageService {

    @Autowired
    private StorageTblMapper storageTblMapper;

    /**
     * 業務服務直接扣減庫存
     * 該方法會直接操作storage_db數據庫，繞過storage服務
     * 
     * @param commodityCode 商品編碼
     * @param count 扣減數量
     * @param businessContext 業務上下文信息
     * @throws RuntimeException 當庫存不足或操作失敗時拋出異常
     */
    @Override
    @DistributedLockable(
        key = "'storage:' + #commodityCode", 
        waitTime = 5, 
        leaseTime = 30,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "business-direct-deduct"
    )
    @Transactional(rollbackFor = Exception.class)
    public void directDeduct(String commodityCode, int count, String businessContext) {
        log.info("開始直接扣減庫存 - 商品編碼: {}, 扣減數量: {}, 業務上下文: {}", 
                commodityCode, count, businessContext);
        
        try {
            // 參數驗證
            if (commodityCode == null || commodityCode.trim().isEmpty()) {
                throw new IllegalArgumentException("商品編碼不能為空");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("扣減數量必須大於0");
            }
            
            // 檢查庫存是否存在
            StorageTbl storage = storageTblMapper.selectByCommodityCode(commodityCode);
            if (storage == null) {
                throw new RuntimeException("商品不存在: " + commodityCode);
            }
            
            // 檢查庫存是否足夠
            if (storage.getCount() < count) {
                log.warn("庫存不足 - 商品編碼: {}, 當前庫存: {}, 需要扣減: {}", 
                        commodityCode, storage.getCount(), count);
                throw new RuntimeException("庫存不足，當前庫存: " + storage.getCount() + ", 需要扣減: " + count);
            }
            
            // 執行庫存扣減
            storageTblMapper.deduct(commodityCode, count);
            
            log.info("庫存扣減成功 - 商品編碼: {}, 扣減數量: {}, 業務上下文: {}", 
                    commodityCode, count, businessContext);
            
        } catch (Exception e) {
            log.error("庫存扣減失敗 - 商品編碼: {}, 扣減數量: {}, 業務上下文: {}, 錯誤: {}", 
                    commodityCode, count, businessContext, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 批量庫存操作
     * 支持對多個商品進行批量庫存操作
     * 
     * @param operations 批量操作列表，包含多個庫存操作
     * @throws RuntimeException 當任何操作失敗時拋出異常
     */
    @Override
    @DistributedLockable(
        key = "'batch:' + T(java.util.Arrays).toString(#operations.![commodityCode].toArray())", 
        waitTime = 10, 
        leaseTime = 60,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "business-batch-operation"
    )
    @Transactional(rollbackFor = Exception.class)
    public void batchStorageOperation(List<StorageOperation> operations) {
        log.info("開始批量庫存操作 - 操作數量: {}", operations != null ? operations.size() : 0);
        
        if (operations == null || operations.isEmpty()) {
            log.warn("批量操作列表為空，跳過處理");
            return;
        }
        
        try {
            for (int i = 0; i < operations.size(); i++) {
                StorageOperation operation = operations.get(i);
                log.info("執行第{}個操作 - 商品編碼: {}, 操作類型: {}, 數量: {}, 業務上下文: {}", 
                        i + 1, operation.getCommodityCode(), operation.getOperationType(), 
                        operation.getCount(), operation.getBusinessContext());
                
                // 參數驗證
                validateOperation(operation, i + 1);
                
                // 根據操作類型執行相應的庫存操作
                switch (operation.getOperationType()) {
                    case StorageOperation.OperationType.DEDUCT:
                        executeDeductOperation(operation);
                        break;
                    case StorageOperation.OperationType.ADD:
                        executeAddOperation(operation);
                        break;
                    case StorageOperation.OperationType.SET:
                        executeSetOperation(operation);
                        break;
                    default:
                        throw new IllegalArgumentException("不支持的操作類型: " + operation.getOperationType());
                }
                
                log.info("第{}個操作執行成功", i + 1);
            }
            
            log.info("批量庫存操作全部完成 - 成功操作數量: {}", operations.size());
            
        } catch (Exception e) {
            log.error("批量庫存操作失敗 - 錯誤: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 驗證操作參數
     */
    private void validateOperation(StorageOperation operation, int index) {
        if (operation == null) {
            throw new IllegalArgumentException("第" + index + "個操作不能為null");
        }
        if (operation.getCommodityCode() == null || operation.getCommodityCode().trim().isEmpty()) {
            throw new IllegalArgumentException("第" + index + "個操作的商品編碼不能為空");
        }
        if (operation.getOperationType() == null || operation.getOperationType().trim().isEmpty()) {
            throw new IllegalArgumentException("第" + index + "個操作的操作類型不能為空");
        }
        if (operation.getCount() <= 0) {
            throw new IllegalArgumentException("第" + index + "個操作的數量必須大於0");
        }
    }
    
    /**
     * 執行扣減操作
     */
    private void executeDeductOperation(StorageOperation operation) {
        // 檢查庫存是否存在和足夠
        StorageTbl storage = storageTblMapper.selectByCommodityCode(operation.getCommodityCode());
        if (storage == null) {
            throw new RuntimeException("商品不存在: " + operation.getCommodityCode());
        }
        if (storage.getCount() < operation.getCount()) {
            throw new RuntimeException("庫存不足，商品: " + operation.getCommodityCode() + 
                    ", 當前庫存: " + storage.getCount() + ", 需要扣減: " + operation.getCount());
        }
        
        storageTblMapper.deduct(operation.getCommodityCode(), operation.getCount());
    }
    
    /**
     * 執行增加操作
     */
    private void executeAddOperation(StorageOperation operation) {
        // 檢查商品是否存在
        StorageTbl storage = storageTblMapper.selectByCommodityCode(operation.getCommodityCode());
        if (storage == null) {
            throw new RuntimeException("商品不存在: " + operation.getCommodityCode());
        }
        
        storageTblMapper.addStock(operation.getCommodityCode(), operation.getCount());
    }
    
    /**
     * 執行設置操作
     */
    private void executeSetOperation(StorageOperation operation) {
        // 檢查商品是否存在
        StorageTbl storage = storageTblMapper.selectByCommodityCode(operation.getCommodityCode());
        if (storage == null) {
            throw new RuntimeException("商品不存在: " + operation.getCommodityCode());
        }
        
        storageTblMapper.setStock(operation.getCommodityCode(), operation.getCount());
    }
}