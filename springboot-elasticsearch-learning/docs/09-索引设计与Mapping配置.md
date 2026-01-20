# 09-索引设计与Mapping配置

## 本章概述

本章详细讲解Elasticsearch的索引设计和Mapping配置，重点解决以下问题：
- **问题1**：如何设计合理的索引结构？
- **问题2**：Mapping字段类型有哪些？如何选择？
- **问题3**：动态Mapping vs 静态Mapping，如何选择？
- **问题4**：如何使用索引模板（Index Template）？
- **问题5**：如何实现索引生命周期管理（ILM）？

---

## 问题1：如何设计合理的索引结构？

### 1.1 索引设计原则

#### 原则1：一个索引对应一类数据

```
✅ 推荐：
- products（商品索引）
- orders（订单索引）
- users（用户索引）

❌ 不推荐：
- data（所有数据混在一起）

原因：
- 不同类型的数据，字段结构不同
- 混在一起会导致Mapping混乱
- 查询性能下降
```

#### 原则2：合理设置分片数

```
分片数计算：
- 预估数据量 / 单分片最大容量（20-50GB）
- 考虑查询并发度
- 考虑节点数量

示例：
数据量：500GB
单分片：50GB
分片数：500GB / 50GB = 10个主分片

配置：
PUT /products
{
  "settings": {
    "number_of_shards": 10,
    "number_of_replicas": 1
  }
}
```

#### 原则3：字段不要过多

```
问题：字段过多会导致什么问题？

1. Mapping膨胀
   - Mapping占用内存
   - 集群状态变大
   - 性能下降

2. 文档解析开销大
   - 每个字段都需要解析
   - CPU消耗增加

建议：
- 单个索引字段数 < 1000
- 如果字段过多，考虑拆分索引
- 使用nested或object类型合并相关字段
```

#### 原则4：避免稀疏字段

```
问题：什么是稀疏字段？

示例：
{
  "name": "iPhone 14",
  "price": 5999,
  "color": "黑色",
  "screen_size": "6.1英寸",  // 只有手机有
  "cpu": "A16",              // 只有手机有
  "battery": "3279mAh"       // 只有手机有
}

{
  "name": "MacBook Pro",
  "price": 14999,
  "color": "银色",
  "screen_size": "14英寸",   // 电脑也有
  "cpu": "M2 Pro",           // 电脑也有
  "memory": "16GB"           // 只有电脑有
}

问题：
- battery字段在电脑文档中为null
- memory字段在手机文档中为null
- 浪费存储空间

解决方案：
1. 拆分索引（推荐）
   - phones索引：存储手机
   - computers索引：存储电脑

2. 使用object类型
   {
     "name": "iPhone 14",
     "specs": {
       "battery": "3279mAh"
     }
   }
```

### 1.2 索引命名规范

```
推荐命名规范：

1. 小写字母
   ✅ products
   ❌ Products

2. 使用连字符分隔
   ✅ product-reviews
   ❌ product_reviews（下划线不推荐）

3. 包含版本号（如果需要）
   ✅ products-v1
   ✅ products-v2

4. 时序数据包含时间
   ✅ logs-2024-01-20
   ✅ metrics-2024-01

5. 使用别名
   PUT /products-v1
   POST /_aliases
   {
     "actions": [
       {
         "add": {
           "index": "products-v1",
           "alias": "products"
         }
       }
     ]
   }
   
   优势：
   - 应用程序使用别名访问
   - 索引升级时，切换别名即可
   - 不需要修改代码
```

### 1.3 索引设计案例

#### 案例1：电商商品索引

