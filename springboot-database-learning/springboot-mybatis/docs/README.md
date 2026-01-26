# 数据库初始化说明

## 数据库配置信息

- **数据库地址**: localhost:3306
- **数据库名称**: springboot_db
- **用户名**: root
- **字符编码**: UTF-8
- **时区**: Asia/Shanghai

## 初始化步骤

### 方式一：使用 MySQL 命令行

```bash
# 1. 登录 MySQL
mysql -u root -p

# 2. 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS springboot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

# 3. 使用数据库
USE springboot_db;

# 4. 执行初始化脚本
SOURCE /path/to/comprehensive_info_init.sql;
```

### 方式二：直接执行 SQL 文件

```bash
mysql -u root -p springboot_db < comprehensive_info_init.sql
```

### 方式三：使用 MySQL Workbench 或其他 GUI 工具

1. 连接到 MySQL 服务器
2. 打开 `comprehensive_info_init.sql` 文件
3. 执行脚本

## 表结构说明

### comprehensive_info 表

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| prod_code | VARCHAR(50) | 产品代码 | 主键 |
| prod_name | VARCHAR(200) | 产品名称 | 可为空 |
| data_date | DATETIME | 数据日期 | 可为空，有索引 |
| prod_cls | VARCHAR(50) | 产品分类 | 可为空，有索引 |

### 索引说明

- **PRIMARY KEY**: `prod_code` - 主键索引
- **idx_data_date**: `data_date` - 普通索引，用于按日期查询
- **idx_prod_cls**: `prod_cls` - 普通索引，用于按分类查询

## 测试数据

脚本中已包含 5 条测试数据，涵盖不同的产品类型和日期，可用于开发和测试。

## 注意事项

1. 执行脚本前请确保已创建 `springboot_db` 数据库
2. 脚本会先删除已存在的 `comprehensive_info` 表，请谨慎操作
3. 字符集使用 utf8mb4，支持完整的 Unicode 字符（包括 emoji）
4. 表引擎使用 InnoDB，支持事务和外键

---

## 常见问题排查

### 问题 1: Access denied for user 'root'@'192.168.65.1'

**错误信息：**
```
java.sql.SQLException: Access denied for user 'root'@'192.168.65.1' (using password: YES)
```

**原因分析：**
- IP `192.168.65.1` 通常是 Docker Desktop 的宿主机 IP
- MySQL 默认只允许 `root@localhost` 访问
- 需要为特定 IP 或网段授权

**解决方案：**

#### 方案一：授权特定 IP（推荐）

```bash
# 1. 登录 MySQL
mysql -u root -p

# 2. 执行授权脚本
SOURCE fix_mysql_access.sql;

# 或手动执行以下命令：
CREATE USER IF NOT EXISTS 'root'@'192.168.65.1' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'192.168.65.1';
FLUSH PRIVILEGES;
```

#### 方案二：授权 Docker 网段

```sql
CREATE USER IF NOT EXISTS 'root'@'192.168.65.%' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'192.168.65.%';
FLUSH PRIVILEGES;
```

#### 方案三：修改连接地址

如果 MySQL 在本地运行，可以修改 `application.yml` 中的连接地址：

```yaml
# 将 localhost 改为 127.0.0.1
url: jdbc:mysql://127.0.0.1:3306/springboot_db?...
```

#### 验证权限

```sql
-- 查看用户列表
SELECT user, host FROM mysql.user WHERE user = 'root';

-- 查看具体权限
SHOW GRANTS FOR 'root'@'192.168.65.1';
```

### 问题 2: 无法连接到 MySQL 服务器

**检查清单：**
1. MySQL 服务是否启动：`sudo systemctl status mysql` (Linux) 或检查 Docker 容器状态
2. 端口 3306 是否开放：`netstat -an | grep 3306`
3. 防火墙是否允许连接
4. 密码是否正确

### 问题 3: 数据库不存在

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS springboot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
```

---

## 快速启动指南

### 一键初始化（推荐）

```bash
# 1. 创建数据库并初始化表结构
mysql -u root -pDjeEZw2S2xS7vCq -e "CREATE DATABASE IF NOT EXISTS springboot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -pDjeEZw2S2xS7vCq springboot_db < comprehensive_info_init.sql

# 2. 如果遇到权限问题，执行权限修复
mysql -u root -pDjeEZw2S2xS7vCq < fix_mysql_access.sql

# 3. 验证
mysql -u root -pDjeEZw2S2xS7vCq springboot_db -e "SELECT * FROM comprehensive_info;"
```

### Docker 环境

如果 MySQL 运行在 Docker 中：

```bash
# 进入 MySQL 容器
docker exec -it <mysql_container_name> mysql -u root -p

# 然后执行授权命令
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'%';
FLUSH PRIVILEGES;
```
