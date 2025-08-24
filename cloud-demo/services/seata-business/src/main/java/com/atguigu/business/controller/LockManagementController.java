package com.atguigu.business.controller;

import com.atguigu.business.lock.LockInfo;
import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.LockStatistics;
import com.atguigu.business.lock.CrossServiceLockMetricsCollector;
import com.atguigu.business.lock.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 跨服務分布式鎖管理REST API控制器
 * 提供鎖狀態查詢、統計信息獲取、強制釋放和衝突檢測等管理功能
 * 
 * @author Kiro
 */
@RestController
@RequestMapping("/api/lock-management")
public class LockManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(LockManagementController.class);
    
    @Autowired
    private LockMonitorService lockMonitorService;
    
    @Autowired(required = false)
    private CrossServiceLockMetricsCollector metricsCollector;
    
    /**
     * 獲取所有當前鎖狀態
     * 
     * @param serviceSource 可選的服務來源過濾器 (seata-business, seata-storage)
     * @return 鎖信息列表
     */
    @GetMapping("/locks")
    public ResponseEntity<LockManagementResponse<List<LockInfoDTO>>> getAllLocks(
            @RequestParam(required = false) String serviceSource) {
        
        try {
            logger.info("查詢所有鎖狀態，服務過濾器: {}", serviceSource);
            
            List<LockInfo> locks;
            if (serviceSource != null && !serviceSource.trim().isEmpty()) {
                locks = lockMonitorService.getLocksByService(serviceSource);
            } else {
                locks = lockMonitorService.getAllLocks();
            }
            
            List<LockInfoDTO> lockDTOs = locks.stream()
                    .map(LockInfoDTO::fromLockInfo)
                    .collect(Collectors.toList());
            
            String message = serviceSource != null ? 
                    String.format("成功獲取服務 %s 的 %d 個鎖信息", serviceSource, lockDTOs.size()) :
                    String.format("成功獲取所有 %d 個鎖信息", lockDTOs.size());
            
            return ResponseEntity.ok(LockManagementResponse.success(message, lockDTOs));
            
        } catch (Exception e) {
            logger.error("獲取鎖狀態失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取鎖狀態失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取指定鎖的詳細信息
     * 
     * @param lockKey 鎖鍵
     * @return 鎖詳細信息
     */
    @GetMapping("/locks/{lockKey}")
    public ResponseEntity<LockManagementResponse<LockInfoDTO>> getLockInfo(
            @PathVariable String lockKey) {
        
        try {
            logger.info("查詢鎖詳細信息: {}", lockKey);
            
            LockInfo lockInfo = lockMonitorService.getLockInfo(lockKey);
            if (lockInfo == null) {
                return ResponseEntity.notFound().build();
            }
            
            LockInfoDTO lockDTO = LockInfoDTO.fromLockInfo(lockInfo);
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取鎖信息", lockDTO));
            
        } catch (Exception e) {
            logger.error("獲取鎖信息失敗: {}", lockKey, e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取鎖信息失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取跨服務鎖統計信息
     * 
     * @param startTime 可選的開始時間（毫秒時間戳）
     * @param endTime 可選的結束時間（毫秒時間戳）
     * @return 統計信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<LockManagementResponse<LockStatisticsDTO>> getLockStatistics(
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        try {
            logger.info("獲取鎖統計信息，時間範圍: {} - {}", startTime, endTime);
            
            LockStatistics statistics;
            if (startTime != null && endTime != null) {
                statistics = lockMonitorService.getLockStatistics(startTime, endTime);
            } else {
                statistics = lockMonitorService.getLockStatistics();
            }
            
            LockStatisticsDTO statisticsDTO = LockStatisticsDTO.fromLockStatistics(statistics);
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取鎖統計信息", statisticsDTO));
            
        } catch (Exception e) {
            logger.error("獲取鎖統計信息失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取鎖統計信息失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 強制釋放指定鎖
     * 
     * @param lockKey 鎖鍵
     * @return 操作結果
     */
    @DeleteMapping("/locks/{lockKey}")
    public ResponseEntity<LockManagementResponse<Boolean>> forceUnlock(
            @PathVariable String lockKey) {
        
        try {
            logger.warn("強制釋放鎖: {}", lockKey);
            
            boolean success = lockMonitorService.forceUnlock(lockKey);
            
            if (success) {
                // 記錄強制釋放事件
                lockMonitorService.recordLockEvent(lockKey, "seata-business", 
                        LockMonitorService.LockOperation.FORCE_UNLOCK, true, 0);
                
                return ResponseEntity.ok(LockManagementResponse.success(
                        "成功強制釋放鎖: " + lockKey, true));
            } else {
                return ResponseEntity.ok(LockManagementResponse.success(
                        "鎖不存在或已釋放: " + lockKey, false));
            }
            
        } catch (Exception e) {
            logger.error("強制釋放鎖失敗: {}", lockKey, e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("強制釋放鎖失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 批量強制釋放鎖
     * 
     * @param lockKeys 鎖鍵列表
     * @return 成功釋放的鎖鍵列表
     */
    @DeleteMapping("/locks/batch")
    public ResponseEntity<LockManagementResponse<List<String>>> batchForceUnlock(
            @RequestBody List<String> lockKeys) {
        
        try {
            logger.warn("批量強制釋放鎖: {}", lockKeys);
            
            if (lockKeys == null || lockKeys.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(LockManagementResponse.failure("鎖鍵列表不能為空"));
            }
            
            List<String> successfullyUnlocked = lockMonitorService.batchForceUnlock(lockKeys);
            
            // 記錄批量強制釋放事件
            for (String lockKey : successfullyUnlocked) {
                lockMonitorService.recordLockEvent(lockKey, "seata-business", 
                        LockMonitorService.LockOperation.FORCE_UNLOCK, true, 0);
            }
            
            String message = String.format("批量強制釋放完成，成功釋放 %d/%d 個鎖", 
                    successfullyUnlocked.size(), lockKeys.size());
            
            return ResponseEntity.ok(LockManagementResponse.success(message, successfullyUnlocked));
            
        } catch (Exception e) {
            logger.error("批量強制釋放鎖失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("批量強制釋放鎖失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 檢測跨服務鎖衝突
     * 
     * @return 衝突信息映射
     */
    @GetMapping("/conflicts")
    public ResponseEntity<LockManagementResponse<Map<String, LockConflictInfoDTO>>> detectConflicts() {
        
        try {
            logger.info("檢測跨服務鎖衝突");
            
            Map<String, LockMonitorService.LockConflictInfo> conflicts = 
                    lockMonitorService.detectCrossServiceConflicts();
            
            // 轉換為DTO
            Map<String, LockConflictInfoDTO> conflictDTOs = conflicts.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> LockConflictInfoDTO.fromLockConflictInfo(entry.getValue())
                    ));
            
            String message = conflictDTOs.isEmpty() ? 
                    "未檢測到跨服務鎖衝突" : 
                    String.format("檢測到 %d 個跨服務鎖衝突", conflictDTOs.size());
            
            return ResponseEntity.ok(LockManagementResponse.success(message, conflictDTOs));
            
        } catch (Exception e) {
            logger.error("檢測跨服務鎖衝突失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("檢測跨服務鎖衝突失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取各服務的鎖使用情況
     * 
     * @return 服務鎖使用統計
     */
    @GetMapping("/service-usage")
    public ResponseEntity<LockManagementResponse<Map<String, ServiceLockUsageDTO>>> getServiceLockUsage() {
        
        try {
            logger.info("獲取各服務鎖使用情況");
            
            Map<String, LockMonitorService.ServiceLockUsage> serviceUsage = 
                    lockMonitorService.getServiceLockUsage();
            
            // 轉換為DTO
            Map<String, ServiceLockUsageDTO> serviceUsageDTOs = serviceUsage.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> ServiceLockUsageDTO.fromServiceLockUsage(entry.getValue())
                    ));
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取服務鎖使用情況", serviceUsageDTOs));
            
        } catch (Exception e) {
            logger.error("獲取服務鎖使用情況失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取服務鎖使用情況失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取活躍鎖數量
     * 
     * @return 活躍鎖數量
     */
    @GetMapping("/active-count")
    public ResponseEntity<LockManagementResponse<Integer>> getActiveLockCount() {
        
        try {
            int activeCount = lockMonitorService.getActiveLockCount();
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取活躍鎖數量", activeCount));
            
        } catch (Exception e) {
            logger.error("獲取活躍鎖數量失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取活躍鎖數量失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 檢測死鎖風險
     * 
     * @return 死鎖風險信息列表
     */
    @GetMapping("/deadlock-risk")
    public ResponseEntity<LockManagementResponse<List<DeadlockRiskInfoDTO>>> detectDeadlockRisk() {
        
        try {
            logger.info("檢測死鎖風險");
            
            List<LockMonitorService.DeadlockRiskInfo> deadlockRisks = 
                    lockMonitorService.detectDeadlockRisk();
            
            // 轉換為DTO
            List<DeadlockRiskInfoDTO> deadlockRiskDTOs = deadlockRisks.stream()
                    .map(DeadlockRiskInfoDTO::fromDeadlockRiskInfo)
                    .collect(Collectors.toList());
            
            String message = deadlockRiskDTOs.isEmpty() ? 
                    "未檢測到死鎖風險" : 
                    String.format("檢測到 %d 個潛在死鎖風險", deadlockRiskDTOs.size());
            
            return ResponseEntity.ok(LockManagementResponse.success(message, deadlockRiskDTOs));
            
        } catch (Exception e) {
            logger.error("檢測死鎖風險失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("檢測死鎖風險失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取持有時間過長的鎖
     * 
     * @param thresholdSeconds 閾值時間（秒），默認300秒（5分鐘）
     * @return 持有時間過長的鎖信息列表
     */
    @GetMapping("/long-held-locks")
    public ResponseEntity<LockManagementResponse<List<LockInfoDTO>>> getLongHeldLocks(
            @RequestParam(defaultValue = "300") long thresholdSeconds) {
        
        try {
            logger.info("獲取持有時間超過 {} 秒的鎖", thresholdSeconds);
            
            List<LockInfo> longHeldLocks = lockMonitorService.getLongHeldLocks(thresholdSeconds);
            
            List<LockInfoDTO> lockDTOs = longHeldLocks.stream()
                    .map(LockInfoDTO::fromLockInfo)
                    .collect(Collectors.toList());
            
            String message = String.format("找到 %d 個持有時間超過 %d 秒的鎖", 
                    lockDTOs.size(), thresholdSeconds);
            
            return ResponseEntity.ok(LockManagementResponse.success(message, lockDTOs));
            
        } catch (Exception e) {
            logger.error("獲取長時間持有鎖失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取長時間持有鎖失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 重置統計信息
     * 
     * @return 操作結果
     */
    @PostMapping("/statistics/reset")
    public ResponseEntity<LockManagementResponse<String>> resetStatistics() {
        
        try {
            logger.info("重置鎖統計信息");
            
            lockMonitorService.resetStatistics();
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功重置鎖統計信息", "統計信息已重置"));
            
        } catch (Exception e) {
            logger.error("重置統計信息失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("重置統計信息失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 健康檢查端點
     * 
     * @return 健康狀態
     */
    @GetMapping("/health")
    public ResponseEntity<LockManagementResponse<Map<String, Object>>> healthCheck() {
        
        try {
            int activeLocks = lockMonitorService.getActiveLockCount();
            LockStatistics statistics = lockMonitorService.getLockStatistics();
            
            Map<String, Object> healthInfo = Map.of(
                    "status", "UP",
                    "activeLocks", activeLocks,
                    "totalRequests", statistics.getTotalLockRequests(),
                    "successRate", statistics.getSuccessRate(),
                    "crossServiceConflicts", statistics.getCrossServiceConflicts()
            );
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "分布式鎖服務運行正常", healthInfo));
            
        } catch (Exception e) {
            logger.error("健康檢查失敗", e);
            
            Map<String, Object> healthInfo = Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            );
            
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("分布式鎖服務異常: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取跨服務分布式鎖指標統計
     * 
     * @return 指標統計信息
     */
    @GetMapping("/metrics/statistics")
    public ResponseEntity<LockManagementResponse<Map<String, Object>>> getLockMetricsStatistics() {
        try {
            logger.info("查詢跨服務分布式鎖指標統計");
            
            if (metricsCollector == null) {
                return ResponseEntity.ok(LockManagementResponse.failure(
                        "指標收集器未啟用"));
            }
            
            Map<String, CrossServiceLockMetricsCollector.ServiceLockStats> allStats = 
                    metricsCollector.getAllServiceStats();
            
            Map<String, Object> metricsData = allStats.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> {
                                CrossServiceLockMetricsCollector.ServiceLockStats stats = entry.getValue();
                                return Map.of(
                                        "totalRequests", stats.getTotalRequests(),
                                        "successfulRequests", stats.getSuccessfulRequests(),
                                        "successRate", String.format("%.2f%%", stats.getSuccessRate() * 100),
                                        "averageWaitTime", String.format("%.2f ms", stats.getAverageWaitTime()),
                                        "averageHoldTime", String.format("%.2f ms", stats.getAverageHoldTime()),
                                        "estimatedActiveLocks", stats.getEstimatedActiveLocks()
                                );
                            }
                    ));
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取跨服務鎖指標統計", metricsData));
            
        } catch (Exception e) {
            logger.error("獲取鎖指標統計失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取指標統計失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 獲取指定服務的鎖指標統計
     * 
     * @param serviceName 服務名稱
     * @return 服務的指標統計信息
     */
    @GetMapping("/metrics/statistics/{serviceName}")
    public ResponseEntity<LockManagementResponse<Map<String, Object>>> getServiceLockMetrics(
            @PathVariable String serviceName) {
        try {
            logger.info("查詢服務 {} 的分布式鎖指標統計", serviceName);
            
            if (metricsCollector == null) {
                return ResponseEntity.ok(LockManagementResponse.failure(
                        "指標收集器未啟用"));
            }
            
            CrossServiceLockMetricsCollector.ServiceLockStats stats = 
                    metricsCollector.getServiceStats(serviceName);
            
            if (stats == null) {
                return ResponseEntity.ok(LockManagementResponse.failure(
                        "未找到服務 " + serviceName + " 的指標數據"));
            }
            
            Map<String, Object> serviceMetrics = Map.of(
                    "serviceName", serviceName,
                    "totalRequests", stats.getTotalRequests(),
                    "successfulRequests", stats.getSuccessfulRequests(),
                    "successRate", String.format("%.2f%%", stats.getSuccessRate() * 100),
                    "averageWaitTime", String.format("%.2f ms", stats.getAverageWaitTime()),
                    "averageHoldTime", String.format("%.2f ms", stats.getAverageHoldTime()),
                    "estimatedActiveLocks", stats.getEstimatedActiveLocks()
            );
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功獲取服務 " + serviceName + " 的鎖指標統計", serviceMetrics));
            
        } catch (Exception e) {
            logger.error("獲取服務 {} 的鎖指標統計失敗", serviceName, e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("獲取服務指標統計失敗: " + e.getMessage()));
        }
    }
    
    /**
     * 重置指標統計數據
     * 
     * @return 重置結果
     */
    @PostMapping("/metrics/reset")
    public ResponseEntity<LockManagementResponse<String>> resetMetricsStatistics() {
        try {
            logger.info("重置跨服務分布式鎖指標統計");
            
            if (metricsCollector == null) {
                return ResponseEntity.ok(LockManagementResponse.failure(
                        "指標收集器未啟用"));
            }
            
            metricsCollector.resetStats();
            
            return ResponseEntity.ok(LockManagementResponse.success(
                    "成功重置跨服務鎖指標統計", "指標統計數據已重置"));
            
        } catch (Exception e) {
            logger.error("重置鎖指標統計失敗", e);
            return ResponseEntity.internalServerError()
                    .body(LockManagementResponse.failure("重置指標統計失敗: " + e.getMessage()));
        }
    }
}