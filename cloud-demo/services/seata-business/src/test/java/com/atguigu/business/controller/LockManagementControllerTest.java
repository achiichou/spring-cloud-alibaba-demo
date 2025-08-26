package com.atguigu.business.controller;

import com.atguigu.business.lock.LockInfo;
import com.atguigu.business.lock.LockMonitorService;
import com.atguigu.business.lock.LockStatistics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 鎖管理控制器測試類
 * 
 * @author Kiro
 */
@WebMvcTest(LockManagementController.class)
public class LockManagementControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private LockMonitorService lockMonitorService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private LockInfo testLockInfo;
    private LockStatistics testStatistics;
    
    @BeforeEach
    void setUp() {
        // 準備測試數據
        testLockInfo = new LockInfo("test-lock-key", "test-holder", "seata-business");
        testLockInfo.setLeaseTime(30);
        testLockInfo.setRemainingTime(25);
        testLockInfo.setBusinessContext("test-context");
        testLockInfo.setLockType("STORAGE_DEDUCT");
        
        testStatistics = new LockStatistics();
        testStatistics.setTotalLockRequests(100);
        testStatistics.setSuccessfulLocks(95);
        testStatistics.setFailedLocks(5);
        testStatistics.setCurrentActiveLocks(10);
        testStatistics.calculateSuccessRate();
    }
    
    @Test
    void testGetAllLocks() throws Exception {
        // 準備模擬數據
        List<LockInfo> locks = Arrays.asList(testLockInfo);
        when(lockMonitorService.getAllLocks()).thenReturn(locks);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/locks"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].lockKey").value("test-lock-key"))
                .andExpect(jsonPath("$.data[0].serviceSource").value("seata-business"));
    }
    
    @Test
    void testGetAllLocksWithServiceFilter() throws Exception {
        // 準備模擬數據
        List<LockInfo> locks = Arrays.asList(testLockInfo);
        when(lockMonitorService.getLocksByService("seata-business")).thenReturn(locks);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/locks")
                        .param("serviceSource", "seata-business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].serviceSource").value("seata-business"));
    }
    
    @Test
    void testGetLockInfo() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getLockInfo("test-lock-key")).thenReturn(testLockInfo);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/locks/test-lock-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lockKey").value("test-lock-key"))
                .andExpect(jsonPath("$.data.holder").value("test-holder"));
    }
    
    @Test
    void testGetLockInfoNotFound() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getLockInfo("non-existent-key")).thenReturn(null);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/locks/non-existent-key"))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetLockStatistics() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getLockStatistics()).thenReturn(testStatistics);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalLockRequests").value(100))
                .andExpect(jsonPath("$.data.successfulLocks").value(95))
                .andExpect(jsonPath("$.data.successRate").value(95.0));
    }
    
    @Test
    void testGetLockStatisticsWithTimeRange() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getLockStatistics(anyLong(), anyLong())).thenReturn(testStatistics);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/statistics")
                        .param("startTime", "1640995200000")
                        .param("endTime", "1640995800000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalLockRequests").value(100));
    }
    
    @Test
    void testForceUnlock() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.forceUnlock("test-lock-key")).thenReturn(true);
        
        // 執行測試
        mockMvc.perform(delete("/api/lock-management/locks/test-lock-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }
    
    @Test
    void testBatchForceUnlock() throws Exception {
        // 準備模擬數據
        List<String> lockKeys = Arrays.asList("lock1", "lock2", "lock3");
        List<String> successfullyUnlocked = Arrays.asList("lock1", "lock3");
        when(lockMonitorService.batchForceUnlock(lockKeys)).thenReturn(successfullyUnlocked);
        
        // 執行測試
        mockMvc.perform(delete("/api/lock-management/locks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lockKeys)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }
    
    @Test
    void testBatchForceUnlockEmptyList() throws Exception {
        // 執行測試
        mockMvc.perform(delete("/api/lock-management/locks/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
    
    @Test
    void testDetectConflicts() throws Exception {
        // 準備模擬數據
        Map<String, LockMonitorService.LockConflictInfo> conflicts = new HashMap<>();
        LockMonitorService.LockConflictInfo conflictInfo = 
                new LockMonitorService.LockConflictInfo("test-lock", "holder1", "seata-business");
        conflictInfo.setWaitingServices(Arrays.asList("seata-storage"));
        conflicts.put("test-lock", conflictInfo);
        
        when(lockMonitorService.detectCrossServiceConflicts()).thenReturn(conflicts);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isMap());
    }
    
    @Test
    void testGetActiveLockCount() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getActiveLockCount()).thenReturn(5);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/active-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(5));
    }
    
    @Test
    void testGetLongHeldLocks() throws Exception {
        // 準備模擬數據
        List<LockInfo> longHeldLocks = Arrays.asList(testLockInfo);
        when(lockMonitorService.getLongHeldLocks(300L)).thenReturn(longHeldLocks);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/long-held-locks")
                        .param("thresholdSeconds", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].lockKey").value("test-lock-key"));
    }
    
    @Test
    void testResetStatistics() throws Exception {
        // 執行測試
        mockMvc.perform(post("/api/lock-management/statistics/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    void testHealthCheck() throws Exception {
        // 準備模擬數據
        when(lockMonitorService.getActiveLockCount()).thenReturn(5);
        when(lockMonitorService.getLockStatistics()).thenReturn(testStatistics);
        
        // 執行測試
        mockMvc.perform(get("/api/lock-management/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.activeLocks").value(5))
                .andExpect(jsonPath("$.data.totalRequests").value(100));
    }
}