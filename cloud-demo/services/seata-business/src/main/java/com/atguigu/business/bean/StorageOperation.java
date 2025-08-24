package com.atguigu.business.bean;

import lombok.Data;
import java.io.Serializable;

/**
 * 庫存操作模型
 * 用於批量庫存操作的數據傳輸
 */
@Data
public class StorageOperation implements Serializable {
    
    /**
     * 商品編碼
     */
    private String commodityCode;
    
    /**
     * 操作數量
     */
    private int count;
    
    /**
     * 操作類型：DEDUCT(扣減), ADD(增加), SET(設置)
     */
    private String operationType;
    
    /**
     * 服務來源：business, storage
     */
    private String serviceSource;
    
    /**
     * 業務上下文信息
     */
    private String businessContext;
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 操作類型常量
     */
    public static class OperationType {
        public static final String DEDUCT = "DEDUCT";
        public static final String ADD = "ADD";
        public static final String SET = "SET";
    }
    
    /**
     * 服務來源常量
     */
    public static class ServiceSource {
        public static final String BUSINESS = "business";
        public static final String STORAGE = "storage";
    }
}