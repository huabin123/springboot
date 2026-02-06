#!/bin/bash

# 设置 Java 1.8 环境变量
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH=$JAVA_HOME/bin:$PATH

echo "✅ Java 环境已切换到 1.8"
echo "JAVA_HOME: $JAVA_HOME"
java -version
