# 跨服務分布式鎖功能實現任務列表

## seata-business服務改造任務

- [x] 1. 為seata-business添加數據庫和Redis依賴
  - 在seata-business的pom.xml中添加mybatis-spring-boot-starter依賴
  - 添加mysql-connector-j依賴
  - 添加spring-boot-starter-data-redis和redisson-spring-boot-starter依賴
  - 配置適當的版本號確保與現有Spring Boot版本兼容
  - _需求: 6.1, 6.3_

- [x] 2. 配置seata-business的storage_db數據源
  - 在application.yml中添加storage數據源配置
  - 創建StorageDataSourceConfiguration配置類
  - 配置SqlSessionFactory和SqlSessionTemplate
  - 設置Mapper掃描路徑為storage相關的mapper
  - _需求: 6.1, 6.2_

- [x] 3. 在seata-business中創建storage相關的Mapper
  - 創建com.atguigu.business.mapper.storage包
  - 複製StorageTblMapper接口到business服務
  - 創建對應的XML映射文件
  - 配置正確的namespace和SQL語句
  - _需求: 2.1, 2.2_

- [x] 4. 實現seata-business的庫存操作服務
  - 創建BusinessStorageService接口
  - 實現BusinessStorageServiceImpl類
  - 添加directDeduct方法直接操作storage_db
  - 添加batchStorageOperation方法支持批量操作
  - _需求: 2.1, 2.2_

- [x] 5. 創建seata-business的庫存操作API
  - 創建BusinessStorageController類
  - 實現直接庫存扣減的REST API端點
  - 實現批量庫存操作的REST API端點
  - 添加適當的參數驗證和錯誤處理
  - _需求: 2.1, 2.4_

## 分布式鎖核心組件任務

- [x] 6. 創建跨服務分布式鎖核心接口和實現
  - 創建DistributedLock接口定義核心鎖操作方法
  - 實現RedisDistributedLock類，基於Redisson提供具體的鎖功能
  - 包含tryLock、unlock、isLocked等核心方法
  - 支持跨服務的鎖持有者識別
  - _需求: 1.1, 1.2, 1.3_

- [x] 7. 實現跨服務分布式鎖配置類
  - 創建DistributedLockProperties配置屬性類
  - 創建RedissonConfiguration配置類，設置Redisson客戶端
  - 支持從application.yml讀取Redis連接配置
  - 配置跨服務鎖的特殊參數
  - _需求: 6.1, 6.2, 6.3_

- [x] 8. 創建分布式鎖註解和相關類
  - 創建@DistributedLockable註解，支持SpEL表達式
  - 創建LockFailStrategy枚舉定義失敗處理策略
  - 創建DistributedLockException異常類和錯誤碼枚舉
  - 創建CrossServiceLockContext類記錄跨服務上下文
  - _需求: 2.1, 5.3_

- [x] 9. 實現跨服務鎖鍵生成器
  - 創建CrossServiceLockKeyGenerator類
  - 實現統一的庫存鎖鍵生成邏輯，確保兩個服務使用相同格式
  - 實現批量操作的鎖鍵生成方法
  - 支持鎖鍵的排序和去重邏輯
  - _需求: 2.1, 2.2_

- [x] 10. 實現分布式鎖AOP切面
  - 創建DistributedLockAspect切面類
  - 實現@DistributedLockable註解的攔截邏輯
  - 支持SpEL表達式解析動態鎖鍵
  - 實現跨服務鎖衝突檢測和處理
  - 記錄服務來源信息到鎖上下文
  - _需求: 2.1, 2.2, 2.3, 5.3_

## 兩個服務的鎖集成任務

- [x] 11. 在seata-business中集成分布式鎖
  - 將分布式鎖相關的類複製到seata-business項目
  - 在BusinessStorageServiceImpl的方法上添加@DistributedLockable註解
  - 配置基於commodityCode的動態鎖鍵
  - 測試鎖的獲取和釋放邏輯
  - _需求: 2.1, 2.2, 2.3_

- [x] 12. 在seata-storage中集成分布式鎖
  - 在seata-storage項目中添加分布式鎖依賴
  - 將分布式鎖相關的類複製到seata-storage項目
  - 在StorageServiceImpl的deduct方法上添加@DistributedLockable註解
  - 確保與seata-business使用相同的鎖鍵格式
  - _需求: 2.1, 2.2, 2.3_

- [x] 13. 配置兩個服務的Redis連接
  - 在seata-business的application.yml中添加Redis配置
  - 在seata-storage的application.yml中添加Redis配置
  - 確保兩個服務連接到同一個Redis實例
  - 配置相同的分布式鎖參數
  - _需求: 6.1, 6.2, 6.3_

## 監控和管理任務

