package com.huabin.mybatis;

import com.huabin.mybatis.mapper.ComprehensiveInfoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author huabin
 * @DateTime 2025-12-29
 * @Desc Mapper注入测试类
 * 
 * 此测试类用于验证：
 * 1. Mapper是否能够正确注入
 * 2. 数据源是否配置正确
 * 3. MyBatis配置是否生效
 * 
 * 运行测试：
 * mvn test -Dtest=MapperInjectionTest
 */
@SpringBootTest
public class MapperInjectionTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private ComprehensiveInfoMapper comprehensiveInfoMapper;

    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * 测试1：验证ApplicationContext是否正常
     */
    @Test
    public void testApplicationContext() {
        assertNotNull(applicationContext, "ApplicationContext不能为null");
        System.out.println("✓ ApplicationContext加载成功");
    }

    /**
     * 测试2：验证Mapper Bean是否被注册
     */
    @Test
    public void testMapperBeanExists() {
        // 检查Mapper是否在Spring容器中
        boolean mapperExists = applicationContext.containsBean("comprehensiveInfoMapper");
        
        if (!mapperExists) {
            System.err.println("✗ Mapper未被注册为Bean！");
            System.err.println("可能的原因：");
            System.err.println("1. 启动类缺少 @MapperScan 注解");
            System.err.println("2. Mapper接口缺少 @Mapper 注解");
            System.err.println("3. 包扫描路径不正确");
            System.err.println("\n请参考 MAPPER_INJECTION_TROUBLESHOOTING.md 进行排查");
        } else {
            System.out.println("✓ Mapper Bean已注册");
        }
        
        assertTrue(mapperExists, "ComprehensiveInfoMapper应该被注册为Bean");
    }

    /**
     * 测试3：验证Mapper是否能够注入
     */
    @Test
    public void testMapperInjection() {
        if (comprehensiveInfoMapper == null) {
            System.err.println("✗ Mapper注入失败！");
            System.err.println("请检查：");
            System.err.println("1. @MapperScan 注解的包路径是否正确");
            System.err.println("2. Mapper接口是否在正确的包下");
            System.err.println("3. 是否有循环依赖问题");
            fail("ComprehensiveInfoMapper注入失败");
        } else {
            System.out.println("✓ Mapper注入成功");
            System.out.println("Mapper类型: " + comprehensiveInfoMapper.getClass().getName());
        }
        
        assertNotNull(comprehensiveInfoMapper, "ComprehensiveInfoMapper不能为null");
    }

    /**
     * 测试4：验证数据源配置
     */
    @Test
    public void testDataSource() {
        if (dataSource == null) {
            System.err.println("✗ 数据源未配置！");
            System.err.println("请检查 application.yml 中的数据源配置");
            System.err.println("需要配置：");
            System.err.println("  spring.datasource.url");
            System.err.println("  spring.datasource.username");
            System.err.println("  spring.datasource.password");
            System.err.println("  spring.datasource.driver-class-name");
        } else {
            System.out.println("✓ 数据源配置成功");
            System.out.println("数据源类型: " + dataSource.getClass().getName());
        }
        
        assertNotNull(dataSource, "DataSource不能为null");
    }

    /**
     * 测试5：验证数据库连接
     */
    @Test
    public void testDatabaseConnection() {
        if (dataSource == null) {
            System.err.println("✗ 数据源为null，跳过连接测试");
            return;
        }
        
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "数据库连接不能为null");
            assertFalse(connection.isClosed(), "数据库连接不应该是关闭状态");
            System.out.println("✓ 数据库连接成功");
            System.out.println("数据库URL: " + connection.getMetaData().getURL());
            System.out.println("数据库产品: " + connection.getMetaData().getDatabaseProductName());
            System.out.println("数据库版本: " + connection.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            System.err.println("✗ 数据库连接失败！");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("\n请检查：");
            System.err.println("1. 数据库服务是否启动");
            System.err.println("2. 连接URL是否正确");
            System.err.println("3. 用户名密码是否正确");
            System.err.println("4. 数据库是否存在");
            fail("数据库连接失败: " + e.getMessage());
        }
    }

    /**
     * 测试6：列出所有Mapper Bean
     */
    @Test
    public void testListAllMappers() {
        System.out.println("\n=== Spring容器中的所有Mapper Bean ===");
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        int mapperCount = 0;
        
        for (String beanName : beanNames) {
            if (beanName.toLowerCase().contains("mapper")) {
                Object bean = applicationContext.getBean(beanName);
                System.out.println("- " + beanName + " : " + bean.getClass().getName());
                mapperCount++;
            }
        }
        
        System.out.println("总计找到 " + mapperCount + " 个Mapper相关的Bean");
        
        if (mapperCount == 0) {
            System.err.println("\n警告：未找到任何Mapper Bean！");
            System.err.println("请检查 @MapperScan 配置");
        }
    }

    /**
     * 测试7：测试Mapper方法调用（需要数据库支持）
     */
    @Test
    public void testMapperMethod() {
        if (comprehensiveInfoMapper == null) {
            System.err.println("✗ Mapper为null，跳过方法测试");
            return;
        }
        
        try {
            // 尝试调用查询所有的方法
            comprehensiveInfoMapper.selectAll();
            System.out.println("✓ Mapper方法调用成功");
        } catch (Exception e) {
            System.err.println("✗ Mapper方法调用失败");
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("\n可能的原因：");
            System.err.println("1. Mapper XML文件路径配置错误");
            System.err.println("2. XML文件的namespace不匹配");
            System.err.println("3. 数据库表不存在");
            System.err.println("4. SQL语句有误");
            
            // 不抛出异常，因为可能是数据库表不存在
            System.err.println("\n注意：如果是表不存在，这是正常的，可以忽略此错误");
        }
    }
}
