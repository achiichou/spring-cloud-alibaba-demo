package com.atguigu.business.service;

public interface BusinessService {

    /**
     * 採購（通過Feign調用其他服務）
     * @param userId            使用者id
     * @param commodityCode     商品編號
     * @param orderCount        購買數量
     */
    void purchase(String userId, String commodityCode, int orderCount);
    
    /**
     * 採購（直接操作庫存數據庫）
     * 演示在全局事務中使用分布式鎖保護直接數據庫操作
     * 
     * @param userId            使用者id
     * @param commodityCode     商品編號
     * @param orderCount        購買數量
     */
    void purchaseWithDirectStorage(String userId, String commodityCode, int orderCount);
}
