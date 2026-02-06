#!/bin/bash

echo "========================================="
echo "Spring Boot 2.2.2 + Log4j2 2.25.3 测试"
echo "========================================="

# 设置 Java 1.8 环境
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH=$JAVA_HOME/bin:$PATH

echo "✅ Java 版本："
java -version

echo ""
echo "========================================="
echo "开始编译和测试..."
echo "========================================="

# 编译并运行测试
mvn clean test

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================="
    echo "✅ 测试全部通过！"
    echo "========================================="
    echo ""
    echo "是否启动应用？(y/n)"
    read -r answer
    
    if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
        echo "启动应用..."
        mvn spring-boot:run
    fi
else
    echo ""
    echo "========================================="
    echo "❌ 测试失败！"
    echo "========================================="
    exit 1
fi
