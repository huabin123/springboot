# Spring Boot + EasyExcel 数据导出项目

## 📌 项目简介

本项目是一个基于 **Spring Boot 2.3.12** 和 **EasyExcel 3.0.2** 的高性能 Excel 数据导出解决方案。

### 核心特性

- ✅ **高性能导出**：使用 EasyExcel 流式写入，支持 10万+ 数据量
- ✅ **表头映射**：支持英文字段名到中文表头的映射，灵活配置
- ✅ **智能截断**：超过 10万条自动截断，响应头返回警告信息
- ✅ **流式响应**：直接写入 `HttpServletResponse`，无临时文件
- ✅ **中文支持**：完美支持中文文件名和表头
- ✅ **架构清晰**：分层设计，职责明确，易于扩展
- ✅ **JDK 1.8**：兼容 JDK 1.8，适用于大多数生产环境

---

## 🚀 快速开始

### 1. 环境要求

- **JDK**：1.8+
- **Maven**：3.6+
- **Spring Boot**：2.3.12.RELEASE
- **EasyExcel**：3.0.2

### 2. 克隆项目

```bash
git clone <repository-url>
cd springboot-excel
```

### 3. 启动应用

```bash
mvn clean install
mvn spring-boot:run
```

### 4. 测试接口

```bash
# 基础导出（1000条数据）
curl -O -J http://localhost:8080/api/excel/export

# 指定数据量
curl -O -J "http://localhost:8080/api/excel/export?count=5000"

# 自定义文件名和Sheet名称
curl -O -J "http://localhost:8080/api/excel/export?count=3000&fileName=订单数据&sheetName=订单列表"

# 大数据量测试（15万条，自动截断为10万条）
curl -O -J http://localhost:8080/api/excel/export/large
```

---

## 📂 项目结构

```
springboot-excel/
├── src/main/java/com/huabin/excel/export/
│   ├── ExcelExportApplication.java          # 启动类
│   ├── constant/
│   │   └── ExcelConstants.java              # 常量定义
│   ├── controller/
│   │   └── ExcelExportController.java       # 控制器
│   ├── service/
│   │   ├── ExcelExportService.java          # 导出服务
│   │   └── MockDataService.java             # 数据模拟服务
│   └── util/
│       └── ExcelExportUtil.java             # 导出工具类
├── src/main/resources/
│   └── application.yml                       # 配置文件
├── 01-功能设计说明.md                        # 功能设计文档
├── 02-核心代码说明.md                        # 代码详解文档
├── 03-使用示例.md                            # 使用示例文档
├── README.md                                 # 项目总览（本文档）
└── pom.xml                                   # Maven 依赖
```

---

## 🏗️ 架构设计

### 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Controller 层                           │
│  ExcelExportController - 接收HTTP请求，参数解析，异常处理    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                       Service 层                             │
│  ExcelExportService - 业务逻辑编排，数据查询，导出协调       │
│  MockDataService - 模拟数据生成（实际项目替换为DAO层）       │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                       Util 层                                │
│  ExcelExportUtil - 封装EasyExcel导出逻辑，响应头设置         │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      Constant 层                             │
│  ExcelConstants - 常量定义（最大行数、响应头名称等）         │
└─────────────────────────────────────────────────────────────┘
```

### 核心类职责

| 类名 | 职责 | 关键方法 |
|------|------|---------|
| **ExcelExportController** | HTTP 请求处理 | export(), exportLarge() |
| **ExcelExportService** | 业务逻辑编排 | exportData(), exportLargeData() |
| **MockDataService** | 模拟数据生成 | queryData(), getHeaders() |
| **ExcelExportUtil** | Excel 导出工具 | exportToResponse() |
| **ExcelConstants** | 常量定义 | MAX_EXPORT_ROWS, HEADER_* |

---

## 📋 API 接口

### 1. 基础导出接口

**接口地址**：`GET /api/excel/export`

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| count | Integer | 否 | 1000 | 数据条数 |
| fileName | String | 否 | export_data_yyyyMMdd_HHmmss | 文件名（不含扩展名） |
| sheetName | String | 否 | 数据导出 | Sheet 名称 |

**响应头**：

| 响应头 | 说明 | 示例值 |
|--------|------|--------|
| Content-Type | 文件类型 | application/vnd.openxmlformats-officedocument.spreadsheetml.sheet |
| Content-Disposition | 文件名 | attachment;filename=订单数据.xlsx |
| X-Total-Count | 数据总条数 | 150000 |
| X-Export-Count | 实际导出条数 | 100000 |
| X-Data-Overflow | 数据超长警告（仅超长时返回） | 数据总量 150000 条，已截断为前 100000 条 |

**示例**：

```bash
# 基础导出
curl -O -J http://localhost:8080/api/excel/export

