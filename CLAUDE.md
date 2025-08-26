# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Development Commands

### Prerequisites
Before starting development, ensure these services are running:

1. **Nacos Server** (Download from: https://nacos.io/docs/v2.5/quickstart/quick-start/)
   ```bash
   cd nacos/bin
   startup.cmd -m standalone
   ```
   - Console: http://localhost:8848/nacos (nacos/nacos)

2. **MySQL and Redis** via Docker:
   ```bash
   docker run -d --name mysql-local -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 -v mysql_data:/var/lib/mysql mysql:8.0
   docker run -d --name redis -p 6379:6379 redis:7.2
   ```

3. **Initialize databases** by running: `services/static/seata-sql-init.sql`

4. **Seata Server** (Download from: https://seata.apache.org/zh-cn/release-history/seata-server/)
   ```bash
   cd seata/bin
   seata-server.bat
   ```

### Build Commands
```bash
# Build entire project
mvn clean install

# Build specific service
cd cloud-demo/services/service-order
mvn clean package

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=DistributedLockAspectTest

# Run integration tests
mvn verify
```

### Running Services
Each service runs on a specific port:
- Gateway: 80
- Service-Order: 8000  
- Service-Product: 9000
- Seata-Business: 11000
- Seata-Order: 12000
- Seata-Storage: 13000
- Seata-Account: 14000

```bash
# Run individual service
cd cloud-demo/services/service-order
mvn spring-boot:run

# Or run the compiled JAR
java -jar target/service-order-0.0.1-SNAPSHOT.jar
```

## Architecture Overview

This is a Spring Cloud Alibaba microservices demonstration project featuring:

### Core Services Architecture
- **Gateway**: Spring Cloud Gateway for API routing and load balancing
- **Service-Order & Service-Product**: Basic microservices demonstrating service discovery and Feign client communication
- **Seata Transaction Services**: seata-business, seata-order, seata-storage, seata-account implementing distributed transaction patterns

### Key Technology Integration Points
1. **Service Discovery**: All services register with Nacos for discovery and configuration
2. **Distributed Transactions**: Seata AT mode with automatic compensation
3. **API Gateway**: Centralized routing with custom filters and predicates
4. **Configuration Management**: Nacos config center for external configuration
5. **Distributed Locking**: Redis-based cross-service locking mechanism

### Distributed Lock Implementation
The project implements a sophisticated distributed locking system across seata-business and seata-storage services:

- **Cross-Service Coordination**: Both services can operate on the same storage_db with mutual exclusion
- **Redis-Based Locking**: Uses Redisson client for high-performance distributed locks
- **Annotation-Driven**: `@DistributedLockable` annotation with SpEL expression support
- **Seata Integration**: Automatic lock release on transaction commit/rollback
- **Monitoring & Metrics**: Comprehensive lock usage statistics and conflict detection

Key lock classes: `DistributedLockAspect`, `RedisDistributedLock`, `LockMonitorService`

### Configuration Patterns
- **Multi-DataSource**: seata-business accesses both its own DB and storage_db
- **Environment-Specific**: Profile-based configuration (dev, test, prod)
- **Centralized Properties**: Common settings in parent POM managed via Spring Cloud Alibaba dependency management

## Database Schema
- `storage_db`: Contains storage_tbl and undo_log tables
- `order_db`: Contains order_tbl and undo_log tables  
- `account_db`: Contains account_tbl and undo_log tables
- Each database includes Seata's `undo_log` table for transaction compensation

## Testing Strategy
- **Unit Tests**: Individual component testing with MockMvc and embedded Redis
- **Integration Tests**: Cross-service interaction testing with TestContainers
- **Performance Tests**: Concurrent lock acquisition and distributed transaction load testing
- **End-to-End Tests**: Full transaction flow validation including rollback scenarios

## Service Communication
- **Feign Clients**: Declarative REST client with fallback support
- **Load Balancing**: Spring Cloud LoadBalancer integration
- **Circuit Breaking**: Sentinel integration for fault tolerance
- **Token Propagation**: Custom interceptors for request context sharing

## Monitoring and Observability
- **Actuator Endpoints**: Health, metrics, and prometheus endpoints exposed
- **Lock Metrics**: Custom metrics for distributed lock performance
- **Cross-Service Monitoring**: Conflict detection and resolution tracking
- **Business Metrics**: Transaction success rates and processing times

## Common Development Patterns
- **Transaction Coordination**: Use `@GlobalTransactional` in seata-business for cross-service transactions
- **Lock Usage**: Apply `@DistributedLockable(key = "'storage:' + #commodityCode")` for storage operations
- **Error Handling**: Global exception handlers with specific error codes for business scenarios
- **Configuration Binding**: Use `@ConfigurationProperties` for type-safe configuration management

## Version Information
- Spring Boot: 3.3.4
- Spring Cloud: 2023.0.3
- Spring Cloud Alibaba: 2023.0.3.2
- Java: 17
- Maven: Multi-module project structure

## Testing Framework and Dependencies

### Key Testing Libraries
- **Embedded Redis**: `it.ozimov:embedded-redis:0.7.3` for unit testing
- **TestContainers**: For integration testing with real Redis instances
- **Spring Boot Test**: Standard testing framework with MockMvc support

### Test Categories
- **Unit Tests**: Mock-based testing (e.g., `RedisDistributedLockTest`)
- **Core Tests**: Focused functionality tests (e.g., `RedisDistributedLockCoreTest`) 
- **Integration Tests**: End-to-end testing with external dependencies
- **Performance Tests**: Concurrent and load testing (see `/performance` directories)

### Running Performance Tests
```bash
# Windows batch script for performance testing
cd cloud-demo/services/seata-business
test-performance.bat

# Or run specific performance test class
mvn test -Dtest=CrossServiceConcurrentTest
```

## Multi-DataSource Configuration

### seata-business Service Database Access
The seata-business service uniquely accesses **both** its own database and storage_db:
- **Primary DataSource**: Configured for business operations
- **Storage DataSource**: Direct access to storage_db for cross-service coordination
- **Mapper Configuration**: Separate mapper packages for different datasources
  - `com.atguigu.business.mapper.storage` for storage_db operations

### Key Configuration Pattern
```yaml
spring:
  datasource:
    # Primary datasource configuration
    url: jdbc:mysql://localhost:3306/storage_db
    # Additional storage datasource configuration handled by custom DataSourceConfiguration
```

## Distributed Lock Implementation Details

### Annotation Usage Patterns
- **Basic Storage Lock**: `@DistributedLockable(key = "'storage:' + #commodityCode")`
- **Batch Operations**: `@DistributedLockable(key = "'batch:' + T(java.util.Arrays).toString(#commodityCodes)")`
- **Cross-Service Context**: All locks include service identifier and business context

### Lock Key Generation Strategy
- **Unified Format**: `distributed:lock:storage:{commodityCode}` across both services
- **Cross-Service Generator**: `CrossServiceLockKeyGenerator` ensures consistent key format
- **Service Identification**: Each service instance tagged with unique identifier

### Testing Distributed Locks
- **Embedded Redis**: Used for fast unit testing
- **TestContainers**: For integration testing with real Redis
- **Concurrent Testing**: Multi-threaded scenarios in performance tests
- **Cross-Service Simulation**: Tests simulate multiple service instances

## Service-Specific Notes

### seata-business (Port 11000)
- **Unique Feature**: Direct storage_db access via separate datasource
- **Lock Management API**: REST endpoints for lock monitoring and management
- **Metrics Collection**: Custom metrics for distributed lock performance
- **Global Transaction Coordination**: Uses `@GlobalTransactional` for cross-service transactions

### seata-storage (Port 13000) 
- **Standard Pattern**: Single datasource to storage_db
- **Local Transaction Integration**: Uses `LocalLockTransactionSynchronization`
- **Shared Lock Components**: Same distributed lock implementation as seata-business

## IDE and Development Tools Support

### No Cursor or Copilot Rules
- No `.cursorrules` or `.github/copilot-instructions.md` files found
- Standard IDE support without custom AI assistant configuration

### Kiro Specifications
The project includes comprehensive design specifications in `.kiro/specs/distributed-lock/`:
- **design.md**: Complete architectural design document
- **requirements.md**: Functional and non-functional requirements  
- **tasks.md**: Implementation task breakdown

# important-instruction-reminders
Do what has been asked; nothing more, nothing less.
NEVER create files unless they're absolutely necessary for achieving your goal.
ALWAYS prefer editing an existing file to creating a new one.
NEVER proactively create documentation files (*.md) or README files. Only create documentation files if explicitly requested by the User.