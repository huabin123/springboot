# MyBatis Collection映射原理图解

## 问题：使用列别名会影响Collection吗？

**答案：不会！** 让我们通过图解来理解原理。

## 核心概念

### 1. 分组依据 vs 数据填充

```
┌─────────────────────────────────────────────────────────────┐
│                   MyBatis处理流程                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │  父对象的<id> │  用于   │  对象分组     │                 │
│  │  column      │ ────►  │  (聚合判断)   │                 │
│  └──────────────┘         └──────────────┘                 │
│                                                              │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │  子对象的属性 │  用于   │  属性赋值     │                 │
│  │  column      │ ────►  │  (数据填充)   │                 │
│  └──────────────┘         └──────────────┘                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 我们的配置分析

### ResultMap配置

```xml
<!-- 父对象 -->
<resultMap id="SysDictTypeVOResult" type="SysDictTypeVO">
    <id property="dictId" column="dict_id"/>  ← 👈 分组依据
    <result property="dictName" column="dict_name"/>
    <collection property="items" ofType="SysDictItem" resultMap="SysDictItemResult"/>
</resultMap>

<!-- 子对象 -->
<resultMap id="SysDictItemResult" type="SysDictItem">
    <id property="itemId" column="item_id"/>
    <result property="dictId" column="item_dict_id"/>  ← 👈 仅用于数据填充
    <result property="itemName" column="item_name"/>
    <result property="itemValue" column="item_value"/>
</resultMap>
```

### 关键点说明

| ResultMap | Column | 作用 | 是否影响分组 |
|-----------|--------|------|-------------|
| **SysDictTypeVOResult** | `dict_id` | **分组依据** | ✅ 是 |
| SysDictTypeVOResult | `dict_name` | 数据填充 | ❌ 否 |
| SysDictItemResult | `item_id` | 数据填充 | ❌ 否 |
| SysDictItemResult | `item_dict_id` | 数据填充 | ❌ 否 |
| SysDictItemResult | `item_name` | 数据填充 | ❌ 否 |
| SysDictItemResult | `item_value` | 数据填充 | ❌ 否 |

## 执行流程图解

### SQL查询结果
```
┌─────────┬───────────┬─────────┬──────────────┬───────────┬────────────┐
│ dict_id │ dict_name │ item_id │ item_dict_id │ item_name │ item_value │
├─────────┼───────────┼─────────┼──────────────┼───────────┼────────────┤
│    1    │   性别    │    1    │      1       │    男     │   male     │
│    1    │   性别    │    2    │      1       │    女     │   female   │
│    1    │   性别    │    3    │      1       │   未知    │  unknown   │
│    2    │   状态    │    4    │      2       │   启用    │     1      │
│    2    │   状态    │    5    │      2       │   禁用    │     0      │
└─────────┴───────────┴─────────┴──────────────┴───────────┴────────────┘
```

### MyBatis处理过程

```
第1行数据处理：
┌──────────────────────────────────────────────────────────┐
│ dict_id=1 (新值) → 创建新的 SysDictTypeVO                │
│   ├─ dictId = 1                                          │
│   ├─ dictName = "性别"                                   │
│   └─ items = []                                          │
│                                                           │
│ 创建 SysDictItem 并添加到 items                          │
│   ├─ itemId = 1                                          │
│   ├─ dictId = 1  ← 从 item_dict_id 列获取               │
│   ├─ itemName = "男"                                     │
│   └─ itemValue = "male"                                  │
└──────────────────────────────────────────────────────────┘

第2行数据处理：
┌──────────────────────────────────────────────────────────┐
│ dict_id=1 (已存在) → 找到已有的 SysDictTypeVO            │
│   不创建新对象，直接使用现有对象                          │
│                                                           │
│ 创建 SysDictItem 并添加到现有对象的 items                │
│   ├─ itemId = 2                                          │
│   ├─ dictId = 1  ← 从 item_dict_id 列获取               │
│   ├─ itemName = "女"                                     │
│   └─ itemValue = "female"                                │
└──────────────────────────────────────────────────────────┘

第3行数据处理：
┌──────────────────────────────────────────────────────────┐
│ dict_id=1 (已存在) → 找到已有的 SysDictTypeVO            │
│                                                           │
│ 创建 SysDictItem 并添加到现有对象的 items                │
│   ├─ itemId = 3                                          │
│   ├─ dictId = 1  ← 从 item_dict_id 列获取               │
│   ├─ itemName = "未知"                                   │
│   └─ itemValue = "unknown"                               │
└──────────────────────────────────────────────────────────┘

第4行数据处理：
┌──────────────────────────────────────────────────────────┐
│ dict_id=2 (新值) → 创建新的 SysDictTypeVO                │
│   ├─ dictId = 2                                          │
│   ├─ dictName = "状态"                                   │
│   └─ items = []                                          │
│                                                           │
│ 创建 SysDictItem 并添加到 items                          │
│   ├─ itemId = 4                                          │
│   ├─ dictId = 2  ← 从 item_dict_id 列获取               │
│   ├─ itemName = "启用"                                   │
│   └─ itemValue = "1"                                     │
└──────────────────────────────────────────────────────────┘

