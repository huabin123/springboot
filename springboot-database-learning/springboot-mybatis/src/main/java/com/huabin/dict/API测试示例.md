# 字典管理模块 - API测试示例

## 接口信息

**基础URL**: `http://localhost:8080`

**接口路径**: `/dict/types`

**请求方法**: `GET`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| dictPid | Long | 是 | 父字典ID |

## cURL测试命令

### 1. 查询顶级字典（dictPid = 0）
```bash
curl -X GET "http://localhost:8080/dict/types?dictPid=0"
```

**预期返回**:
```json
[
  {
    "dictId": 1,
    "dictName": "性别",
    "items": [
      {
        "itemId": 1,
        "dictId": 1,
        "itemName": "男",
        "itemValue": "male"
      },
      {
        "itemId": 2,
        "dictId": 1,
        "itemName": "女",
        "itemValue": "female"
      },
      {
        "itemId": 3,
        "dictId": 1,
        "itemName": "未知",
        "itemValue": "unknown"
      }
    ]
  },
  {
    "dictId": 2,
    "dictName": "状态",
    "items": [
      {
        "itemId": 4,
        "dictId": 2,
        "itemName": "启用",
        "itemValue": "1"
      },
      {
        "itemId": 5,
        "dictId": 2,
        "itemName": "禁用",
        "itemValue": "0"
      }
    ]
  },
  {
    "dictId": 3,
    "dictName": "用户类型",
    "items": [
      {
        "itemId": 6,
        "dictId": 3,
        "itemName": "普通用户",
        "itemValue": "normal"
      },
      {
        "itemId": 7,
        "dictId": 3,
        "itemName": "VIP用户",
        "itemValue": "vip"
      },
      {
        "itemId": 8,
        "dictId": 3,
        "itemName": "管理员",
        "itemValue": "admin"
      }
    ]
  },
  {
    "dictId": 4,
    "dictName": "订单状态",
    "items": [
      {
        "itemId": 9,
        "dictId": 4,
        "itemName": "待支付",
        "itemValue": "pending"
      },
      {
        "itemId": 10,
        "dictId": 4,
        "itemName": "已支付",
        "itemValue": "paid"
      },
      {
        "itemId": 11,
        "dictId": 4,
        "itemName": "已发货",
        "itemValue": "shipped"
      },
      {
        "itemId": 12,
        "dictId": 4,
        "itemName": "已完成",
        "itemValue": "completed"
      },
      {
        "itemId": 13,
        "dictId": 4,
        "itemName": "已取消",
        "itemValue": "cancelled"
      }
    ]
  }
]
```

### 2. 查询子字典（dictPid = 1，性别的子分类）
```bash
curl -X GET "http://localhost:8080/dict/types?dictPid=1"
```

**预期返回**:
```json
[
  {
    "dictId": 5,
    "dictName": "性别扩展",
    "items": [
      {
        "itemId": 14,
        "dictId": 5,
        "itemName": "保密",
        "itemValue": "secret"
      },
      {
        "itemId": 15,
        "dictId": 5,
        "itemName": "其他",
        "itemValue": "other"
      }
    ]
  }
]
```

### 3. 查询不存在的父字典（dictPid = 999）
```bash
curl -X GET "http://localhost:8080/dict/types?dictPid=999"
```

**预期返回**:
```json
[]
```

## Postman测试配置

### 方式一：导入cURL命令
1. 打开Postman
2. 点击 "Import" 按钮
3. 选择 "Raw text" 标签
4. 粘贴上述cURL命令
5. 点击 "Continue" 和 "Import"

### 方式二：手动创建请求

#### 请求1：查询顶级字典
- **Method**: GET
- **URL**: `http://localhost:8080/dict/types`
- **Params**:
  - Key: `dictPid`
  - Value: `0`

#### 请求2：查询子字典
- **Method**: GET
- **URL**: `http://localhost:8080/dict/types`
- **Params**:
  - Key: `dictPid`
  - Value: `1`

## 浏览器测试

直接在浏览器地址栏输入以下URL：

```
http://localhost:8080/dict/types?dictPid=0
http://localhost:8080/dict/types?dictPid=1
```

浏览器会自动格式化JSON响应（需要安装JSON格式化插件）。

## 测试检查点

### 功能测试
- [ ] 查询顶级字典返回4条记录
- [ ] 每个字典类型都包含对应的字典项列表
- [ ] 查询子字典返回1条记录（性别扩展）
- [ ] 查询不存在的父字典返回空数组

### 性能测试
- [ ] 查看控制台日志，确认只执行了一条SQL
- [ ] SQL使用了LEFT JOIN关联两张表
- [ ] 响应时间在可接受范围内（< 100ms）

### 数据正确性
- [ ] 字典ID正确映射
- [ ] 字典名称正确显示
- [ ] 字典项列表完整
- [ ] 字典项的itemName和itemValue正确

## 常见问题排查

### 1. 404 Not Found
**原因**: 应用未启动或端口配置错误
**解决**: 
- 检查Spring Boot应用是否启动
- 确认端口号是否为8080
- 检查context-path配置

### 2. 500 Internal Server Error
**原因**: 数据库连接失败或SQL执行错误
**解决**:
- 检查数据库连接配置
- 确认已执行init.sql初始化脚本
- 查看应用日志中的错误信息

### 3. 返回空数组
**原因**: 数据库中没有对应的数据
**解决**:
- 确认已执行init.sql脚本
- 检查传入的dictPid是否正确
- 在数据库中手动查询验证数据

### 4. 字典项列表为null
**原因**: MyBatis映射配置错误
**解决**:
- 检查SysDictTypeMapper.xml中的resultMap配置
- 确认collection标签配置正确
- 查看SQL执行日志

## 日志查看

启动应用后，控制台会输出SQL执行日志：

```
==>  Preparing: SELECT dt.dict_id, dt.dict_name, di.item_id, di.dict_id, di.item_name, di.item_value FROM sys_dict_type dt LEFT JOIN sys_dict_item di ON dt.dict_id = di.dict_id WHERE dt.dict_pid = ? ORDER BY dt.dict_id, di.item_id
==> Parameters: 0(Long)
<==    Columns: dict_id, dict_name, item_id, dict_id, item_name, item_value
<==        Row: 1, 性别, 1, 1, 男, male
<==        Row: 1, 性别, 2, 1, 女, female
...
<==      Total: 15
```

通过日志可以确认：
- SQL语句正确生成
- 参数正确传递
- 返回的行数符合预期
