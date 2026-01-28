# Spring Maven 项目文档目录

## 📋 文档规划

本目录用于存放 Spring Maven 项目的技术文档，重点关注依赖管理、安全漏洞修复和最佳实践。

## 📚 文档列表

### 1. 漏洞修复文档
- **[SnakeYAML漏洞升级指南.md](./SnakeYAML漏洞升级指南.md)** - 详细说明如何修复 MyBatis Spring Boot Starter 中的 SnakeYAML 漏洞
- **[Maven插件漏洞升级指南.md](./Maven插件漏洞升级指南.md)** - 详细说明如何修复 maven-compiler-plugin 中的 maven-shared-utils 漏洞

### 2. 依赖管理文档（规划中）
- Maven 依赖冲突解决方案
- 依赖版本管理最佳实践
- 第三方库安全扫描流程

### 3. 项目配置文档（规划中）
- Spring Boot 配置说明
- Maven 插件配置详解
- 多环境配置管理

## 🎯 当前任务

### 1. SnakeYAML 漏洞修复 ✅
**问题描述**：
- MyBatis Spring Boot Starter 2.1.2 依赖的 SnakeYAML 1.25 版本存在安全漏洞
- 需要升级到 SnakeYAML 2.0 版本

**解决方案**：
1. 在父 POM 中统一管理 SnakeYAML 版本
2. 使用 Maven 依赖排除机制排除旧版本
3. 显式引入安全版本的 SnakeYAML

**相关文档**：
- [SnakeYAML漏洞升级指南.md](./SnakeYAML漏洞升级指南.md) - 完整的升级步骤和验证方法

### 2. Maven 插件漏洞修复 ✅
**问题描述**：
- maven-compiler-plugin 3.8.1 依赖的 maven-shared-utils 3.2.1 版本存在安全漏洞
- 需要升级到 maven-shared-utils 3.4.1 版本

**解决方案**：
1. 在 maven-compiler-plugin 的 `<dependencies>` 中覆盖 maven-shared-utils 版本
2. 配置编译参数（JDK 1.8）
3. 验证插件依赖树

**相关文档**：
- [Maven插件漏洞升级指南.md](./Maven插件漏洞升级指南.md) - 完整的升级步骤和验证方法

## 📖 使用说明

### 文档阅读顺序
1. 先阅读本 README 了解文档结构
2. 根据具体问题查阅对应的专题文档
3. 按照文档中的步骤进行实操

### 文档更新规范
- 每次修复漏洞或优化配置后，及时更新相关文档
- 文档命名使用中文，便于团队理解
- 包含完整的代码示例和验证步骤

## 🔧 快速链接

- [父 POM 配置](../pom.xml)
- [根 POM 配置](../../pom.xml)
- [项目源码](../src)

## 📝 版本历史

| 日期 | 版本 | 说明 | 作者 |
|------|------|------|------|
| 2026-01-28 | v1.0 | 创建文档目录，添加 SnakeYAML 漏洞升级指南 | - |
| 2026-01-28 | v1.1 | 添加 Maven 插件漏洞升级指南（maven-shared-utils） | - |

## 🤝 贡献指南

如需添加或更新文档，请遵循以下规范：
1. 使用 Markdown 格式
2. 包含完整的代码示例
3. 提供验证步骤
4. 更新本 README 的文档列表
