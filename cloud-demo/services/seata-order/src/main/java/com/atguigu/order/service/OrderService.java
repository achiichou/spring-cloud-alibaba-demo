package com.atguigu.order.service;

import com.atguigu.order.bean.OrderTbl;

public interface OrderService {
    /**
     * 建立訂單
     * @param userId    使用者id
     * @param commodityCode  商品編碼
     * @param orderCount  商品數量
     */
    OrderTbl create(String userId, String commodityCode, int orderCount);
}