- [x] 14. 創建跨服務鎖監控數據模型
  - 創建LockInfo類表示鎖信息，包含服務來源字段
  - 創建LockStatistics類表示跨服務鎖統計數據
  - 創建StorageOperation類表示庫存操作信息
  - 創建相關的DTO類用於API響應
  - _需求: 3.1, 3.4_

- [x] 15. 實現跨服務鎖監控服務
  - 創建LockMonitorService接口和實現類
  - 實現獲取所有鎖信息的功能，區分服務來源
  - 實現跨服務鎖統計信息收集和計算
  - 實現強制釋放鎖的管理功能
  - 實現跨服務鎖衝突檢測功能
  - _需求: 3.1, 3.2, 3.4, 3.5_

- [x] 16. 實現鎖管理REST API
  - 在seata-business中創建LockManagementController
  - 實現查詢當前鎖狀態的API端點
  - 實現獲取跨服務鎖統計信息的API端點
  - 實現強制釋放鎖的API端點
  - 實現跨服務鎖衝突查詢API
  - _需求: 3.4, 3.5_

- [x] 17. 實現跨服務鎖指標收集器
  - 創建CrossServiceLockMetricsCollector類
  - 集成Spring Boot Actuator指標系統
  - 記錄按服務分組的鎖獲取成功率
  - 記錄跨服務鎖衝突次數和平均等待時間
  - _需求: 3.1, 3.2, 5.1_

## 事務集成任務

- [x] 18. 實現與Seata全局事務的集成
  - 在seata-business中創建SeataGlobalLockTransactionSynchronization類
  - 實現全局事務提交後釋放鎖的邏輯
  - 實現全局事務回滾後立即釋放鎖的邏輯
  - 確保分布式鎖與全局事務生命週期同步
  - _需求: 4.1, 4.2, 4.3, 4.5_

- [x] 19. 實現與本地事務的集成
  - 在seata-storage中創建LocalLockTransactionSynchronization類
  - 實現本地事務提交後釋放鎖的邏輯
  - 實現本地事務回滾後立即釋放鎖的邏輯
  - 確保分布式鎖與本地事務生命週期同步
  - _需求: 4.1, 4.2, 4.3, 4.5_

## 測試任務

- [x] 20. 編寫跨服務分布式鎖核心功能測試
  - 為RedisDistributedLock類編寫單元測試
  - 測試跨服務鎖的獲取、釋放和超時機制
  - 測試服務標識和上下文信息的正確性
  - 使用嵌入式Redis進行測試
  - _需求: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 21. 編寫跨服務業務邏輯集成測試 (已更新為使用MySQL) - **失敗：需要重新測試**
  - 創建CrossServiceStorageIntegrationTest集成測試類
  - 測試seata-business和seata-storage同時操作庫存的場景
  - 驗證分布式鎖防止跨服務數據衝突的效果
  - 測試不同服務的事務與鎖的協同工作
  - **已修改**: 將測試數據源從H2改為MySQL storage_test_db
  - **狀態**: 資料庫配置已修改但測試尚未執行，需要驗證MySQL連接和測試功能
  - _需求: 2.1, 2.2, 2.4, 4.1, 4.2_

- [ ] 22. 編寫跨服務併發性能測試
  - 創建CrossServiceConcurrentTest性能測試類
  - 模擬兩個服務同時發起1000併發請求
  - 驗證跨服務鎖獲取響應時間在100毫秒內
  - 測試高併發場景下跨服務系統的穩定性
  - _需求: 5.1, 5.3_

- [ ] 23. 編寫端到端跨服務測試
  - 創建完整的跨服務業務流程測試
  - 測試從兩個服務的API調用到數據庫操作的完整鏈路
  - 驗證分布式鎖在真實跨服務場景中的表現
  - 測試異常恢復和故障轉移機制
  - _需求: 2.1, 2.2, 2.3, 4.1, 4.2_

## 錯誤處理和優化任務

- [ ] 24. 實現跨服務錯誤處理和降級策略
  - 在RedisDistributedLock中添加Redis連接異常處理
  - 實現指數退避重試策略
  - 添加跨服務降級邏輯當Redis不可用時
  - 實現跨服務鎖衝突的優雅處理
  - _需求: 5.2, 5.3, 5.4_

- [ ] 25. 添加跨服務健康檢查和監控
  - 實現DistributedLockHealthIndicator健康檢查
  - 集成Spring Boot Actuator暴露跨服務鎖狀態端點
  - 添加跨服務鎖相關的JMX監控指標
  - 配置跨服務鎖告警閾值和監控規則
  - _需求: 3.1, 3.2, 5.1_

- [ ] 26. 完善跨服務配置和文檔
  - 完善兩個服務application.yml中的配置註釋
  - 添加跨服務分布式鎖配置參數的詳細說明
  - 創建跨服務分布式鎖使用的開發者文檔
  - 添加跨服務場景的故障排除和最佳實踐指南
  - _需求: 6.1, 6.2, 6.4, 6.5_