```json
PUT /products
{
  "settings": {
    "number_of_shards": 5,
    "number_of_replicas": 1,
    "analysis": {
      "analyzer": {
        "ik_max_word_analyzer": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": {
        "type": "keyword"
      },
      "name": {
        "type": "text",
        "analyzer": "ik_max_word_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      },
      "category": {
        "type": "keyword"
      },
      "price": {
        "type": "double"
      },
      "stock": {
        "type": "integer"
      },
      "description": {
        "type": "text",
        "analyzer": "ik_max_word_analyzer"
      },
      "tags": {
        "type": "keyword"
      },
      "brand": {
        "type": "keyword"
      },
      "created_at": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      },
      "updated_at": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      }
    }
  }
}
```

#### 案例2：日志索引

```json
PUT /logs-2024-01-20
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "index.lifecycle.name": "logs-policy",
    "index.lifecycle.rollover_alias": "logs"
  },
  "mappings": {
    "properties": {
      "timestamp": {
        "type": "date"
      },
      "level": {
        "type": "keyword"
      },
      "logger": {
        "type": "keyword"
      },
      "message": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "thread": {
        "type": "keyword"
      },
      "exception": {
        "type": "text"
      },
      "host": {
        "type": "keyword"
      },
      "application": {
        "type": "keyword"
      }
    }
  }
}
```

---

## 问题2：Mapping字段类型有哪些？如何选择？

### 2.1 核心数据类型

#### 1. 字符串类型

**text类型**：
```json
{
  "name": {
    "type": "text",
    "analyzer": "ik_max_word"
  }
}

特点：
- 会被分词
- 支持全文搜索
- 不支持聚合和排序（除非开启fielddata）
- 适用场景：商品名称、文章内容、描述等

示例：
"iPhone 14 Pro Max" → ["iPhone", "14", "Pro", "Max"]
```

**keyword类型**：
```json
{
  "category": {
    "type": "keyword"
  }
}

特点：
- 不分词，精确匹配
- 支持聚合和排序
- 支持前缀搜索
- 适用场景：分类、标签、状态、ID等

示例：
"iPhone 14 Pro Max" → "iPhone 14 Pro Max"（整体作为一个词）
```

**text + keyword组合**（推荐）：
```json
{
  "name": {
    "type": "text",
    "analyzer": "ik_max_word",
    "fields": {
      "keyword": {
        "type": "keyword",
        "ignore_above": 256
      }
    }
  }
}

优势：
- name：用于全文搜索
- name.keyword：用于聚合、排序、精确匹配

使用：
// 全文搜索
GET /products/_search
{
  "query": {
    "match": { "name": "iPhone" }
  }
}

// 精确匹配
GET /products/_search
{
  "query": {
    "term": { "name.keyword": "iPhone 14 Pro Max" }
  }
}

// 聚合
GET /products/_search
{
  "aggs": {
    "top_products": {
      "terms": { "field": "name.keyword" }
    }
  }
}
```

#### 2. 数值类型

```json
{
  "price": {
    "type": "double"      // 双精度浮点数
  },
  "stock": {
    "type": "integer"     // 整数
  },
  "sales": {
    "type": "long"        // 长整数
  },
  "rating": {
    "type": "float"       // 单精度浮点数
  },
  "discount": {
    "type": "half_float"  // 半精度浮点数（节省空间）
  }
}

类型选择：
- byte：-128 ~ 127
- short：-32768 ~ 32767
- integer：-2^31 ~ 2^31-1
- long：-2^63 ~ 2^63-1
- float：单精度浮点数
- double：双精度浮点数
- half_float：半精度浮点数（节省50%空间）
- scaled_float：缩放浮点数（例如价格，精确到分）

推荐：
- 价格：scaled_float（精确到分）
- 库存：integer
- 销量：long
- 评分：float
```

#### 3. 日期类型

```json
{
  "created_at": {
    "type": "date",
    "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
  }
}

支持的格式：
1. 字符串：
   - "2024-01-20"
   - "2024-01-20 10:30:00"
   
2. 时间戳：
   - 1705723800000（毫秒）
   - 1705723800（秒，需要指定epoch_second）

3. 自定义格式：
   "format": "dd/MM/yyyy"

查询示例：
GET /products/_search
{
  "query": {
    "range": {
      "created_at": {
        "gte": "2024-01-01",
        "lte": "2024-01-31"
      }
    }
  }
}
```