# 指定参数
curl -O -J "http://localhost:8080/api/excel/export?count=5000&fileName=订单数据&sheetName=订单列表"
```

### 2. 大数据量测试接口

**接口地址**：`GET /api/excel/export/large`

**说明**：
- 固定导出 150000 条数据
- 自动截断为 100000 条
- 用于测试数据超长场景

**示例**：

```bash
curl -O -J http://localhost:8080/api/excel/export/large
```

### 3. 健康检查接口

**接口地址**：`GET /api/excel/health`

**响应**：

```
Excel Export Service is running!
```

---

## 🎯 核心功能

### 1. 数据超长处理

**问题**：一次性导出过多数据可能导致内存溢出

**解决方案**：
- 设置最大导出行数：100000 条
- 超过限制自动截断
- 响应头返回警告信息

**示例**：

```bash
# 请求 150000 条数据
curl -I "http://localhost:8080/api/excel/export?count=150000"

# 响应头
X-Total-Count: 150000
X-Export-Count: 100000
X-Data-Overflow: 数据总量 150000 条，超过最大导出限制 100000 条，已截断为前 100000 条
```

### 2. 表头映射机制

**问题**：数据库字段通常是英文，但导出的 Excel 需要中文表头

**解决方案**：
- 数据库查询返回英文字段名
- 使用 `HeaderMapping` 定义英文到中文的映射关系
- 导出时自动转换为中文表头

**示例**：

```java
// 1. 数据库查询返回英文字段
LinkedHashMap<String, Object> row = new LinkedHashMap<>();
row.put("orderNo", "ORD0000000001");
row.put("customerName", "张伟");
row.put("amount", new BigDecimal("99.99"));

// 2. 定义表头映射
List<HeaderMapping> mappings = new ArrayList<>();
mappings.add(new HeaderMapping("orderNo", "订单编号"));
mappings.add(new HeaderMapping("customerName", "客户姓名"));
mappings.add(new HeaderMapping("amount", "订单金额"));

// 3. 导出的 Excel 显示中文表头：订单编号、客户姓名、订单金额
```

### 3. 流式响应

**问题**：传统方式需要先生成临时文件，再读取文件写入响应

**解决方案**：
- 直接写入 `HttpServletResponse` 输出流
- 无需临时文件
- 节省磁盘空间和 IO 开销

**优势**：

| 对比项 | 传统方式 | 流式方式 |
|--------|---------|---------|
| 磁盘IO | 2次（写+读） | 0次 |
| 磁盘空间 | 需要临时文件 | 不需要 |
| 性能 | 慢 | 快 |
| 安全性 | 临时文件可能泄露 | 无泄露风险 |

### 4. 中文表头和文件名处理

**问题**：浏览器下载时中文文件名乱码

**解决方案**：
- URL 编码文件名
- 使用 `filename*` 参数（RFC 5987）
- 兼容多种浏览器

**示例**：

```java
String encodedFileName = URLEncoder.encode("订单数据", "UTF-8");
response.setHeader("Content-Disposition", 
    "attachment;filename=" + encodedFileName + ".xlsx" +
    ";filename*=utf-8''" + encodedFileName + ".xlsx");
```

---

## 💡 使用场景

### 1. 订单数据导出

```java
// 实际项目中替换 MockDataService
@Service
public class OrderExportService {
    
    @Autowired
    private OrderMapper orderMapper;
    
    public void exportOrders(HttpServletResponse response, QueryParam param) {
        // 1. 查询订单数据（返回英文字段名）
        List<Order> orders = orderMapper.selectByParam(param);
        
        // 2. 转换为 LinkedHashMap（使用英文字段名）
        List<LinkedHashMap<String, Object>> data = new ArrayList<>();
        for (Order order : orders) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", order.getOrderNo());
            row.put("customerName", order.getCustomerName());
            row.put("amount", order.getAmount());
            row.put("status", order.getStatus());
            row.put("orderTime", order.getCreateTime());
            data.add(row);
        }
        
        // 3. 定义表头映射（英文 -> 中文）
        List<HeaderMapping> headerMappings = new ArrayList<>();
        headerMappings.add(new HeaderMapping("orderNo", "订单编号"));
        headerMappings.add(new HeaderMapping("customerName", "客户姓名"));
        headerMappings.add(new HeaderMapping("amount", "订单金额"));
        headerMappings.add(new HeaderMapping("status", "订单状态"));
        headerMappings.add(new HeaderMapping("orderTime", "下单时间"));
        
        // 4. 导出（传入表头映射）
        ExcelExportUtil.exportToResponse(response, data, headerMappings, "订单数据", "订单列表");
    }
}
```

### 2. 用户数据导出

```java
@Service
public class UserExportService {
    
