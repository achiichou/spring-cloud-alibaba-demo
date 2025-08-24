package com.atguigu.business.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 數據源配置測試
 * 驗證測試環境的數據源配置是否正確
 */
@SpringBootTest(
    classes = com.atguigu.business.SeataBusinessMainApplication.class,
    properties = {
        "spring.autoconfigure.exclude=org.apache.seata.spring.boot.autoconfigure.SeataAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataCoreAutoConfiguration,org.apache.seata.spring.boot.autoconfigure.SeataDataSourceAutoConfiguration,com.alibaba.cloud.seata.SeataAutoConfiguration"
    }
)
@Import({TestMyBatisConfiguration.class})
@ActiveProfiles("test")
public class DataSourceConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    public void testDataSourceConnection() throws SQLException {
        assertNotNull(dataSource, "DataSource should not be null");
        
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "Connection should not be null");
            assertFalse(connection.isClosed(), "Connection should be open");
            
            // 測試簡單查詢
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT 1");
            assertTrue(resultSet.next(), "Should have at least one result");
            assertEquals(1, resultSet.getInt(1), "Result should be 1");
        }
    }
}