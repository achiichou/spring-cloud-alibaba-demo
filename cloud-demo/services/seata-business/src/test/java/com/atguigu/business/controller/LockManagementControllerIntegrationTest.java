package com.atguigu.business.controller;

import com.atguigu.business.lock.LockInfo;
import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.LockStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 鎖管理控制器集成測試
 * 測試控制器與服務層的集成
 * 
 * @author Kiro
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
public class LockManagementControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private LockMonitorService lockMonitorService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testFullLockManagementWorkflow() throws Exception {
        // 準備測試數據
        LockInfo testLock = new LockInfo("test-lock", "test-holder", "seata-business");
        testLock.setLeaseTime(30);
        testLock.setRemainingTime(25);
        testLock.setBusinessContext("test-context");
        
        LockStatistics testStats = new LockStatistics();
        testStats.setTotalLockRequests(100);
        testStats.setSuccessfulLocks(95);
        testStats.setCurrentActiveLocks(5);
        testStats.calculateSuccessRate();
        
        // 模擬服務方法
        when(lockMonitorService.getAllLocks()).thenReturn(Arrays.asList(testLock));
        when(lockMonitorService.getLockInfo(anyString())).thenReturn(testLock);
        when(lockMonitorService.getLockStatistics()).thenReturn(testStats);
        when(lockMonitorService.getActiveLockCount()).thenReturn(5);
        when(lockMonitorService.forceUnlock(anyString())).thenReturn(true);
        when(lockMonitorService.detectCrossServiceConflicts()).thenReturn(new HashMap<>());
        when(lockMonitorService.getServiceLockUsage()).thenReturn(new HashMap<>());
        when(lockMonitorService.detectDeadlockRisk()).thenReturn(Arrays.asList());
        when(lockMonitorService.getLongHeldLocks(any(Long.class))).thenReturn(Arrays.asList());
        
        // 測試獲取所有鎖
        mockMvc.perform(get("/api/lock-management/locks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
        
        // 測試獲取統計信息
        mockMvc.perform(get("/api/lock-management/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalLockRequests").value(100));
        
        // 測試健康檢查
        mockMvc.perform(get("/api/lock-management/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
        
        // 測試強制釋放鎖
        mockMvc.perform(delete("/api/lock-management/locks/test-lock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        // 測試檢測衝突
        mockMvc.perform(get("/api/lock-management/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    void testErrorHandling() throws Exception {
        // 模擬服務異常
        when(lockMonitorService.getAllLocks()).thenThrow(new RuntimeException("Redis connection failed"));
        
        // 測試錯誤處理
        mockMvc.perform(get("/api/lock-management/locks"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Redis connection failed")));
    }
    
    @Test
    void testBatchOperations() throws Exception {
        // 準備批量操作數據
        List<String> lockKeys = Arrays.asList("lock1", "lock2", "lock3");
        List<String> successfullyUnlocked = Arrays.asList("lock1", "lock3");
        
        when(lockMonitorService.batchForceUnlock(lockKeys)).thenReturn(successfullyUnlocked);
        
        // 測試批量強制釋放
        mockMvc.perform(delete("/api/lock-management/locks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockKeys)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}