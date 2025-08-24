package com.atguigu.business.lock.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 鎖管理API響應包裝類
 * 
 * @author Kiro
 */
public class LockManagementResponse<T> {
    
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private String errorCode;
    
    public LockManagementResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public LockManagementResponse(boolean success, String message, T data) {
        this();
        this.success = success;
        this.message = message;
        this.data = data;
    }
    
    /**
     * 創建成功響應
     */
    public static <T> LockManagementResponse<T> success(T data) {
        return new LockManagementResponse<>(true, "操作成功", data);
    }
    
    /**
     * 創建成功響應（帶自定義消息）
     */
    public static <T> LockManagementResponse<T> success(String message, T data) {
        return new LockManagementResponse<>(true, message, data);
    }
    
    /**
     * 創建失敗響應
     */
    public static <T> LockManagementResponse<T> failure(String message) {
        return new LockManagementResponse<>(false, message, null);
    }
    
    /**
     * 創建失敗響應（帶錯誤碼）
     */
    public static <T> LockManagementResponse<T> failure(String message, String errorCode) {
        LockManagementResponse<T> response = new LockManagementResponse<>(false, message, null);
        response.setErrorCode(errorCode);
        return response;
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}

/**
 * 鎖列表響應DTO
 */
class LockListResponse {
    private List<LockInfoDTO> locks;
    private int totalCount;
    private String serviceFilter;
    
    public LockListResponse() {}
    
    public LockListResponse(List<LockInfoDTO> locks, int totalCount) {
        this.locks = locks;
        this.totalCount = totalCount;
    }
    
    // Getters and Setters
    public List<LockInfoDTO> getLocks() {
        return locks;
    }
    
    public void setLocks(List<LockInfoDTO> locks) {
        this.locks = locks;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public String getServiceFilter() {
        return serviceFilter;
    }
    
    public void setServiceFilter(String serviceFilter) {
        this.serviceFilter = serviceFilter;
    }
}

/**
 * 操作歷史響應DTO
 */
class OperationHistoryResponse {
    private List<StorageOperationDTO> operations;
    private int totalCount;
    private String serviceFilter;
    private String statusFilter;
    
    public OperationHistoryResponse() {}
    
    public OperationHistoryResponse(List<StorageOperationDTO> operations, int totalCount) {
        this.operations = operations;
        this.totalCount = totalCount;
    }
    
    // Getters and Setters
    public List<StorageOperationDTO> getOperations() {
        return operations;
    }
    
    public void setOperations(List<StorageOperationDTO> operations) {
        this.operations = operations;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
    
    public String getServiceFilter() {
        return serviceFilter;
    }
    
    public void setServiceFilter(String serviceFilter) {
        this.serviceFilter = serviceFilter;
    }
    
    public String getStatusFilter() {
        return statusFilter;
    }
    
    public void setStatusFilter(String statusFilter) {
        this.statusFilter = statusFilter;
    }
}