#### 4. 布尔类型

```json
{
  "is_active": {
    "type": "boolean"
  }
}

接受的值：
- true, false
- "true", "false"
- "" (false)
- 任何非空字符串 (true)

查询示例：
GET /products/_search
{
  "query": {
    "term": { "is_active": true }
  }
}
```

### 2.2 复杂数据类型

#### 1. Object类型

```json
{
  "user": {
    "type": "object",
    "properties": {
      "name": { "type": "text" },
      "age": { "type": "integer" },
      "email": { "type": "keyword" }
    }
  }
}

文档示例：
{
  "user": {
    "name": "张三",
    "age": 25,
    "email": "zhangsan@example.com"
  }
}

查询：
GET /users/_search
{
  "query": {
    "match": { "user.name": "张三" }
  }
}

注意：
- Object类型会被扁平化存储
- 不保留对象之间的关联关系
```

#### 2. Nested类型

```json
// 问题：Object类型的局限性

// Mapping
{
  "comments": {
    "type": "object",
    "properties": {
      "user": { "type": "keyword" },
      "content": { "type": "text" }
    }
  }
}

// 文档
{
  "title": "ES教程",
  "comments": [
    { "user": "张三", "content": "写得好" },
    { "user": "李四", "content": "很实用" }
  ]
}

// 实际存储（扁平化）
{
  "title": "ES教程",
  "comments.user": ["张三", "李四"],
  "comments.content": ["写得好", "很实用"]
}

// 问题查询
GET /articles/_search
{
  "query": {
    "bool": {
      "must": [
        { "term": { "comments.user": "张三" } },
        { "match": { "comments.content": "很实用" } }
      ]
    }
  }
}

// 结果：会匹配到文档（错误！）
// 因为扁平化后，"张三"和"很实用"都存在，但不是同一条评论
```

**解决方案：使用Nested类型**：
```json
// Mapping
{
  "comments": {
    "type": "nested",
    "properties": {
      "user": { "type": "keyword" },
      "content": { "type": "text" }
    }
  }
}

// 查询（使用nested query）
GET /articles/_search
{
  "query": {
    "nested": {
      "path": "comments",
      "query": {
        "bool": {
          "must": [
            { "term": { "comments.user": "张三" } },
            { "match": { "comments.content": "很实用" } }
          ]
        }
      }
    }
  }
}

// 结果：不会匹配到文档（正确！）
// 因为nested类型保留了对象之间的关联关系

优势：
- 保留对象之间的关联关系
- 支持独立查询每个嵌套对象

劣势：
- 查询性能略低
- 索引和查询更复杂
```

#### 3. Array类型

```json
// ES没有专门的数组类型，任何字段都可以存储数组

{
  "tags": {
    "type": "keyword"
  }
}

// 文档
{
  "name": "iPhone 14",
  "tags": ["手机", "苹果", "5G"]
}

// 查询
GET /products/_search
{
  "query": {
    "term": { "tags": "手机" }
  }
}

注意：
- 数组中的所有元素必须是同一类型
- 不能混合类型：["手机", 123] ❌
```

### 2.3 特殊数据类型

#### 1. Geo类型（地理位置）

**geo_point**：
```json
{
  "location": {
    "type": "geo_point"
  }
}

// 文档（多种格式）
// 格式1：对象
{ "location": { "lat": 39.9042, "lon": 116.4074 } }

// 格式2：字符串
{ "location": "39.9042,116.4074" }

// 格式3：数组
{ "location": [116.4074, 39.9042] }  // 注意：[lon, lat]

// 查询：查找附近的地点
GET /places/_search
{
  "query": {
    "geo_distance": {
      "distance": "5km",
      "location": {
        "lat": 39.9042,
        "lon": 116.4074
      }
    }
  }
}
```