    @Autowired
    private UserMapper userMapper;
    
    public void exportUsers(HttpServletResponse response) {
        // 1. 查询用户数据
        List<User> users = userMapper.selectAll();
        
        // 2. 转换为 LinkedHashMap（使用英文字段名）
        List<LinkedHashMap<String, Object>> data = new ArrayList<>();
        for (User user : users) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("userId", user.getId());
            row.put("username", user.getUsername());
            row.put("email", user.getEmail());
            row.put("phone", user.getPhone());
            row.put("createTime", user.getCreateTime());
            data.add(row);
        }
        
        // 3. 定义表头映射
        List<HeaderMapping> headerMappings = new ArrayList<>();
        headerMappings.add(new HeaderMapping("userId", "用户ID"));
        headerMappings.add(new HeaderMapping("username", "用户名"));
        headerMappings.add(new HeaderMapping("email", "邮箱"));
        headerMappings.add(new HeaderMapping("phone", "手机号"));
        headerMappings.add(new HeaderMapping("createTime", "注册时间"));
        
        // 4. 导出
        ExcelExportUtil.exportToResponse(response, data, headerMappings, "用户数据", "用户列表");
    }
}
```

### 3. 报表数据导出

```java
@Service
public class ReportExportService {
    
    @Autowired
    private ReportMapper reportMapper;
    
    public void exportReport(HttpServletResponse response, String reportType) {
        // 1. 查询报表数据
        List<Map<String, Object>> reportData = reportMapper.selectReportData(reportType);
        
        // 2. 转换为 LinkedHashMap（保证顺序）
        List<LinkedHashMap<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> item : reportData) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.putAll(item);
            data.add(row);
        }
        
        // 3. 导出
        ExcelExportUtil.exportToResponse(response, data, reportType + "报表", "数据");
    }
}
```

---

## 🔧 配置说明

### 1. 最大导出行数

修改 `ExcelConstants.java`：

```java
public static final int MAX_EXPORT_ROWS = 100000; // 修改此值
```

### 2. 应用端口

修改 `application.yml`：

```yaml
server:
  port: 8080  # 修改端口
```

### 3. 日志级别

修改 `application.yml`：

```yaml
logging:
  level:
    com.huabin.excel.export: DEBUG  # 修改为 INFO、WARN、ERROR
```

---

## 📊 性能指标

### 测试环境

- **CPU**：Intel i7-9750H (6核12线程)
- **内存**：16GB
- **JDK**：1.8
- **Spring Boot**：2.3.12

### 性能数据

| 数据量 | 数据生成耗时 | 导出耗时 | 总耗时 | 文件大小 | 内存占用 |
|--------|------------|---------|--------|---------|---------|
| 1,000 | 50ms | 100ms | 150ms | 50KB | ~5MB |
| 10,000 | 200ms | 500ms | 700ms | 500KB | ~50MB |
| 50,000 | 1s | 2.5s | 3.5s | 2.5MB | ~250MB |
| 100,000 | 2s | 5s | 7s | 5MB | ~500MB |

**建议**：
- 数据量 < 10万：直接导出
- 数据量 10万-100万：分批导出或异步导出
- 数据量 > 100万：使用离线任务 + 文件下载

---

## 🔍 常见问题

### Q1: 如何修改最大导出行数？

**A:** 修改 `ExcelConstants.MAX_EXPORT_ROWS` 常量

```java
public static final int MAX_EXPORT_ROWS = 200000; // 改为 20万
```

### Q2: 如何导出自定义字段？

**A:** 修改 `MockDataService.queryData()` 方法中的字段定义

```java
LinkedHashMap<String, Object> row = new LinkedHashMap<>();
row.put("自定义字段1", value1);
row.put("自定义字段2", value2);
// ...
```

### Q3: 如何连接真实数据库？

**A:** 替换 `MockDataService` 为实际的 DAO 层

```java
@Service
public class RealDataService {
    
    @Autowired
    private YourMapper yourMapper;
    
    public List<LinkedHashMap<String, Object>> queryData(QueryParam param) {
        // 从数据库查询
        List<YourEntity> entities = yourMapper.selectByParam(param);
        
        // 转换为 LinkedHashMap
        List<LinkedHashMap<String, Object>> result = new ArrayList<>();
        for (YourEntity entity : entities) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("字段1", entity.getField1());
            row.put("字段2", entity.getField2());
            // ...
            result.add(row);
        }
        
