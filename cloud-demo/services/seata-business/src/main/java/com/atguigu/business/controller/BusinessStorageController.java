package com.atguigu.business.controller;

import com.atguigu.business.bean.StorageOperation;
import com.atguigu.business.service.BusinessStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 業務服務庫存操作控制器
 * 提供直接操作storage_db的REST API接口
 */
@RestController
@RequestMapping("/business/storage")
public class BusinessStorageController {

    @Autowired
    private BusinessStorageService businessStorageService;

    /**
     * 直接庫存扣減API
     * 業務服務直接操作storage_db進行庫存扣減
     * 
     * @param commodityCode 商品編碼
     * @param count 扣減數量
     * @param businessContext 業務上下文（可選）
     * @return 操作結果
     */
    @PostMapping("/deduct")
    public ResponseEntity<Map<String, Object>> directDeduct(
            @RequestParam @NotBlank(message = "商品編碼不能為空") String commodityCode,
            @RequestParam @Min(value = 1, message = "扣減數量必須大於0") int count,
            @RequestParam(required = false, defaultValue = "business-direct-deduct") String businessContext) {
        
        // 執行庫存扣減
        businessStorageService.directDeduct(commodityCode, count, businessContext);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "庫存扣減成功");
        response.put("data", Map.of(
            "commodityCode", commodityCode,
            "count", count,
            "businessContext", businessContext
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 批量庫存操作API
     * 支持對多個商品進行批量庫存操作
     * 
     * @param request 批量操作請求
     * @return 操作結果
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchStorageOperation(
            @RequestBody @Valid BatchStorageRequest request) {
        
        // 驗證每個操作項
        for (int i = 0; i < request.getOperations().size(); i++) {
            StorageOperation operation = request.getOperations().get(i);
            String validationError = validateStorageOperation(operation, i);
            if (validationError != null) {
                throw new IllegalArgumentException(validationError);
            }
        }
        
        // 執行批量操作
        businessStorageService.batchStorageOperation(request.getOperations());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "批量庫存操作成功");
        response.put("data", Map.of(
            "operationCount", request.getOperations().size(),
            "operations", request.getOperations()
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * 驗證庫存操作對象
     * 
     * @param operation 庫存操作對象
     * @param index 操作索引
     * @return 驗證錯誤信息，null表示驗證通過
     */
    private String validateStorageOperation(StorageOperation operation, int index) {
        if (operation == null) {
            return String.format("第%d個操作對象不能為空", index + 1);
        }
        
        if (!StringUtils.hasText(operation.getCommodityCode())) {
            return String.format("第%d個操作的商品編碼不能為空", index + 1);
        }
        
        if (operation.getCount() <= 0) {
            return String.format("第%d個操作的數量必須大於0", index + 1);
        }
        
        if (!StringUtils.hasText(operation.getOperationType())) {
            return String.format("第%d個操作的操作類型不能為空", index + 1);
        }
        
        // 驗證操作類型是否有效
        if (!StorageOperation.OperationType.DEDUCT.equals(operation.getOperationType()) &&
            !StorageOperation.OperationType.ADD.equals(operation.getOperationType()) &&
            !StorageOperation.OperationType.SET.equals(operation.getOperationType())) {
            return String.format("第%d個操作的操作類型無效，支持的類型: DEDUCT, ADD, SET", index + 1);
        }
        
        // 設置默認值
        if (!StringUtils.hasText(operation.getServiceSource())) {
            operation.setServiceSource(StorageOperation.ServiceSource.BUSINESS);
        }
        
        if (!StringUtils.hasText(operation.getBusinessContext())) {
            operation.setBusinessContext("business-batch-operation");
        }
        
        return null;
    }

    /**
     * 批量操作請求對象
     */
    public static class BatchStorageRequest {
        @NotEmpty(message = "操作列表不能為空")
        private List<StorageOperation> operations;

        public List<StorageOperation> getOperations() {
            return operations;
        }

        public void setOperations(List<StorageOperation> operations) {
            this.operations = operations;
        }
    }
}