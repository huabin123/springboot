package com.huabin.mybatisplus;


import java.util.Arrays;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.AutoGenerator;
import com.baomidou.mybatisplus.generator.config.*;
import com.baomidou.mybatisplus.generator.config.po.TableFill;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;

public class CodeGenerator {

    public static void main(String[] args) {
        // 创建代码生成器
        AutoGenerator generator = new AutoGenerator();

        // 全局配置
        GlobalConfig globalConfig = new GlobalConfig();
        String projectPath = System.getProperty("user.dir");
        globalConfig.setOutputDir(projectPath + "/src/main/java");
        globalConfig.setAuthor("Your Name");
        globalConfig.setOpen(false);
        globalConfig.setIdType(IdType.AUTO);
        globalConfig.setDateType(DateType.ONLY_DATE);
        generator.setGlobalConfig(globalConfig);

        // 数据源配置
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.setDbType(DbType.MYSQL);
        dataSourceConfig.setUrl("jdbc:mysql://localhost:3306/db_name");
        dataSourceConfig.setUsername("username");
        dataSourceConfig.setPassword("password");
        generator.setDataSource(dataSourceConfig);

        // 包配置
        PackageConfig packageConfig = new PackageConfig();
        packageConfig.setParent("com.example");
        packageConfig.setModuleName("module_name");
        generator.setPackageInfo(packageConfig);

        // 策略配置
        StrategyConfig strategyConfig = new StrategyConfig();
        strategyConfig.setNaming(NamingStrategy.underline_to_camel);
        strategyConfig.setColumnNaming(NamingStrategy.underline_to_camel);
        strategyConfig.setEntityLombokModel(true);
        strategyConfig.setRestControllerStyle(true);
        strategyConfig.setControllerMappingHyphenStyle(true);
        strategyConfig.setLogicDeleteFieldName("deleted");
        strategyConfig.setEntityTableFieldAnnotationEnable(true);
        strategyConfig.setEntityBooleanColumnRemoveIsPrefix(true);
        strategyConfig.setVersionFieldName("version");
        strategyConfig.setTableFillList(Arrays.asList(
                new TableFill("create_time", FieldFill.INSERT),
                new TableFill("update_time", FieldFill.INSERT_UPDATE)
        ));
        generator.setStrategy(strategyConfig);

        // 执行生成代码
        generator.execute();
    }
}