**geo_shape**：
```json
{
  "area": {
    "type": "geo_shape"
  }
}

// 文档（多边形）
{
  "area": {
    "type": "polygon",
    "coordinates": [
      [
        [116.3, 39.9],
        [116.5, 39.9],
        [116.5, 40.1],
        [116.3, 40.1],
        [116.3, 39.9]
      ]
    ]
  }
}

// 查询：查找在某个区域内的地点
GET /places/_search
{
  "query": {
    "geo_shape": {
      "area": {
        "shape": {
          "type": "point",
          "coordinates": [116.4, 40.0]
        },
        "relation": "within"
      }
    }
  }
}
```

#### 2. IP类型

```json
{
  "client_ip": {
    "type": "ip"
  }
}

// 文档
{ "client_ip": "192.168.1.1" }

// 查询：IP范围查询
GET /logs/_search
{
  "query": {
    "range": {
      "client_ip": {
        "gte": "192.168.1.0",
        "lte": "192.168.1.255"
      }
    }
  }
}

// 查询：CIDR查询
GET /logs/_search
{
  "query": {
    "term": { "client_ip": "192.168.1.0/24" }
  }
}
```

### 2.4 字段类型选择指南

```
场景1：商品名称
- 类型：text + keyword
- 原因：需要全文搜索，也需要聚合统计

场景2：商品分类
- 类型：keyword
- 原因：精确匹配，需要聚合

场景3：商品价格
- 类型：scaled_float（scaling_factor=100）
- 原因：精确到分，节省空间

场景4：商品库存
- 类型：integer
- 原因：整数，范围足够

场景5：商品描述
- 类型：text
- 原因：只需要全文搜索，不需要聚合

场景6：商品标签
- 类型：keyword（数组）
- 原因：精确匹配，需要聚合

场景7：创建时间
- 类型：date
- 原因：需要范围查询、聚合

场景8：是否上架
- 类型：boolean
- 原因：只有两个值

场景9：商品评论
- 类型：nested
- 原因：需要保留评论之间的关联关系

场景10：商店位置
- 类型：geo_point
- 原因：需要地理位置查询
```

---

## 问题3：动态Mapping vs 静态Mapping，如何选择？

### 3.1 动态Mapping

#### 什么是动态Mapping？

```
定义：
- ES自动根据文档内容推断字段类型
- 无需手动定义Mapping

示例：
// 直接写入文档，无需创建索引
POST /products/_doc/1
{
  "name": "iPhone 14",
  "price": 5999,
  "stock": 100,
  "created_at": "2024-01-20"
}

// ES自动创建Mapping
GET /products/_mapping

// 响应
{
  "products": {
    "mappings": {
      "properties": {
        "name": { "type": "text" },
        "price": { "type": "long" },
        "stock": { "type": "long" },
        "created_at": { "type": "date" }
      }
    }
  }
}
```

#### 动态类型推断规则

```
JSON类型 → ES类型

字符串：
- 日期格式（yyyy-MM-dd）→ date
- 数字字符串（"123"）→ text + keyword
- 其他字符串 → text + keyword

数字：
- 整数 → long
- 浮点数 → float

布尔值：
- true/false → boolean

对象：
- {} → object

数组：
- [] → 根据第一个元素推断
```

#### 动态Mapping配置

```json
PUT /products
{
  "mappings": {
    "dynamic": "true"  // 默认值
  }
}

dynamic选项：
1. true（默认）：
   - 自动添加新字段
   - 推断字段类型

2. false：
   - 不自动添加新字段
   - 新字段可以写入，但不会被索引
   - 不能搜索新字段

3. strict：
   - 不允许添加新字段
   - 写入包含新字段的文档会报错

示例：
PUT /products
{
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "name": { "type": "text" },
      "price": { "type": "double" }
    }
  }
}

// 写入包含新字段的文档
POST /products/_doc/1
{
  "name": "iPhone 14",
  "price": 5999,
  "stock": 100  // 新字段
}

// 报错：strict_dynamic_mapping_exception
```

