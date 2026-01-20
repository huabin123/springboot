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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc 从数据源配置类（springboot_db2）
 *
 * 关键配置说明：
 * 1. 不使用@Primary注解：因为主数据源已经使用了@Primary
 * 2. @MapperScan：扫描指定包下的Mapper接口，并指定使用的SqlSessionFactory
 * 3. basePackages：指定Mapper接口所在的包路径（与主数据源不同）
 * 4. sqlSessionFactoryRef：指定使用的SqlSessionFactory Bean名称
 *
 * 数据源信息：
 * - 数据库：springboot_db2
 * - Mapper包：com.huabin.multids.db2.mapper
 * - XML路径：classpath:mapper/db2/*.xml
 * - 实体类包：com.huabin.multids.db2.entity
 *
 * 注意事项：
 * - 所有Bean名称必须与主数据源不同
 * - Mapper包路径必须与主数据源不同
 * - XML文件路径必须与主数据源不同
 */
@Configuration
@MapperScan(
    basePackages = "com.huabin.multids.db2.mapper",  // Mapper接口包路径
    sqlSessionFactoryRef = "secondarySqlSessionFactory"  // 指定SqlSessionFactory
)
public class SecondaryDataSourceConfig {

    /**
     * 创建从数据源
     *
     * 注意：
     * - 不使用@Primary注解
     * - Bean名称为secondaryDataSource（与主数据源不同）
     * - 配置前缀为spring.datasource.secondary
     *
     * @return DataSource 数据源对象
     */
    @Bean(name = "secondaryDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.secondary")
    public DataSource secondaryDataSource() {
        // 使用HikariCP连接池
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * 创建从数据源的SqlSessionFactory
     *
     * 配置说明：
     * - 使用secondaryDataSource数据源
     * - Mapper XML文件路径：classpath:mapper/db2/*.xml
     * - 实体类别名包：com.huabin.multids.db2.entity
     *
     * @param dataSource 从数据源
     * @return SqlSessionFactory
     * @throws Exception 配置异常
     */
    @Bean(name = "secondarySqlSessionFactory")
    public SqlSessionFactory secondarySqlSessionFactory(
            @Qualifier("secondaryDataSource") DataSource dataSource) throws Exception {

        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();

        // 设置数据源
        bean.setDataSource(dataSource);

        // 设置Mapper XML文件位置
        // 注意：路径为mapper/db2/*.xml，与主数据源不同
        bean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/db2/*.xml")
        );

        // 设置实体类别名包
        // 注意：包路径为com.huabin.multids.db2.entity，与主数据源不同
        bean.setTypeAliasesPackage("com.huabin.multids.db2.entity");

        // 设置MyBatis配置
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        // 驼峰命名转换
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
     * 创建从数据源的事务管理器
     *
     * 说明：
     * - Bean名称为secondaryTransactionManager
     * - 管理secondaryDataSource的事务
     * - 在Service中使用@Transactional时，需要指定transactionManager
     *
     * 使用示例：
     * @Transactional(transactionManager = "secondaryTransactionManager")
     * public void someMethod() {
     *     // 使用从数据源的事务
     * }
     *
     * @param dataSource 从数据源
     * @return DataSourceTransactionManager
     */
    @Bean(name = "secondaryTransactionManager")
    public DataSourceTransactionManager secondaryTransactionManager(
            @Qualifier("secondaryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 创建从数据源的SqlSessionTemplate
     *
     * 说明：
     * - Bean名称为secondarySqlSessionTemplate
     * - 可以直接注入到Service中使用
     * - 线程安全，自动管理SqlSession生命周期
     *
     * 使用示例：
     * @Autowired
     * @Qualifier("secondarySqlSessionTemplate")
     * private SqlSessionTemplate sqlSessionTemplate;
     *
     * @param sqlSessionFactory 从数据源的SqlSessionFactory
     * @return SqlSessionTemplate
     */
    @Bean(name = "secondarySqlSessionTemplate")
    public SqlSessionTemplate secondarySqlSessionTemplate(
            @Qualifier("secondarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    /**
     * 创建从数据源的事务模板
     *
     * 说明：
     * - Bean名称为secondaryTransactionTemplate
     * - 用于编程式事务管理
     * - 在异步任务中手动管理事务
     *
     * 使用示例：
     * transactionTemplate.execute(status -> {
     *     // 在事务中执行的代码
     *     return result;
     * });
     *
     * @param transactionManager 从数据源的事务管理器
     * @return TransactionTemplate
     */
    @Bean(name = "secondaryTransactionTemplate")
    public TransactionTemplate secondaryTransactionTemplate(
            @Qualifier("secondaryTransactionManager") DataSourceTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
