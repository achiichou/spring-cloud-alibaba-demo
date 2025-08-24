package com.atguigu.business.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Storage数据源配置类
 * 用于配置seata-business服务访问storage_db数据库的数据源
 */
@Configuration
@MapperScan(basePackages = "com.atguigu.business.mapper.storage", sqlSessionTemplateRef = "storageSqlSessionTemplate")
public class StorageDataSourceConfiguration {

    /**
     * 配置storage数据源
     * 从application.yml中读取spring.datasource.storage配置
     */
    @Bean(name = "storageDataSource")
    @ConfigurationProperties("spring.datasource.storage")
    public DataSource storageDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 配置storage数据源的SqlSessionFactory
     * 设置mapper文件位置为classpath:mapper/storage/*.xml
     */
    @Bean(name = "storageSqlSessionFactory")
    public SqlSessionFactory storageSqlSessionFactory(@Qualifier("storageDataSource") DataSource dataSource)
            throws Exception {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        // 设置mapper XML文件的位置
        bean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/storage/*.xml"));
        return bean.getObject();
    }

    /**
     * 配置storage数据源的SqlSessionTemplate
     * 用于执行SQL操作
     */
    @Bean(name = "storageSqlSessionTemplate")
    public SqlSessionTemplate storageSqlSessionTemplate(
            @Qualifier("storageSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}