### 3.2 静态Mapping

#### 什么是静态Mapping？

```
定义：
- 手动定义字段类型
- 创建索引时指定Mapping

示例：
PUT /products
{
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "ik_max_word",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "price": {
        "type": "scaled_float",
        "scaling_factor": 100
      },
      "stock": {
        "type": "integer"
      },
      "created_at": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss"
      }
    }
  }
}
```

#### 静态Mapping的优势

```
1. 精确控制字段类型
   - 动态：price → long
   - 静态：price → scaled_float（更合适）

2. 指定分词器
   - 动态：使用默认分词器
   - 静态：使用IK分词器

3. 优化存储和性能
   - 动态：所有数字都是long
   - 静态：根据实际情况选择integer/long

4. 避免类型冲突
   - 动态：第一个文档决定类型
   - 静态：提前定义，避免冲突
```

### 3.3 动态模板（Dynamic Templates）

```json
// 结合动态和静态的优势

PUT /products
{
  "mappings": {
    "dynamic_templates": [
      {
        "strings_as_keywords": {
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword"
          }
        }
      },
      {
        "longs_as_integers": {
          "match_mapping_type": "long",
          "mapping": {
            "type": "integer"
          }
        }
      },
      {
        "dates": {
          "match": "*_at",
          "mapping": {
            "type": "date",
            "format": "yyyy-MM-dd HH:mm:ss"
          }
        }
      }
    ]
  }
}

规则说明：
1. 所有字符串字段 → keyword
2. 所有long字段 → integer
3. 以_at结尾的字段 → date

写入文档：
POST /products/_doc/1
{
  "name": "iPhone 14",      // → keyword
  "price": 5999,            // → integer
  "created_at": "2024-01-20 10:00:00"  // → date
}
```

### 3.4 选择建议

```
使用动态Mapping的场景：
✅ 日志、监控数据（字段不固定）
✅ 快速原型开发
✅ 数据探索阶段

使用静态Mapping的场景：
✅ 生产环境（推荐）
✅ 字段结构固定
✅ 需要精确控制字段类型
✅ 需要优化性能

使用动态模板的场景：
✅ 字段较多，但有规律
✅ 需要灵活性，但也要控制
✅ 日志数据（配合索引模板）

最佳实践：
1. 生产环境使用静态Mapping
2. 设置dynamic=strict，防止意外添加字段
3. 使用动态模板处理规律性字段
4. 定期review Mapping，优化字段类型
```

---

## 问题4：如何使用索引模板（Index Template）？

### 4.1 什么是索引模板？

```
定义：
- 预定义索引的settings和mappings
- 创建新索引时，自动应用模板
- 适用于时序数据（日志、监控）

使用场景：
- 日志索引：logs-2024-01-20, logs-2024-01-21, ...
- 监控索引：metrics-2024-01, metrics-2024-02, ...
- 每天/每月自动创建新索引
- 所有索引使用相同的配置
```

### 4.2 创建索引模板

#### 基本示例

```json
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "priority": 100,
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "index.lifecycle.name": "logs_policy"
    },
    "mappings": {
      "properties": {
        "timestamp": {
          "type": "date"
        },
        "level": {
          "type": "keyword"
        },
        "message": {
          "type": "text"
        },
        "host": {
          "type": "keyword"
        }
      }
    }
  }
}

说明：
- index_patterns：匹配的索引名称模式
- priority：优先级（数字越大，优先级越高）
- template：索引的settings和mappings
```

#### 使用模板

```bash
# 创建索引（自动应用模板）
PUT /logs-2024-01-20

# 查看索引配置
GET /logs-2024-01-20

# 写入数据
POST /logs-2024-01-20/_doc
{
  "timestamp": "2024-01-20T10:00:00",
  "level": "INFO",
  "message": "Application started",
  "host": "server-1"
}
```