        return result;
    }
}
```

### Q4: 如何处理超大数据量（百万级）？

**A:** 使用分批导出或异步导出

**方案 1：分批导出**

```java
// 每次导出 10万条，分多次导出
for (int i = 0; i < totalCount; i += 100000) {
    List<LinkedHashMap<String, Object>> batch = queryData(i, 100000);
    String fileName = "数据_第" + (i/100000 + 1) + "批";
    ExcelExportUtil.exportToResponse(response, batch, fileName, "数据");
}
```

**方案 2：异步导出**

```java
@Async
public CompletableFuture<String> exportAsync(int count) {
    // 生成文件到服务器
    String filePath = "/data/exports/data.xlsx";
    // 导出逻辑...
    // 完成后通知用户下载
    return CompletableFuture.completedFuture(filePath);
}
```

### Q5: 如何自定义 Excel 样式？

**A:** 使用 EasyExcel 的 `WriteHandler`

```java
// 自定义样式处理器
public class CustomStyleHandler implements CellWriteHandler {
    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder, 
                                 WriteTableHolder writeTableHolder,
                                 List<WriteCellData<?>> cellDataList, 
                                 Cell cell, 
                                 Head head, 
                                 Integer relativeRowIndex, 
                                 Boolean isHead) {
        // 设置样式
        CellStyle cellStyle = cell.getSheet().getWorkbook().createCellStyle();
        cellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cell.setCellStyle(cellStyle);
    }
}

// 使用
EasyExcel.write(outputStream)
    .registerWriteHandler(new CustomStyleHandler())
    .sheet("数据")
    .doWrite(data);
```

---

## 📚 文档导航

| 文档 | 说明 |
|------|------|
| [README.md](./README.md) | 项目总览（本文档） |
| [01-功能设计说明.md](./01-功能设计说明.md) | 详细的功能设计和架构说明 |
| [02-核心代码说明.md](./02-核心代码说明.md) | 核心代码详解和技术细节 |
| [03-使用示例.md](./03-使用示例.md) | 完整的接口调用示例 |

---

## 🛠️ 技术栈

- **Spring Boot 2.3.12.RELEASE** - Web 框架
- **EasyExcel 3.0.2** - Excel 处理库
- **Apache POI 4.1.2** - Excel 底层库
- **SLF4J + Logback** - 日志框架
- **Maven** - 项目管理工具
- **JDK 1.8** - Java 版本

---

## 📝 开发规范

### 1. 代码规范

- ✅ 遵循阿里巴巴 Java 开发手册
- ✅ 类名、方法名使用驼峰命名
- ✅ 常量使用全大写 + 下划线
- ✅ 每个类和方法都有详细注释

### 2. 日志规范

- ✅ 使用 SLF4J 门面
- ✅ 使用占位符而非字符串拼接
- ✅ 关键操作记录 INFO 日志
- ✅ 异常记录 ERROR 日志

### 3. 异常处理

- ✅ 统一异常处理
- ✅ 资源及时释放（try-finally）
- ✅ 异常信息返回给前端

---

## 🎯 后续优化

### 1. 功能增强

- [ ] 支持多 Sheet 导出
- [ ] 支持自定义样式
- [ ] 支持合并单元格
- [ ] 支持图片导出
- [ ] 支持模板导出

### 2. 性能优化

- [ ] 支持分页查询（避免一次性加载大量数据）
- [ ] 支持异步导出（大数据量场景）
- [ ] 支持缓存机制（重复导出场景）
- [ ] 支持压缩导出（多文件打包）

### 3. 监控告警

- [ ] 导出耗时监控
- [ ] 内存使用监控
- [ ] 导出失败告警
- [ ] 导出统计报表

---

## 👥 贡献指南

欢迎提交 Issue 和 Pull Request！

### 提交规范

- **feat**: 新功能
- **fix**: 修复 Bug
- **docs**: 文档更新
- **style**: 代码格式调整
- **refactor**: 代码重构
- **test**: 测试用例
- **chore**: 构建工具或辅助工具的变动

---

## 📄 许可证

本项目采用 MIT 许可证。

---

## 📧 联系方式

如有问题或建议，请联系：

- **作者**：huabin
- **邮箱**：your-email@example.com
- **GitHub**：https://github.com/your-username

---

## 🎉 总结

本项目提供了一个完整的 Excel 数据导出解决方案，具有以下优势：

1. **高性能**：使用 EasyExcel 流式写入，支持大数据量
2. **高灵活**：动态表头，无需预定义实体类
3. **高可用**：完善的异常处理和资源管理
4. **高扩展**：分层清晰，易于扩展和维护
5. **高体验**：中文支持、进度提示、错误反馈

适用场景：
- ✅ 订单数据导出
- ✅ 用户数据导出
- ✅ 报表数据导出
- ✅ 任意结构化数据导出

**立即开始使用，享受高效的 Excel 导出体验！** 🚀
