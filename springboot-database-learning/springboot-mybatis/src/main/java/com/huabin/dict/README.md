# 字典管理模块

## 需求说明

### 业务背景
系统需要实现字典管理功能，包含字典类型和字典项两个实体。字典类型支持树形结构（通过`dict_pid`字段实现父子关系），每个字典类型可以包含多个字典项。

### 数据库表设计

#### 1. sys_dict_type（字典类型表）
| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| dict_id | BIGINT | 字典ID | 主键，自增 |
| dict_name | VARCHAR(100) | 字典名称 | 非空 |
| dict_pid | BIGINT | 父字典ID | 0表示顶级字典 |

#### 2. sys_dict_item（字典项表）
| 字段名 | 类型 | 说明 | 备注 |
|--------|------|------|------|
| item_id | BIGINT | 字典项ID | 主键，自增 |
| dict_id | BIGINT | 字典ID | 外键，关联sys_dict_type.dict_id |
| item_name | VARCHAR(100) | 字典项名称 | 非空 |
| item_value | VARCHAR(100) | 字典项值 | 非空 |

### 核心需求
**传入一个父字典ID（dict_pid），返回该父字典下所有字典类型及其对应的字典项列表。**

#### 输入参数
- `dictPid`: Long类型，父字典ID

#### 返回结果
返回 `List<SysDictTypeVO>`，其中 `SysDictTypeVO` 包含：
- `dictId`: 字典ID
- `dictName`: 字典名称
- `items`: List<SysDictItem>，该字典类型下的所有字典项

### 技术实现

#### 关键技术点
1. **使用MyBatis的collection标签实现一对多映射**
   - 通过LEFT JOIN关联两张表
   - 使用resultMap的collection标签自动将字典项聚合到字典类型中

2. **单SQL实现**
   - 避免N+1查询问题
   - 提高查询性能

#### SQL实现原理
```sql
SELECT
    dt.dict_id,
    dt.dict_name,
    di.item_id,
    di.dict_id,
    di.item_name,
    di.item_value
FROM
    sys_dict_type dt
LEFT JOIN
    sys_dict_item di ON dt.dict_id = di.dict_id
WHERE
    dt.dict_pid = #{dictPid}
ORDER BY
    dt.dict_id, di.item_id
```

**说明：**
- 使用LEFT JOIN确保即使字典类型没有字典项也能查询出来
- MyBatis会根据resultMap配置自动将相同dict_id的记录聚合成一个SysDictTypeVO对象
- collection标签会将关联的字典项自动组装成List

## 项目结构

```
com.huabin.dict
├── controller
│   └── SysDictTypeController.java      # 控制器层
├── entity
│   ├── SysDictType.java                # 字典类型实体
│   └── SysDictItem.java                # 字典项实体
├── mapper
│   └── SysDictTypeMapper.java          # Mapper接口
├── service
│   ├── SysDictTypeService.java         # 服务接口
│   └── impl
│       └── SysDictTypeServiceImpl.java # 服务实现
├── vo
│   └── SysDictTypeVO.java              # 视图对象
└── README.md                            # 本文档
```

## API接口

### 查询字典类型及字典项
**接口地址：** `GET /dict/types`

**请求参数：**
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| dictPid | Long | 是 | 父字典ID |

**请求示例：**
```
GET http://localhost:8080/dict/types?dictPid=0
```

**响应示例：**
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
      }
    ]
  },
  {
    "dictId": 2,
    "dictName": "状态",
    "items": [
      {
        "itemId": 3,
        "dictId": 2,
        "itemName": "启用",
        "itemValue": "1"
      },
      {
        "itemId": 4,
        "dictId": 2,
        "itemName": "禁用",
        "itemValue": "0"
      }
    ]
  }
]
```

## 使用说明

### 1. 初始化数据库
执行 `init.sql` 脚本创建表并插入测试数据。

### 2. 配置MyBatis
确保 `application.yml` 中配置了正确的mapper扫描路径：
```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.huabin.dict.entity
```

### 3. 启动应用
启动Spring Boot应用后，访问接口进行测试。

### 4. 测试建议
- 测试dictPid=0的情况（查询顶级字典）
- 测试dictPid为具体值的情况（查询子字典）
- 测试字典类型没有字典项的情况（验证LEFT JOIN效果）

## 扩展功能建议

1. **分页查询**：添加分页参数支持大数据量查询
2. **缓存优化**：使用Redis缓存字典数据，减少数据库查询
3. **字典树查询**：递归查询整个字典树结构
4. **字典CRUD**：添加字典类型和字典项的增删改功能
5. **字典排序**：添加sort_order字段支持自定义排序

## 注意事项

1. **性能考虑**：如果字典项数量很大，建议添加分页或限制返回数量
2. **数据一致性**：删除字典类型时需要级联删除或处理关联的字典项
3. **参数校验**：生产环境建议添加参数校验和异常处理
4. **日志记录**：建议添加操作日志记录字典的变更
