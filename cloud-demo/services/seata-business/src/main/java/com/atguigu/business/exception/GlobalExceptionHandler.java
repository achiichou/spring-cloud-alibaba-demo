package com.atguigu.business.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局異常處理器
 * 統一處理控制器中的異常並返回標準格式的響應
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 處理請求參數驗證異常
     * 
     * @param ex HandlerMethodValidationException
     * @return 錯誤響應
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(HandlerMethodValidationException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        // 提取第一個驗證錯誤信息
        String errorMessage = "參數驗證失敗";
        if (ex.getAllValidationResults() != null && !ex.getAllValidationResults().isEmpty()) {
            var validationResult = ex.getAllValidationResults().get(0);
            if (!validationResult.getResolvableErrors().isEmpty()) {
                errorMessage = validationResult.getResolvableErrors().get(0).getDefaultMessage();
            }
        }
        
        response.put("message", errorMessage);
        response.put("error", "ValidationException");
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 處理請求體驗證異常
     * 
     * @param ex MethodArgumentNotValidException
     * @return 錯誤響應
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        // 提取第一個字段驗證錯誤信息
        String errorMessage = "請求參數驗證失敗";
        if (ex.getBindingResult().hasFieldErrors()) {
            errorMessage = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        }
        
        response.put("message", errorMessage);
        response.put("error", "ValidationException");
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 處理參數異常
     * 
     * @param ex IllegalArgumentException
     * @return 錯誤響應
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ex.getMessage());
        response.put("error", "IllegalArgumentException");
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 處理運行時異常
     * 
     * @param ex RuntimeException
     * @return 錯誤響應
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "系統異常: " + ex.getMessage());
        response.put("error", ex.getClass().getSimpleName());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 處理通用異常
     * 
     * @param ex Exception
     * @return 錯誤響應
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "系統異常: " + ex.getMessage());
        response.put("error", ex.getClass().getSimpleName());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}