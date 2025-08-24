package com.atguigu.business.service;

import com.atguigu.business.bean.StorageOperation;
import java.util.List;

/**
 * 業務服務庫存操作接口
 * 提供直接操作storage_db的功能
 */
public interface BusinessStorageService {
    
    /**
     * 業務服務直接扣減庫存
     * 該方法會直接操作storage_db數據庫，繞過storage服務
     * 
     * @param commodityCode 商品編碼
     * @param count 扣減數量
     * @param businessContext 業務上下文信息
     * @throws RuntimeException 當庫存不足或操作失敗時拋出異常
     */
    void directDeduct(String commodityCode, int count, String businessContext);
    
    /**
     * 批量庫存操作
     * 支持對多個商品進行批量庫存操作
     * 
     * @param operations 批量操作列表，包含多個庫存操作
     * @throws RuntimeException 當任何操作失敗時拋出異常
     */
    void batchStorageOperation(List<StorageOperation> operations);
}