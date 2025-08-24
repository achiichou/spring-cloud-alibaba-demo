package com.atguigu.storage.service.impl;

import com.atguigu.storage.lock.DistributedLockable;
import com.atguigu.storage.lock.LockFailStrategy;
import com.atguigu.storage.mapper.StorageTblMapper;
import com.atguigu.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class StorageServiceImpl implements StorageService {

    @Autowired
    StorageTblMapper storageTblMapper;

    @Override
    @DistributedLockable(
        key = "'storage:' + #commodityCode", 
        waitTime = 5, 
        leaseTime = 30,
        failStrategy = LockFailStrategy.EXCEPTION,
        businessContext = "storage-deduct"
    )
    @Transactional(rollbackFor = Exception.class)
    public void deduct(String commodityCode, int count) {
        storageTblMapper.deduct(commodityCode, count);
        // 模擬異常
        if (Objects.equals(5, count)) {
            throw new RuntimeException("庫存不足！");
        }
    }
}
