package com.atguigu.business.service.impl;

import com.atguigu.business.feign.OrderFeignClient;
import com.atguigu.business.feign.StorageFeignClient;
import com.atguigu.business.service.BusinessService;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class BusinessServiceImpl implements BusinessService {
    @Autowired
    private StorageFeignClient storageFeignClient;
    @Autowired
    private OrderFeignClient orderFeignClient;

    @Override
    // 使用seata全域事務
    @GlobalTransactional
    public void purchase(String userId, String commodityCode, int orderCount) {
        // 1. 扣減庫存
        storageFeignClient.deduct(commodityCode, orderCount);
        // 2. 建立訂單
        orderFeignClient.create(userId, commodityCode, orderCount);
    }
}