第5行数据处理：
┌──────────────────────────────────────────────────────────┐
│ dict_id=2 (已存在) → 找到已有的 SysDictTypeVO            │
│                                                           │
│ 创建 SysDictItem 并添加到现有对象的 items                │
│   ├─ itemId = 5                                          │
│   ├─ dictId = 2  ← 从 item_dict_id 列获取               │
│   ├─ itemName = "禁用"                                   │
│   └─ itemValue = "0"                                     │
└──────────────────────────────────────────────────────────┘
```

### 最终结果

```
List<SysDictTypeVO> result = [

  ┌─────────────────────────────────────────┐
  │ SysDictTypeVO #1                        │
  ├─────────────────────────────────────────┤
  │ dictId: 1                               │
  │ dictName: "性别"                        │
  │ items: [                                │
  │   SysDictItem(id=1, dictId=1, ...)     │
  │   SysDictItem(id=2, dictId=1, ...)     │
  │   SysDictItem(id=3, dictId=1, ...)     │
  │ ]                                       │
  └─────────────────────────────────────────┘

  ┌─────────────────────────────────────────┐
  │ SysDictTypeVO #2                        │
  ├─────────────────────────────────────────┤
  │ dictId: 2                               │
  │ dictName: "状态"                        │
  │ items: [                                │
  │   SysDictItem(id=4, dictId=2, ...)     │
  │   SysDictItem(id=5, dictId=2, ...)     │
  │ ]                                       │
  └─────────────────────────────────────────┘
]
```

## 为什么item_dict_id不影响分组？

### 分组逻辑伪代码

```java
// MyBatis内部处理逻辑（简化版）
Map<Object, SysDictTypeVO> resultMap = new LinkedHashMap<>();

for (Row row : resultSet) {
    // 1. 获取父对象的主键值（用于分组）
    Object parentKey = row.get("dict_id");  // ← 只看这个字段！
    
    // 2. 判断是否已存在
    SysDictTypeVO parent = resultMap.get(parentKey);
    if (parent == null) {
        // 不存在，创建新对象
        parent = new SysDictTypeVO();
        parent.setDictId(row.getLong("dict_id"));
        parent.setDictName(row.getString("dict_name"));
        parent.setItems(new ArrayList<>());
        resultMap.put(parentKey, parent);
    }
    
    // 3. 创建子对象（无论父对象是新建还是已存在）
    SysDictItem item = new SysDictItem();
    item.setItemId(row.getLong("item_id"));
    item.setDictId(row.getLong("item_dict_id"));  // ← 这里只是赋值，不参与分组判断
    item.setItemName(row.getString("item_name"));
    item.setItemValue(row.getString("item_value"));
    
    // 4. 添加到父对象的集合中
    parent.getItems().add(item);
}

return new ArrayList<>(resultMap.values());
```

### 关键点

```
┌────────────────────────────────────────────────────────────┐
│  分组判断只看父对象的 <id> 标签                             │
│                                                             │
│  <resultMap id="SysDictTypeVOResult" ...>                  │
│      <id property="dictId" column="dict_id"/>  ← 只看这个！ │
│      ...                                                    │
│  </resultMap>                                              │
│                                                             │
│  子对象的所有字段（包括 item_dict_id）                      │
│  都只用于填充属性值，不参与分组逻辑                          │
└────────────────────────────────────────────────────────────┘
```

## 对比：如果不使用别名会怎样？

### 不使用别名的SQL（有风险）

```sql
SELECT
    dt.dict_id,      -- 第1个 dict_id
    dt.dict_name,
    di.item_id,
    di.dict_id,      -- 第2个 dict_id，可能覆盖第1个！
    di.item_name,
    di.item_value
```

### 潜在问题

```
结果集中的列名：
┌─────────┬───────────┬─────────┬─────────┬───────────┬────────────┐
│ dict_id │ dict_name │ item_id │ dict_id │ item_name │ item_value │
│  (第1个) │           │         │  (第2个) │           │            │
└─────────┴───────────┴─────────┴─────────┴───────────┴────────────┘
                                    ↑
                                    可能覆盖第1个！

不同数据库驱动的行为可能不同：
- 有的保留第1个
- 有的保留第2个
- 有的抛出异常
```

### 使用别名的SQL（安全）

```sql
SELECT
    dt.dict_id AS dict_id,          -- 明确的列名
    dt.dict_name AS dict_name,
    di.item_id AS item_id,
    di.dict_id AS item_dict_id,     -- 使用别名，避免冲突
    di.item_name AS item_name,
    di.item_value AS item_value
```

### 结果

```
结果集中的列名：
┌─────────┬───────────┬─────────┬──────────────┬───────────┬────────────┐
│ dict_id │ dict_name │ item_id │ item_dict_id │ item_name │ item_value │
│         │           │         │              │           │            │
└─────────┴───────────┴─────────┴──────────────┴───────────┴────────────┘
                                      ↑
                                   独立的列名，不会冲突！
```

## 验证方法

运行单元测试：
```bash
mvn test -Dtest=SysDictTypeMapperTest#testCollectionMappingCorrectness
```

测试会验证：
1. ✅ Collection能正确聚合数据
2. ✅ 每个字典类型包含正确数量的字典项
3. ✅ 字典项的dictId正确填充（来自item_dict_id列）
4. ✅ 字典项与字典类型的关联关系正确

## 总结

### ✅ 使用列别名的优势

1. **避免字段冲突** - 不同表的同名字段不会相互覆盖
2. **明确映射关系** - 每个列都有清晰的用途
3. **不影响Collection** - 分组依据是父对象的`<id>`，与子对象的列名无关
4. **提高可维护性** - 代码意图更清晰
5. **跨数据库兼容** - 避免不同驱动的行为差异

### 📌 核心原则

```
┌──────────────────────────────────────────────────────┐
│  MyBatis Collection 分组原则                         │
├──────────────────────────────────────────────────────┤
│                                                       │
│  1. 分组依据：父对象 <id> 标签的 column               │
│  2. 数据填充：所有 <result> 标签的 column             │
│  3. 子对象的字段：只用于填充属性，不参与分组           │
│                                                       │
│  因此：item_dict_id 只是数据填充，不影响分组！        │
│                                                       │
└──────────────────────────────────────────────────────┘
```

**结论：使用列别名（如 item_dict_id）完全不会影响Collection的聚合功能！**
