package com.atguigu.business.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 測試用 MyBatis 配置
 * 覆蓋主應用的 Storage 數據源配置，使用測試數據源
 */
// @TestConfiguration
public class TestMyBatisConfiguration {
    
    /**
     * 覆蓋主應用的 storageSqlSessionFactory，使用測試數據源
     */
    // @Bean(name = "storageSqlSessionFactory")
    // @Primary
    // public SqlSessionFactory storageSqlSessionFactory(@Qualifier("dataSource") DataSource dataSource) throws Exception {
    //     SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    //     factoryBean.setDataSource(dataSource);
        
    //     // 設置 MyBatis 配置
    //     org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    //     configuration.setMapUnderscoreToCamelCase(true);
    //     configuration.setCacheEnabled(false);
    //     configuration.setLazyLoadingEnabled(false);
    //     factoryBean.setConfiguration(configuration);
        
    //     // 設置 Mapper XML 文件位置
    //     factoryBean.setMapperLocations(
    //         new PathMatchingResourcePatternResolver().getResources("classpath:mapper/storage/*.xml")
    //     );
        
    //     // 設置類型別名包
    //     factoryBean.setTypeAliasesPackage("com.atguigu.business.bean");
        
    //     return factoryBean.getObject();
    // }
    
    // /**
    //  * 覆蓋主應用的 storageSqlSessionTemplate，使用測試的 SqlSessionFactory
    //  */
    // @Bean(name = "storageSqlSessionTemplate")
    // @Primary
    // public SqlSessionTemplate storageSqlSessionTemplate(@Qualifier("storageSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
    //     return new SqlSessionTemplate(sqlSessionFactory);
    // }
}