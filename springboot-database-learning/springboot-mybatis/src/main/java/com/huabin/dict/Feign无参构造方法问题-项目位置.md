# Feign 无参构造方法问题演示项目

## 项目位置

完整的演示项目已创建在：

```
/Users/huabin/workspace/playground/my-github/springboot/feign-noargs-demo/
```

## 快速访问

```bash
cd /Users/huabin/workspace/playground/my-github/springboot/feign-noargs-demo
```

## 项目内容

这是一个完整的 Spring Cloud 微服务项目，用于演示和解决 **Feign 调用时因缺少无参构造方法导致 JSON 反序列化失败** 的问题。

### 包含内容

1. **Provider Service** - 服务提供者（端口 8081）
2. **Consumer Service** - 服务消费者（端口 8082）
3. **3 种 DTO 版本** - 问题版本、修复版本、Lombok 版本
4. **完整文档** - README、测试指南、原理图解
5. **启动脚本** - 一键启动、停止、测试

### 核心文档

| 文档 | 说明 |
|------|------|
| `README.md` | 项目主文档（必读） |
| `问题原理图解.md` | 可视化原理图解（推荐） |
| `项目说明.md` | 详细项目说明 |
| `测试指南.md` | 详细测试步骤 |
| `项目文件清单.md` | 文件清单 |

### 快速开始

```bash
# 1. 进入项目目录
cd /Users/huabin/workspace/playground/my-github/springboot/feign-noargs-demo

# 2. 启动服务
./启动脚本.sh

# 3. 快速测试
./快速测试.sh

# 4. 停止服务
./停止脚本.sh
```

### 测试接口

启动服务后，可以访问以下接口：

```bash
# 场景 1: 问题复现（预期失败）
curl http://localhost:8082/test/problem/1

# 场景 2: 问题修复（预期成功）
curl http://localhost:8082/test/fixed/1

# 场景 3: Lombok 版本（预期成功）
curl http://localhost:8082/test/lombok/1
```

## 问题总结

### 问题原因

在使用 Spring Cloud + Feign 开发微服务时，如果 DTO 类只定义了有参构造方法而没有无参构造方法，会导致 Feign 远程调用失败。

**根本原因**：
- Java 规则：定义有参构造后，编译器不再提供默认无参构造
- JSON 反序列化：Jackson 需要先调用无参构造创建对象实例
- Feign 机制：使用 Jackson 进行 JSON 反序列化

### 解决方案

#### 方案 1: 手动添加无参构造（适合不使用 Lombok）

```java
public class UserDTO {
    private Long id;
    private String name;
    
    // ✅ 添加无参构造方法
    public UserDTO() {}
    
    public UserDTO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
```

#### 方案 2: 使用 Lombok（推荐）

```java
@Data
@NoArgsConstructor   // ✅ 生成无参构造
@AllArgsConstructor  // ✅ 生成全参构造
public class UserDTO {
    private Long id;
    private String name;
}
```

### 核心要点

> **在微服务开发中，所有需要进行 JSON 序列化/反序列化的 DTO 类，必须提供无参构造方法！**

### 需要无参构造的常见场景

- ✅ JSON 序列化/反序列化（Jackson、FastJson、Gson）
- ✅ ORM 框架（MyBatis、Hibernate、JPA）
- ✅ RPC 框架（Feign、Dubbo、gRPC）
- ✅ 对象拷贝工具（BeanUtils、MapStruct）
- ✅ 反射相关操作

## 相关文档

- 原始问题分析文档：`Feign调用失败问题排查与解决.md`（同目录）
- 完整演示项目：`/Users/huabin/workspace/playground/my-github/springboot/feign-noargs-demo/`

---

**创建日期**: 2026-03-29  
**作者**: huabin
