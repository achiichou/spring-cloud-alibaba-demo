package com.atguigu.business.lock.dto;

import com.atguigu.business.lock.StorageOperation;
import java.time.LocalDateTime;

/**
 * 庫存操作DTO - 用於API響應
 * 
 * @author Kiro
 */
public class StorageOperationDTO {
    
    private String operationId;
    private String commodityCode;
    private int count;
    private String operationType;
    private String operationTypeDescription;
    private String serviceSource;
    private String businessContext;
    private LocalDateTime operationTime;
    private String status;
    private String statusDescription;
    private String resultMessage;
    private Integer beforeStock;
    private Integer afterStock;
    private Integer stockChange;
    private String lockKey;
    private long duration;
    private boolean usedDistributedLock;
    private long lockWaitTime;
    private boolean success;
    
    public StorageOperationDTO() {}
    
    /**
     * 從StorageOperation轉換為DTO
     */
    public static StorageOperationDTO fromStorageOperation(StorageOperation operation) {
        StorageOperationDTO dto = new StorageOperationDTO();
        dto.setOperationId(operation.getOperationId());
        dto.setCommodityCode(operation.getCommodityCode());
        dto.setCount(operation.getCount());
        dto.setOperationType(operation.getOperationType() != null ? operation.getOperationType().name() : null);
        dto.setOperationTypeDescription(operation.getOperationType() != null ? operation.getOperationType().getDescription() : null);
        dto.setServiceSource(operation.getServiceSource());
        dto.setBusinessContext(operation.getBusinessContext());
        dto.setOperationTime(operation.getOperationTime());
        dto.setStatus(operation.getStatus() != null ? operation.getStatus().name() : null);
        dto.setStatusDescription(operation.getStatus() != null ? operation.getStatus().getDescription() : null);
        dto.setResultMessage(operation.getResultMessage());
        dto.setBeforeStock(operation.getBeforeStock());
        dto.setAfterStock(operation.getAfterStock());
        dto.setStockChange(operation.getStockChange());
        dto.setLockKey(operation.getLockKey());
        dto.setDuration(operation.getDuration());
        dto.setUsedDistributedLock(operation.isUsedDistributedLock());
        dto.setLockWaitTime(operation.getLockWaitTime());
        dto.setSuccess(operation.isSuccess());
        return dto;
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
    
    public String getOperationType() {
        return operationType;
    }
    
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    
    public String getOperationTypeDescription() {
        return operationTypeDescription;
    }
    
    public void setOperationTypeDescription(String operationTypeDescription) {
        this.operationTypeDescription = operationTypeDescription;
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
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getStatusDescription() {
        return statusDescription;
    }
    
    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
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
    
    public Integer getStockChange() {
        return stockChange;
    }
    
    public void setStockChange(Integer stockChange) {
        this.stockChange = stockChange;
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
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
}