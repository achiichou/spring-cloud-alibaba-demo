package com.atguigu.business.controller;

import com.atguigu.business.bean.StorageOperation;
import com.atguigu.business.service.BusinessStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BusinessStorageController 測試類
 */
@WebMvcTest(BusinessStorageController.class)
public class BusinessStorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusinessStorageService businessStorageService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testDirectDeduct_Success() throws Exception {
        // 模擬服務調用成功
        doNothing().when(businessStorageService).directDeduct(anyString(), anyInt(), anyString());

        mockMvc.perform(post("/business/storage/deduct")
                .param("commodityCode", "C001")
                .param("count", "10")
                .param("businessContext", "test-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("庫存扣減成功"))
                .andExpect(jsonPath("$.data.commodityCode").value("C001"))
                .andExpect(jsonPath("$.data.count").value(10));

        verify(businessStorageService, times(1)).directDeduct("C001", 10, "test-context");
    }

    @Test
    public void testDirectDeduct_EmptyCommodityCode() throws Exception {
        mockMvc.perform(post("/business/storage/deduct")
                .param("commodityCode", "")
                .param("count", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("商品編碼不能為空"));

        verify(businessStorageService, never()).directDeduct(anyString(), anyInt(), anyString());
    }

    @Test
    public void testDirectDeduct_InvalidCount() throws Exception {
        mockMvc.perform(post("/business/storage/deduct")
                .param("commodityCode", "C001")
                .param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("扣減數量必須大於0"));

        verify(businessStorageService, never()).directDeduct(anyString(), anyInt(), anyString());
    }

    @Test
    public void testDirectDeduct_ServiceException() throws Exception {
        // 模擬服務拋出異常
        doThrow(new RuntimeException("庫存不足")).when(businessStorageService)
                .directDeduct(anyString(), anyInt(), anyString());

        mockMvc.perform(post("/business/storage/deduct")
                .param("commodityCode", "C001")
                .param("count", "10"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系統異常: 庫存不足"));
    }

    @Test
    public void testBatchStorageOperation_Success() throws Exception {
        // 準備測試數據
        StorageOperation operation1 = new StorageOperation();
        operation1.setCommodityCode("C001");
        operation1.setCount(10);
        operation1.setOperationType(StorageOperation.OperationType.DEDUCT);
        operation1.setServiceSource(StorageOperation.ServiceSource.BUSINESS);
        operation1.setBusinessContext("test-batch");

        StorageOperation operation2 = new StorageOperation();
        operation2.setCommodityCode("C002");
        operation2.setCount(5);
        operation2.setOperationType(StorageOperation.OperationType.ADD);
        operation2.setServiceSource(StorageOperation.ServiceSource.BUSINESS);
        operation2.setBusinessContext("test-batch");

        List<StorageOperation> operations = Arrays.asList(operation1, operation2);

        BusinessStorageController.BatchStorageRequest request = new BusinessStorageController.BatchStorageRequest();
        request.setOperations(operations);

        // 模擬服務調用成功
        doNothing().when(businessStorageService).batchStorageOperation(anyList());

        mockMvc.perform(post("/business/storage/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("批量庫存操作成功"))
                .andExpect(jsonPath("$.data.operationCount").value(2));

        verify(businessStorageService, times(1)).batchStorageOperation(anyList());
    }

    @Test
    public void testBatchStorageOperation_EmptyOperations() throws Exception {
        BusinessStorageController.BatchStorageRequest request = new BusinessStorageController.BatchStorageRequest();
        request.setOperations(Arrays.asList());

        mockMvc.perform(post("/business/storage/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("操作列表不能為空"));

        verify(businessStorageService, never()).batchStorageOperation(anyList());
    }

    @Test
    public void testBatchStorageOperation_InvalidOperation() throws Exception {
        // 準備無效的測試數據
        StorageOperation invalidOperation = new StorageOperation();
        invalidOperation.setCommodityCode(""); // 空的商品編碼
        invalidOperation.setCount(10);
        invalidOperation.setOperationType(StorageOperation.OperationType.DEDUCT);

        List<StorageOperation> operations = Arrays.asList(invalidOperation);

        BusinessStorageController.BatchStorageRequest request = new BusinessStorageController.BatchStorageRequest();
        request.setOperations(operations);

        mockMvc.perform(post("/business/storage/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("第1個操作的商品編碼不能為空"));

        verify(businessStorageService, never()).batchStorageOperation(anyList());
    }
}