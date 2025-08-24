# Spring Cloud Alibaba 學習專案

這是一個用於學習Spring Cloud Alibaba的示範專案，包含了微服務架構的基礎組件和分散式事務處理的實踐案例。

## 專案概述

本專案基於Spring Boot 3.3.4和Spring Cloud Alibaba 2023.0.3.2構建，展示了微服務架構中的服務發現、配置管理、API網關、分散式事務等核心功能。

## 技術棧

- **Spring Boot**: 3.3.4
- **Spring Cloud**: 2023.0.3
- **Spring Cloud Alibaba**: 2023.0.3.2
- **Java**: 17
- **Maven**: 多模組專案
- **Nacos**: 服務發現與配置中心
- **Seata**: 分散式事務解決方案
- **Sentinel**: 流量控制與熔斷降級
- **Spring Cloud Gateway**: API網關
- **OpenFeign**: 服務間調用
- **MyBatis**: 資料持久化

## 專案結構

```
cloud-demo/
├── gateway/                    # API網關模組
├── model/                      # 共用實體類模組
├── services/                   # 微服務模組
│   ├── service-order/          # 基礎訂單服務
│   ├── service-product/        # 基礎商品服務
│   ├── seata-business/         # Seata業務協調服務
│   ├── seata-order/           # Seata訂單服務
│   ├── seata-storage/         # Seata庫存服務
│   ├── seata-account/         # Seata帳戶服務
│   └── static/                # SQL初始化腳本
└── pom.xml                    # 父級POM配置
```

## 服務說明

### 基礎微服務模組

#### 1. Gateway (API網關)
- **端口**: 80
- **功能**: 統一入口，路由轉發，負載均衡
- **技術**: Spring Cloud Gateway + Nacos服務發現

#### 2. Service-Product (商品服務)
- **端口**: 9000
- **服務名**: service-product
- **功能**: 商品資訊管理
- **技術**: Spring Boot + Nacos服務發現 + Sentinel流量控制

#### 3. Service-Order (訂單服務)
- **端口**: 8000
- **服務名**: service-order
- **功能**: 訂單管理，調用商品服務
- **技術**: Spring Boot + Nacos配置中心 + OpenFeign服務調用

### Seata分散式事務模組

#### 1. Seata-Business (業務協調服務)
- **端口**: 11000
- **服務名**: seata-business
- **功能**: 分散式事務協調，業務流程編排
- **技術**: Seata + OpenFeign

#### 2. Seata-Order (Seata訂單服務)
- **端口**: 12000
- **服務名**: seata-order
- **資料庫**: order_db
- **功能**: 訂單資料管理，參與分散式事務
- **技術**: Seata + MyBatis + MySQL

#### 3. Seata-Storage (Seata庫存服務)
- **端口**: 13000
- **服務名**: seata-storage
- **資料庫**: storage_db
- **功能**: 庫存管理，參與分散式事務
- **技術**: Seata + MyBatis + MySQL

#### 4. Seata-Account (Seata帳戶服務)
- **端口**: 14000
- **服務名**: seata-account
- **資料庫**: account_db
- **功能**: 帳戶餘額管理，參與分散式事務
- **技術**: Seata + MyBatis + MySQL

## 環境準備

### 必要軟體
1. **JDK 17+**
2. **Maven 3.6+**
3. **MySQL 8.0+**
4. **Nacos Server 2.x**
5. **Seata Server 1.x**

### 外部服務啟動方式

#### 1. 啟動 Nacos
# 下載Nacos Server(2.5.1版本)
- 下載位置：https://nacos.io/docs/v2.5/quickstart/quick-start/?spm=5238cd80.2ef5001f.0.0.3f613b7cdZauQc
# 啟動Nacos (單機模式)
```bash
cd /bin 資料夾
startup.cmd -m standalone
```
- 控制台地址: http://localhost:8848/nacos
- 預設帳號密碼: nacos/nacos

#### 2. 啟動 Sentinel Dashboard
# 下載Sentinel Dashboard jar 檔
- 下載位置： https://sentinelguard.io/zh-cn/docs/dashboard.html
# 啟動
```bash
java -Dserver.port=8080 -Dcsp.sentinel.dashboard.server=localhost:8080 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard.jar
```
- Dashboard URL：http://localhost:8080/#/dashboard
- 預設帳號密碼:sentinel/sentinel

#### 3. 啟動 Seata Server
# 下載 Seata-Server
- 下載位置： https://seata.apache.org/zh-cn/release-history/seata-server/
# 啟動
```bash
cd /bin 資料夾
執行 seata-server.bat
```

#### 4. 啟動 mysql & redis in docker
```bash
docker run -d --name mysql-local -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 -v mysql_data:/var/lib/mysql mysql:8.0

docker run -d --name redis -p 6379:6379 redis:7.2

```

#### 3. 初始化資料庫
執行 `services/static/seata-sql-init.sql` 腳本，創建以下資料庫：
- `storage_db`: 庫存資料庫
- `order_db`: 訂單資料庫
- `account_db`: 帳戶資料庫

每個資料庫都包含對應的業務表和Seata的`undo_log`表。


## 學習重點

### 1. Spring Cloud Alibaba基礎
- **服務發現**: 使用Nacos實現服務註冊與發現
- **配置管理**: 使用Nacos配置中心實現配置外部化
- **服務調用**: 使用OpenFeign實現聲明式服務調用
- **API網關**: 使用Spring Cloud Gateway實現統一入口
- **流量控制**: 使用Sentinel實現流量控制和熔斷降級

### 2. 分散式事務處理
- **Seata AT模式**: 自動補償的分散式事務解決方案
- **事務協調**: 業務服務協調多個資源服務的事務
- **資料一致性**: 確保跨服務的資料一致性
- **回滾機制**: 自動回滾失敗的分散式事務

## 配置說明

### Nacos配置
- 服務地址: `127.0.0.1:8848`
- 所有服務都註冊到Nacos進行服務發現
- 部分服務使用Nacos作為配置中心

### 資料庫配置
- 主機: localhost:3306
- 用戶名: root  
- 密碼: 123456
- 字符集: utf8

### 端口分配
- Gateway: 80
- Service-Order: 8000
- Service-Product: 9000
- Seata-Business: 11000
- Seata-Order: 12000
- Seata-Storage: 13000
- Seata-Account: 14000

## 注意事項

1. **啟動順序**: 確保Nacos和Seata Server在微服務之前啟動
2. **資料庫連接**: 確保MySQL服務正常運行且資料庫已初始化
3. **端口衝突**: 確保所有配置的端口未被占用
4. **版本相容性**: 注意Spring Cloud Alibaba版本與Spring Boot版本的相容性

## 學習建議

1. **循序漸進**: 先理解基礎的service-order和service-product服務
2. **實踐操作**: 通過實際調用API來理解服務間的交互
3. **分散式事務**: 重點學習Seata的AT模式和分散式事務的處理流程
4. **配置管理**: 理解Nacos配置中心的使用方式
5. **監控觀察**: 通過Nacos控制台觀察服務註冊情況

## 擴展學習

- 添加更多的業務場景測試分散式事務
- 集成Sentinel Dashboard進行流量監控
- 使用Nacos配置中心管理更多配置
- 添加分散式鏈路追蹤 (如Sleuth + Zipkin)
- 集成更多Spring Cloud組件

---

這個專案為學習Spring Cloud Alibaba提供了完整的實踐環境，涵蓋了微服務架構的核心組件和分散式事務處理的典型場景。