package com.huabin.multids.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 主数据源配置类（springboot_db）
 *
 * 关键配置说明：
 * 1. @Primary：标记为主数据源，当有多个相同类型的Bean时，优先使用此Bean
 * 2. @MapperScan：扫描指定包下的Mapper接口，并指定使用的SqlSessionFactory
 * 3. basePackages：指定Mapper接口所在的包路径
 * 4. sqlSessionFactoryRef：指定使用的SqlSessionFactory Bean名称
 *
 * 数据源信息：
 * - 数据库：springboot_db
 * - Mapper包：com.huabin.multids.db1.mapper
 * - XML路径：classpath:mapper/db1/*.xml
 * - 实体类包：com.huabin.multids.db1.entity
 */
@Configuration
@MapperScan(
    basePackages = "com.huabin.multids.db1.mapper",  // Mapper接口包路径
    sqlSessionFactoryRef = "primarySqlSessionFactory"  // 指定SqlSessionFactory
)
public class PrimaryDataSourceConfig {

    /**
     * 创建主数据源
     *
     * @Primary 注解说明：
     * - 标记为主数据源
     * - 当有多个DataSource Bean时，默认使用此Bean
     * - 如果不指定@Qualifier，自动注入时会使用此Bean
     *
     * @ConfigurationProperties 注解说明：
     * - 自动绑定application.yml中的配置
     * - prefix指定配置前缀：spring.datasource.primary
     * - 会自动读取jdbc-url、username、password等配置
     *
     * @return DataSource 数据源对象
     */
    @Primary
    @Bean(name = "primaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        // 使用HikariCP连接池（Spring Boot 2.x 默认连接池）
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 创建主数据源的SqlSessionFactory
     *
     * SqlSessionFactory 说明：
     * - MyBatis的核心对象，用于创建SqlSession
     * - 每个数据源需要独立的SqlSessionFactory
     * - 通过SqlSessionFactory可以配置MyBatis的各种属性
     *
     * @param dataSource 主数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Primary
    @Bean(name = "primarySqlSessionFactory")
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();

        // 设置数据源
        bean.setDataSource(dataSource);

        // 设置Mapper XML文件位置
        // 支持通配符：classpath:mapper/db1/*.xml
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/db1/*.xml")
        );

        // 设置实体类别名包
        // 配置后，在XML中可以直接使用类名，不需要写全限定名
        // 例如：resultType="User" 而不是 resultType="com.huabin.multids.db1.entity.User"
        bean.setTypeAliasesPackage("com.huabin.multids.db1.entity");

        // 设置MyBatis配置
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        // 驼峰命名转换：数据库字段 user_name -> 实体类属性 userName
        configuration.setMapUnderscoreToCamelCase(true);
        // 开启二级缓存
        configuration.setCacheEnabled(true);
        // 使用列标签
        configuration.setUseColumnLabel(true);
        // 自动生成主键
        configuration.setUseGeneratedKeys(true);
        // 日志实现（开发环境）
        configuration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class);

        bean.setConfiguration(configuration);

        return bean.getObject();
    }

    /**
     * 创建主数据源的事务管理器
     *
     * DataSourceTransactionManager 说明：
     * - 用于管理数据源的事务
     * - 支持@Transactional注解
     * - 每个数据源需要独立的事务管理器
     *
     * @param dataSource 主数据源
     * @return DataSourceTransactionManager
     */
    @Primary
    @Bean(name = "primaryTransactionManager")
    public DataSourceTransactionManager primaryTransactionManager(
            @Qualifier("primaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 创建主数据源的SqlSessionTemplate
     *
     * SqlSessionTemplate 说明：
     * - MyBatis-Spring的核心类
     * - 线程安全的SqlSession实现
     * - 可以直接注入到Service中使用
     * - 自动管理SqlSession的生命周期
     *
     * @param sqlSessionFactory 主数据源的SqlSessionFactory
     * @return SqlSessionTemplate
     */
    @Primary
    @Bean(name = "primarySqlSessionTemplate")
    public SqlSessionTemplate primarySqlSessionTemplate(
            @Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
