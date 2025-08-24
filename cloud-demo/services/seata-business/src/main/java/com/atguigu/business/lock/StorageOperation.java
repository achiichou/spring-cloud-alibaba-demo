package com.atguigu.business.lock;

import java.time.LocalDateTime;

/**
 * 庫存操作信息模型 - 表示庫存操作的詳細信息
 * 
 * @author Kiro
 */
public class StorageOperation {
    
    /**
     * 操作ID
     */
    private String operationId;
    
    /**
     * 商品編碼
     */
    private String commodityCode;
    
    /**
     * 操作數量
     */
    private int count;
    
    /**
     * 操作類型 (DEDUCT, ADD, SET, QUERY)
     */
    private OperationType operationType;
    
    /**
     * 服務來源 (seata-business, seata-storage)
     */
    private String serviceSource;
    
    /**
     * 業務上下文信息
     */
    private String businessContext;
    
    /**
     * 操作時間
     */
    private LocalDateTime operationTime;
    
    /**
     * 操作狀態 (PENDING, SUCCESS, FAILED, TIMEOUT)
     */
    private OperationStatus status;
    
    /**
     * 操作結果描述
     */
    private String resultMessage;
    
    /**
     * 操作前庫存數量
     */
    private Integer beforeStock;
    
    /**
     * 操作後庫存數量
     */
    private Integer afterStock;
    
    /**
     * 關聯的分布式鎖鍵
     */
    private String lockKey;
    
    /**
     * 操作耗時（毫秒）
     */
    private long duration;
    
    /**
     * 是否使用了分布式鎖
     */
    private boolean usedDistributedLock;
    
    /**
     * 鎖等待時間（毫秒）
     */
    private long lockWaitTime;
    
    public StorageOperation() {
        this.operationTime = LocalDateTime.now();
        this.status = OperationStatus.PENDING;
    }
    
    public StorageOperation(String commodityCode, int count, OperationType operationType, String serviceSource) {
        this();
        this.commodityCode = commodityCode;
        this.count = count;
        this.operationType = operationType;
        this.serviceSource = serviceSource;
        this.operationId = generateOperationId();
    }
    
    /**
     * 生成操作ID
     */
    private String generateOperationId() {
        return serviceSource + "-" + operationType.name().toLowerCase() + "-" + System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getOperationId() {
        return operationId;
    }
    
    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
    
    public String getCommodityCode() {
        return commodityCode;
    }
    
    public void setCommodityCode(String commodityCode) {
        this.commodityCode = commodityCode;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public OperationType getOperationType() {
        return operationType;
    }
    
    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }
    
    public String getServiceSource() {
        return serviceSource;
    }
    
    public void setServiceSource(String serviceSource) {
        this.serviceSource = serviceSource;
    }
    
    public String getBusinessContext() {
        return businessContext;
    }
    
    public void setBusinessContext(String businessContext) {
        this.businessContext = businessContext;
    }
    
    public LocalDateTime getOperationTime() {
        return operationTime;
    }
    
    public void setOperationTime(LocalDateTime operationTime) {
        this.operationTime = operationTime;
    }
    
    public OperationStatus getStatus() {
        return status;
    }
    
    public void setStatus(OperationStatus status) {
        this.status = status;
    }
    
    public String getResultMessage() {
        return resultMessage;
    }
    
    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }
    
    public Integer getBeforeStock() {
        return beforeStock;
    }
    
    public void setBeforeStock(Integer beforeStock) {
        this.beforeStock = beforeStock;
    }
    
    public Integer getAfterStock() {
        return afterStock;
    }
    
    public void setAfterStock(Integer afterStock) {
        this.afterStock = afterStock;
    }
    
    public String getLockKey() {
        return lockKey;
    }
    
    public void setLockKey(String lockKey) {
        this.lockKey = lockKey;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public boolean isUsedDistributedLock() {
        return usedDistributedLock;
    }
    
    public void setUsedDistributedLock(boolean usedDistributedLock) {
        this.usedDistributedLock = usedDistributedLock;
    }
    
    public long getLockWaitTime() {
        return lockWaitTime;
    }
    
    public void setLockWaitTime(long lockWaitTime) {
        this.lockWaitTime = lockWaitTime;
    }
    
    /**
     * 標記操作成功
     */
    public void markSuccess(String message) {
        this.status = OperationStatus.SUCCESS;
        this.resultMessage = message;
    }
    
    /**
     * 標記操作失敗
     */
    public void markFailed(String message) {
        this.status = OperationStatus.FAILED;
        this.resultMessage = message;
    }
    
    /**
     * 標記操作超時
     */
    public void markTimeout(String message) {
        this.status = OperationStatus.TIMEOUT;
        this.resultMessage = message;
    }
    
    /**
     * 計算庫存變化量
     */
    public Integer getStockChange() {
        if (beforeStock != null && afterStock != null) {
            return afterStock - beforeStock;
        }
        return null;
    }
    
    /**
     * 檢查操作是否成功
     */
    public boolean isSuccess() {
        return status == OperationStatus.SUCCESS;
    }
    
    @Override
    public String toString() {
        return "StorageOperation{" +
                "operationId='" + operationId + '\'' +
                ", commodityCode='" + commodityCode + '\'' +
                ", count=" + count +
                ", operationType=" + operationType +
                ", serviceSource='" + serviceSource + '\'' +
                ", businessContext='" + businessContext + '\'' +
                ", operationTime=" + operationTime +
                ", status=" + status +
                ", resultMessage='" + resultMessage + '\'' +
                ", beforeStock=" + beforeStock +
                ", afterStock=" + afterStock +
                ", lockKey='" + lockKey + '\'' +
                ", duration=" + duration +
                ", usedDistributedLock=" + usedDistributedLock +
                ", lockWaitTime=" + lockWaitTime +
                '}';
    }
    
    /**
     * 操作類型枚舉
     */
    public enum OperationType {
        DEDUCT("扣減"),
        ADD("增加"),
        SET("設置"),
        QUERY("查詢");
        
        private final String description;
        
        OperationType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 操作狀態枚舉
     */
    public enum OperationStatus {
        PENDING("處理中"),
        SUCCESS("成功"),
        FAILED("失敗"),
        TIMEOUT("超時");
        
        private final String description;
        
        OperationStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}