### 4.3 组件模板（Component Templates）

```json
// ES 7.8+支持组件模板，可以复用配置

// 1. 创建settings组件模板
PUT /_component_template/logs_settings
{
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1
    }
  }
}

// 2. 创建mappings组件模板
PUT /_component_template/logs_mappings
{
  "template": {
    "mappings": {
      "properties": {
        "timestamp": { "type": "date" },
        "level": { "type": "keyword" },
        "message": { "type": "text" }
      }
    }
  }
}

// 3. 创建索引模板，引用组件模板
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "composed_of": ["logs_settings", "logs_mappings"],
  "priority": 100
}

优势：
- 配置复用
- 便于维护
- 模块化管理
```

### 4.4 索引模板最佳实践

```json
// 完整的日志索引模板

PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "priority": 100,
  "template": {
    "settings": {
      // 分片配置
      "number_of_shards": 3,
      "number_of_replicas": 1,
      
      // 生命周期管理
      "index.lifecycle.name": "logs_policy",
      "index.lifecycle.rollover_alias": "logs",
      
      // 刷新间隔
      "index.refresh_interval": "5s",
      
      // 编解码器
      "index.codec": "best_compression",
      
      // 分词器
      "analysis": {
        "analyzer": {
          "default": {
            "type": "standard"
          }
        }
      }
    },
    "mappings": {
      // 动态模板
      "dynamic_templates": [
        {
          "strings_as_keywords": {
            "match_mapping_type": "string",
            "mapping": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        }
      ],
      // 字段定义
      "properties": {
        "@timestamp": {
          "type": "date"
        },
        "level": {
          "type": "keyword"
        },
        "logger": {
          "type": "keyword"
        },
        "message": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "thread": {
          "type": "keyword"
        },
        "exception": {
          "type": "text"
        }
      }
    }
  }
}
```

---

## 问题5：如何实现索引生命周期管理（ILM）？

### 5.1 什么是ILM？

```
定义：
- Index Lifecycle Management（索引生命周期管理）
- 自动管理索引的整个生命周期
- 从创建到删除的全过程

生命周期阶段：
1. Hot（热）：索引正在被写入和查询
2. Warm（温）：索引不再写入，但仍被查询
3. Cold（冷）：索引很少被查询
4. Frozen（冻结）：索引几乎不被查询
5. Delete（删除）：索引被删除
```

### 5.2 创建ILM策略

```json
PUT /_ilm/policy/logs_policy
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "1d",
            "max_docs": 10000000
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "forcemerge": {
            "max_num_segments": 1
          },
          "shrink": {
            "number_of_shards": 1
          },
          "set_priority": {
            "priority": 50
          }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": {
          "set_priority": {
            "priority": 0
          },
          "freeze": {}
        }
      },
      "delete": {
        "min_age": "90d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}

策略说明：

Hot阶段：
- rollover：满足以下任一条件时滚动到新索引
  - 索引大小 > 50GB
  - 索引年龄 > 1天
  - 文档数 > 1000万
- set_priority：设置索引优先级为100

Warm阶段（7天后）：
- forcemerge：合并segment到1个
- shrink：缩减分片数到1个
- set_priority：降低优先级到50

Cold阶段（30天后）：
- freeze：冻结索引（减少内存占用）
- set_priority：优先级降到0

Delete阶段（90天后）：
- delete：删除索引
```

### 5.3 应用ILM策略

#### 方式1：通过索引模板

```json
PUT /_index_template/logs_template
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "index.lifecycle.name": "logs_policy",
      "index.lifecycle.rollover_alias": "logs"
    }
  }
}

// 创建初始索引
PUT /logs-000001
{
  "aliases": {
    "logs": {
      "is_write_index": true
    }
  }
}

// 写入数据（使用别名）
POST /logs/_doc
{
  "@timestamp": "2024-01-20T10:00:00",
  "message": "Application started"
}

// 当满足rollover条件时，自动创建logs-000002
```

