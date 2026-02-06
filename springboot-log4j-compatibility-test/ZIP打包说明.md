# ✅ Log4j 依赖已打包为 ZIP 格式

## 📦 打包信息

- **格式**：ZIP
- **大小**：约 2.0M
- **文件名**：log4j-dependencies-YYYYMMDD_HHMMSS.zip
- **包含依赖**：3个核心依赖（log4j-api、log4j-core、log4j-slf4j-impl）

## 🎯 为什么使用 ZIP 格式？

相比 tar.gz，ZIP 格式有以下优势：

### 1. **跨平台兼容性更好**
- ✅ Windows 原生支持（双击即可解压）
- ✅ macOS 原生支持
- ✅ Linux 原生支持
- ❌ tar.gz 在 Windows 上需要额外工具

### 2. **更方便的解压方式**
```bash
# ZIP 解压（所有平台）
unzip log4j-dependencies-*.zip

# tar.gz 解压（需要记住参数）
tar -xzf log4j-dependencies-*.tar.gz
```

### 3. **内网环境更友好**
- Windows 服务器可以直接解压
- 不需要安装额外的解压工具

## 📁 ZIP 包内容

```
log4j-dependencies-YYYYMMDD_HHMMSS.zip
└── log4j-dependencies-export/
    ├── repository/
    │   └── org/apache/logging/log4j/
    │       ├── log4j-api/2.25.3/
    │       │   ├── log4j-api-2.25.3.jar (342K)
    │       │   ├── log4j-api-2.25.3.pom
    │       │   └── ...
    │       ├── log4j-core/2.25.3/
    │       │   ├── log4j-core-2.25.3.jar (1.9M)
    │       │   ├── log4j-core-2.25.3.pom
    │       │   └── ...
    │       └── log4j-slf4j-impl/2.25.3/
    │           ├── log4j-slf4j-impl-2.25.3.jar (25K)
    │           ├── log4j-slf4j-impl-2.25.3.pom
    │           └── ...
    ├── dependencies-list.txt
    ├── import-to-nexus.sh
    ├── settings.xml
    └── README.md
```

## 🚀 使用方法

### Windows 环境

#### 方式1：图形界面
1. 右键点击 `log4j-dependencies-*.zip`
2. 选择"解压到当前文件夹"或"解压到 log4j-dependencies-export\"
3. 进入解压后的目录

#### 方式2：命令行
```cmd
# PowerShell
Expand-Archive -Path log4j-dependencies-*.zip -DestinationPath .

# 或使用 7-Zip
7z x log4j-dependencies-*.zip
```

### Linux/macOS 环境

```bash
# 解压
unzip log4j-dependencies-*.zip

# 进入目录
cd log4j-dependencies-export

# 查看内容
ls -la
```

## 🔍 验证 ZIP 包

### 查看 ZIP 包内容（不解压）

```bash
# Linux/macOS
unzip -l log4j-dependencies-*.zip

# Windows PowerShell
Add-Type -Assembly System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::OpenRead("log4j-dependencies-*.zip").Entries
```

### 测试解压

```bash
# 使用测试脚本
./test-unzip.sh
```

## 📊 文件完整性

### ZIP 包中的文件清单

| 文件类型 | 数量 | 说明 |
|---------|------|------|
| .jar 文件 | 3 | Log4j 核心依赖 |
| .pom 文件 | 3 | Maven 项目描述文件 |
| .sha1 文件 | 6 | 校验和文件 |
| 配置文件 | 4 | 导入脚本和说明文档 |

### 验证文件完整性

```bash
# 解压后验证
cd log4j-dependencies-export

# 检查 jar 文件
find repository -name "*.jar" -ls

# 检查 pom 文件
find repository -name "*.pom" -ls

# 验证 SHA1 校验和
cd repository/org/apache/logging/log4j/log4j-api/2.25.3
sha1sum -c log4j-api-2.25.3.jar.sha1
```

## ⚠️ 注意事项

### 1. 解压路径
- 建议解压到没有中文和空格的路径
- 避免路径过长（Windows 有路径长度限制）

### 2. 文件权限
- Linux/macOS 解压后需要给脚本添加执行权限：
  ```bash
  chmod +x import-to-nexus.sh
  ```

### 3. 压缩比
- ZIP 使用标准压缩算法
- 压缩比：约 50%（原始 4M → 压缩后 2M）
- 如果需要更高压缩比，可以使用 7z 格式

## 🔄 如果需要 tar.gz 格式

如果你的环境更适合 tar.gz 格式，可以手动转换：

```bash
# 解压 ZIP
unzip log4j-dependencies-*.zip

# 重新打包为 tar.gz
tar -czf log4j-dependencies.tar.gz log4j-dependencies-export/

# 清理
rm -rf log4j-dependencies-export/
```

## 📝 导出脚本说明

导出脚本 `export-log4j-dependencies.sh` 已更新为默认生成 ZIP 格式：

```bash
# 配置
PACKAGE_NAME="log4j-dependencies-${TIMESTAMP}.zip"

# 打包命令
zip -r "${PACKAGE_NAME}" "${EXPORT_DIR}"
```

如果需要修改为其他格式，编辑脚本中的打包部分即可。

## 🎉 总结

- ✅ ZIP 格式跨平台兼容性最好
- ✅ 解压简单，无需额外工具
- ✅ 文件完整，包含所有必需依赖
- ✅ 大小适中（约 2.0M）
- ✅ 适合内网传输和部署

## 📞 技术支持

如有问题，请联系：huabin

## 📅 更新日期

2026-02-04
