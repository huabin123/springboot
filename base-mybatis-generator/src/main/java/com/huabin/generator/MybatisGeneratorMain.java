package com.huabin.generator;

import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.Configuration;
import org.mybatis.generator.config.Context;
import org.mybatis.generator.config.TableConfiguration;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.exception.XMLParserException;
import org.mybatis.generator.internal.DefaultShellCallback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author huabin
 * @DateTime 2023-04-25 10:40
 * @Desc
 */
public class MybatisGeneratorMain {

    public static void main(String[] args) throws Exception {
        List<String> warningList = new ArrayList<>();
        File file = new File("/Users/huabin/workspace/playground/springboot/base-mybatis-generator/src/main/resources/generatorConfig.xml");
        ConfigurationParser configurationParser = new ConfigurationParser(warningList);
        Configuration configuration = configurationParser.parseConfiguration(file);

        Context context = configuration.getContexts().get(0);
        List<TableConfiguration> tableConfigurations = context.getTableConfigurations();
        tableConfigurations.forEach( tableConfiguration -> {
            tableConfiguration.setSelectByExampleStatementEnabled(false);
            tableConfiguration.setDeleteByExampleStatementEnabled(false);
            tableConfiguration.setCountByExampleStatementEnabled(false);
            tableConfiguration.setUpdateByExampleStatementEnabled(false);
            tableConfiguration.setSelectByExampleQueryId("false");
        });

        DefaultShellCallback callback = new DefaultShellCallback(true);
        MyBatisGenerator myBatisGenerator = new MyBatisGenerator(configuration, callback, warningList);
        myBatisGenerator.generate(null);
    }

}