#### 方式2：手动应用

```json
PUT /logs-2024-01-20/_settings
{
  "index.lifecycle.name": "logs_policy"
}
```

### 5.4 监控ILM

```bash
# 查看ILM策略
GET /_ilm/policy/logs_policy

# 查看索引的ILM状态
GET /logs-*/_ilm/explain

# 响应示例
{
  "indices": {
    "logs-000001": {
      "index": "logs-000001",
      "managed": true,
      "policy": "logs_policy",
      "phase": "hot",
      "action": "rollover",
      "step": "check-rollover-ready",
      "age": "1d"
    }
  }
}

# 手动触发rollover
POST /logs/_rollover

# 重试失败的ILM步骤
POST /logs-000001/_ilm/retry
```

### 5.5 ILM最佳实践

```
1. 合理设置rollover条件
   - 单索引不要太大（< 50GB）
   - 不要太小（> 10GB）
   - 考虑查询性能

2. 使用forcemerge优化
   - Warm阶段合并segment
   - 减少segment数量
   - 提升查询性能

3. 使用shrink节省资源
   - 减少分片数
   - 降低资源消耗

4. 设置合理的删除时间
   - 根据业务需求
   - 考虑存储成本
   - 考虑合规要求

5. 监控ILM执行
   - 定期检查ILM状态
   - 处理失败的步骤
   - 调整策略参数
```

---

## 本章总结

### 核心要点

1. **索引设计原则**
   - 一个索引对应一类数据
   - 合理设置分片数
   - 字段不要过多（< 1000）
   - 避免稀疏字段

2. **Mapping字段类型**
   - text：全文搜索
   - keyword：精确匹配、聚合
   - 数值类型：根据范围选择
   - date：时间范围查询
   - nested：保留对象关联

3. **动态vs静态Mapping**
   - 动态：快速开发、日志数据
   - 静态：生产环境、精确控制
   - 动态模板：兼顾灵活性和控制

4. **索引模板**
   - 预定义索引配置
   - 适用于时序数据
   - 组件模板复用配置

5. **ILM生命周期管理**
   - Hot → Warm → Cold → Delete
   - 自动rollover
   - 优化存储和性能

### 最佳实践

```
✅ Mapping设计：
1. 生产环境使用静态Mapping
2. text字段配合keyword子字段
3. 数值类型根据实际范围选择
4. 使用nested保留对象关联

✅ 索引设计：
1. 单索引字段数 < 1000
2. 单分片大小 20-50GB
3. 使用索引别名
4. 时序数据使用索引模板

✅ ILM配置：
1. 合理设置rollover条件
2. Warm阶段forcemerge
3. Cold阶段freeze
4. 定期删除过期数据

✅ 性能优化：
1. 使用best_compression编解码器
2. 调整refresh_interval
3. 禁用不需要的功能
4. 使用routing优化查询
```

### 常见问题

```
Q1：Mapping创建后可以修改吗？
A1：不能修改已有字段类型，但可以添加新字段

Q2：如何修改Mapping？
A2：创建新索引，使用reindex迁移数据

Q3：text和keyword如何选择？
A3：需要全文搜索用text，精确匹配/聚合用keyword

Q4：什么时候使用nested类型？
A4：需要保留对象之间的关联关系时

Q5：ILM的rollover如何触发？
A5：满足max_size、max_age、max_docs任一条件即触发
```

---

## 下一章预告

**10-查询DSL实战指南**

下一章将详细讲解：
- 全文查询（Match、Match Phrase、Multi Match）
- 精确查询（Term、Range、Exists）
- 复合查询（Bool Query）
- 聚合查询（Metrics、Bucket、Pipeline）
- 高亮、排序、分页
- 实战案例

敬请期待！
