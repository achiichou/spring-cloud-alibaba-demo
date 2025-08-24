package com.atguigu.storage.lock;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 跨服務鎖鍵生成器
 * 
 * 負責生成統一的分布式鎖鍵，確保seata-business和seata-storage兩個服務
 * 使用相同的鎖鍵格式，實現跨服務的互斥保護。
 * 
 * 支持功能：
 * - 單個商品的鎖鍵生成
 * - 批量操作的鎖鍵生成
 * - 鎖鍵的排序和去重邏輯
 * 
 * @author system
 */
@Component
public class CrossServiceLockKeyGenerator {
    
    /**
     * 分布式鎖鍵前綴，確保兩個服務使用相同格式
     */
    private static final String KEY_PREFIX = "distributed:lock:storage:";
    
    /**
     * 批量操作鎖鍵前綴
     */
    private static final String BATCH_KEY_PREFIX = KEY_PREFIX + "batch:";
    
    /**
     * 生成單個商品的庫存鎖鍵
     * 
     * 此方法生成的鎖鍵格式為：distributed:lock:storage:{commodityCode}
     * 確保seata-business和seata-storage兩個服務對同一商品使用相同的鎖鍵
     * 
     * @param commodityCode 商品編碼，不能為空
     * @return 標準化的鎖鍵
     * @throws IllegalArgumentException 當商品編碼為空時拋出
     */
    public String generateStorageLockKey(String commodityCode) {
        if (!StringUtils.hasText(commodityCode)) {
            throw new IllegalArgumentException("商品編碼不能為空");
        }
        
        // 去除前後空格並轉換為小寫，確保鍵的一致性
        String normalizedCode = commodityCode.trim().toLowerCase();
        
        return KEY_PREFIX + normalizedCode;
    }
    
    /**
     * 生成批量操作的鎖鍵
     * 
     * 對於批量操作，將所有商品編碼排序後連接，然後生成MD5哈希值
     * 這樣可以確保相同的商品組合（無論順序如何）生成相同的鎖鍵
     * 
     * @param commodityCodes 商品編碼列表，不能為空或包含空元素
     * @return 批量操作的鎖鍵
     * @throws IllegalArgumentException 當商品編碼列表為空或包含空元素時拋出
     */
    public String generateBatchLockKey(List<String> commodityCodes) {
        if (commodityCodes == null || commodityCodes.isEmpty()) {
            throw new IllegalArgumentException("商品編碼列表不能為空");
        }
        
        // 驗證列表中不包含空元素
        for (String code : commodityCodes) {
            if (!StringUtils.hasText(code)) {
                throw new IllegalArgumentException("商品編碼列表中不能包含空元素");
            }
        }
        
        // 排序和去重邏輯：先去重，再排序，最後連接
        String sortedCodes = commodityCodes.stream()
                .map(code -> code.trim().toLowerCase()) // 標準化處理
                .distinct() // 去重
                .sorted() // 排序
                .collect(Collectors.joining(","));
        
        // 生成MD5哈希值以避免鍵過長
        String md5Hash = generateMd5Hash(sortedCodes);
        
        return BATCH_KEY_PREFIX + md5Hash;
    }
    
    /**
     * 生成多個單獨的鎖鍵
     * 
     * 為批量操作中的每個商品生成獨立的鎖鍵，用於需要對每個商品
     * 單獨加鎖的場景
     * 
     * @param commodityCodes 商品編碼列表
     * @return 排序後的鎖鍵列表
     * @throws IllegalArgumentException 當商品編碼列表為空或包含空元素時拋出
     */
    public List<String> generateMultipleStorageLockKeys(List<String> commodityCodes) {
        if (commodityCodes == null || commodityCodes.isEmpty()) {
            throw new IllegalArgumentException("商品編碼列表不能為空");
        }
        
        return commodityCodes.stream()
                .map(this::generateStorageLockKey)
                .distinct() // 去重
                .sorted() // 排序，避免死鎖
                .collect(Collectors.toList());
    }
    
    /**
     * 驗證鎖鍵是否為有效的庫存鎖鍵
     * 
     * @param lockKey 要驗證的鎖鍵
     * @return 是否為有效的庫存鎖鍵
     */
    public boolean isValidStorageLockKey(String lockKey) {
        if (!StringUtils.hasText(lockKey)) {
            return false;
        }
        
        return lockKey.startsWith(KEY_PREFIX);
    }
    
    /**
     * 從鎖鍵中提取商品編碼
     * 
     * @param lockKey 鎖鍵
     * @return 商品編碼，如果無法提取則返回null
     */
    public String extractCommodityCode(String lockKey) {
        if (!isValidStorageLockKey(lockKey) || lockKey.startsWith(BATCH_KEY_PREFIX)) {
            return null;
        }
        
        return lockKey.substring(KEY_PREFIX.length());
    }
    
    /**
     * 檢查是否為批量操作鎖鍵
     * 
     * @param lockKey 鎖鍵
     * @return 是否為批量操作鎖鍵
     */
    public boolean isBatchLockKey(String lockKey) {
        return StringUtils.hasText(lockKey) && lockKey.startsWith(BATCH_KEY_PREFIX);
    }
    
    /**
     * 生成字符串的MD5哈希值
     * 
     * @param input 輸入字符串
     * @return MD5哈希值的十六進制表示
     * @throws RuntimeException 當MD5算法不可用時拋出
     */
    private String generateMd5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // 將字節數組轉換為十六進制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5算法不可用", e);
        }
    }
}