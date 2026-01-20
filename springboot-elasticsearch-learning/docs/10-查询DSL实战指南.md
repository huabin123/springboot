# 10-查询DSL实战指南

## 本章概述

本章详细讲解Elasticsearch查询DSL的实战应用，重点解决以下问题：
- **问题1**：全文查询如何使用？（Match、Match Phrase、Multi Match）
- **问题2**：精确查询如何使用？（Term、Range、Exists）
- **问题3**：复合查询如何组合？（Bool Query）
- **问题4**：聚合查询如何实现？（Metrics、Bucket、Pipeline）
- **问题5**：如何实现高亮、排序、分页？

---

## 问题1：全文查询如何使用？

### 1.1 Match Query（匹配查询）

#### 基本用法

```json
// 查询商品名称包含"手机"的文档
GET /products/_search
{
  "query": {
    "match": {
      "name": "手机"
    }
  }
}

工作原理：
1. 对查询词"手机"进行分词
2. 在倒排索引中查找包含这些词的文档
3. 计算相关性评分
4. 返回匹配的文档
```

#### 多词查询

```json
// 查询包含"苹果"或"手机"的文档
GET /products/_search
{
  "query": {
    "match": {
      "name": "苹果手机"
    }
  }
}

分词结果：["苹果", "手机"]
匹配逻辑：默认OR，包含任一词即匹配

// 修改为AND逻辑（必须同时包含）
GET /products/_search
{
  "query": {
    "match": {
      "name": {
        "query": "苹果手机",
        "operator": "and"
      }
    }
  }
}
```

#### 最小匹配度

```json
// 至少匹配2个词
GET /products/_search
{
  "query": {
    "match": {
      "name": {
        "query": "苹果 华为 小米 手机",
        "minimum_should_match": 2
      }
    }
  }
}

分词结果：["苹果", "华为", "小米", "手机"]
匹配逻辑：至少包含2个词

// 使用百分比
GET /products/_search
{
  "query": {
    "match": {
      "name": {
        "query": "苹果 华为 小米 手机",
        "minimum_should_match": "75%"
      }
    }
  }
}

匹配逻辑：至少包含75%的词（4个词 * 75% = 3个词）
```

### 1.2 Match Phrase Query（短语查询）

#### 基本用法

```json
// 查询包含"苹果手机"短语的文档（词序必须一致）
GET /products/_search
{
  "query": {
    "match_phrase": {
      "name": "苹果手机"
    }
  }
}

匹配：
✅ "苹果手机很好用"
✅ "这是一款苹果手机"
❌ "手机苹果"（词序不对）
❌ "苹果的手机"（中间有其他词）
```

#### Slop参数（允许词间距离）

```json
// 允许词之间有1个词的间隔
GET /products/_search
{
  "query": {
    "match_phrase": {
      "name": {
        "query": "苹果手机",
        "slop": 1
      }
    }
  }
}

匹配：
✅ "苹果手机"（间隔0）
✅ "苹果的手机"（间隔1）
✅ "苹果新款手机"（间隔1）
❌ "苹果最新款手机"（间隔2）
```

### 1.3 Multi Match Query（多字段查询）

#### 基本用法

```json
// 在多个字段中查询
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "iPhone",
      "fields": ["name", "description", "brand"]
    }
  }
}

说明：
- 在name、description、brand三个字段中查询
- 任一字段匹配即可
```

#### 字段权重

```json
// 给不同字段设置不同权重
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "iPhone",
      "fields": ["name^3", "description^2", "brand"],
      "type": "best_fields"
    }
  }
}

权重说明：
- name字段：权重3（最重要）
- description字段：权重2
- brand字段：权重1（默认）

评分计算：
- name匹配的文档评分 * 3
- description匹配的文档评分 * 2
- brand匹配的文档评分 * 1
```

#### Multi Match类型

```json
// 1. best_fields（默认）：取最佳匹配字段的评分
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "iPhone",
      "fields": ["name", "description"],
      "type": "best_fields"
    }
  }
}

// 2. most_fields：综合所有字段的评分
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "iPhone",
      "fields": ["name", "description"],
      "type": "most_fields"
    }
  }
}

// 3. cross_fields：跨字段查询（适合姓名查询）
GET /users/_search
{
  "query": {
    "multi_match": {
      "query": "张三",
      "fields": ["first_name", "last_name"],
      "type": "cross_fields"
    }
  }
}

// 4. phrase：短语查询
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "苹果手机",
      "fields": ["name", "description"],
      "type": "phrase"
    }
  }
}
```

### 1.4 Match All Query（查询所有）

```json
// 查询所有文档
GET /products/_search
{
  "query": {
    "match_all": {}
  }
}

// 查询所有文档，并设置评分
GET /products/_search
{
  "query": {
    "match_all": {
      "boost": 1.0
    }
  }
}
```

---

## 问题2：精确查询如何使用？

### 2.1 Term Query（精确匹配）

#### 基本用法

```json
// 查询category字段精确等于"手机"的文档
GET /products/_search
{
  "query": {
    "term": {
      "category": "手机"
    }
  }
}

注意：
- term查询不分词
- 必须完全匹配
- 适用于keyword类型字段

❌ 错误用法：
GET /products/_search
{
  "query": {
    "term": {
      "name": "iPhone 14"  // name是text类型，会被分词
    }
  }
}

原因：
- name字段类型是text，存储时被分词为["iphone", "14"]
- term查询不分词，查找"iPhone 14"
- 找不到匹配的文档

✅ 正确用法：
GET /products/_search
{
  "query": {
    "term": {
      "name.keyword": "iPhone 14"  // 使用keyword子字段
    }
  }
}
```

#### 设置boost

```json
// 提升评分权重
GET /products/_search
{
  "query": {
    "term": {
      "category": {
        "value": "手机",
        "boost": 2.0
      }
    }
  }
}
```

### 2.2 Terms Query（多值匹配）

```json
// 查询category为"手机"或"电脑"的文档
GET /products/_search
{
  "query": {
    "terms": {
      "category": ["手机", "电脑", "平板"]
    }
  }
}

等价于SQL：
SELECT * FROM products WHERE category IN ('手机', '电脑', '平板')
```

### 2.3 Range Query（范围查询）

#### 数值范围

```json
// 查询价格在5000-10000之间的商品
GET /products/_search
{
  "query": {
    "range": {
      "price": {
        "gte": 5000,
        "lte": 10000
      }
    }
  }
}

参数说明：
- gte：大于等于（>=）
- gt：大于（>）
- lte：小于等于（<=）
- lt：小于（<）

// 查询价格大于5000的商品
GET /products/_search
{
  "query": {
    "range": {
      "price": {
        "gt": 5000
      }
    }
  }
}
```

#### 日期范围

```json
// 查询最近7天创建的商品
GET /products/_search
{
  "query": {
    "range": {
      "created_at": {
        "gte": "now-7d/d",
        "lte": "now/d"
      }
    }
  }
}

日期数学表达式：
- now：当前时间
- now-7d：7天前
- now/d：今天的开始时间（00:00:00）
- now-1M：1个月前
- now-1y：1年前

// 查询指定日期范围
GET /products/_search
{
  "query": {
    "range": {
      "created_at": {
        "gte": "2024-01-01",
        "lte": "2024-01-31",
        "format": "yyyy-MM-dd"
      }
    }
  }
}
```

### 2.4 Exists Query（字段存在查询）

```json
// 查询存在description字段的文档
GET /products/_search
{
  "query": {
    "exists": {
      "field": "description"
    }
  }
}

匹配：
✅ { "description": "这是描述" }
✅ { "description": "" }  // 空字符串也算存在
❌ { }  // 没有description字段
❌ { "description": null }  // null不算存在
```

### 2.5 Prefix Query（前缀查询）

```json
// 查询name以"iPhone"开头的文档
GET /products/_search
{
  "query": {
    "prefix": {
      "name.keyword": "iPhone"
    }
  }
}

匹配：
✅ "iPhone 14"
✅ "iPhone 14 Pro"
❌ "苹果 iPhone"（不是以iPhone开头）

注意：
- 前缀查询性能较差
- 建议在keyword字段上使用
- 考虑使用Edge NGram优化
```

### 2.6 Wildcard Query（通配符查询）

```json
// 查询name包含"Phone"的文档（*表示任意字符）
GET /products/_search
{
  "query": {
    "wildcard": {
      "name.keyword": "*Phone*"
    }
  }
}

通配符：
- *：匹配0个或多个字符
- ?：匹配1个字符

示例：
- "iPhone*"：以iPhone开头
- "*Phone"：以Phone结尾
- "*Phone*"：包含Phone
- "iPhone??"：iPhone后跟2个字符

注意：
- 通配符查询性能很差
- 避免在生产环境大量使用
- 不要以*或?开头（性能极差）
```

### 2.7 Regexp Query（正则表达式查询）

```json
// 查询name匹配正则表达式的文档
GET /products/_search
{
  "query": {
    "regexp": {
      "name.keyword": "iPhone [0-9]+"
    }
  }
}

匹配：
✅ "iPhone 14"
✅ "iPhone 13"
❌ "iPhone Pro"

注意：
- 正则查询性能很差
- 避免复杂的正则表达式
- 考虑使用其他查询方式
```

---

## 问题3：复合查询如何组合？

### 3.1 Bool Query（布尔查询）

#### 基本结构

```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": [],      // 必须匹配（AND，影响评分）
      "filter": [],    // 必须匹配（AND，不影响评分）
      "should": [],    // 应该匹配（OR，影响评分）
      "must_not": []   // 必须不匹配（NOT，不影响评分）
    }
  }
}
```

#### must子句（必须匹配）

```json
// 查询category为"手机"且price在5000-10000之间的商品
GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "category": "手机"
          }
        },
        {
          "range": {
            "price": {
              "gte": 5000,
              "lte": 10000
            }
          }
        }
      ]
    }
  }
}

等价于SQL：
SELECT * FROM products 
WHERE category = '手机' AND price >= 5000 AND price <= 10000
```

#### filter子句（过滤）

```json
// 查询category为"手机"且price在5000-10000之间的商品（不计算评分）
GET /products/_search
{
  "query": {
    "bool": {
      "filter": [
        {
          "term": {
            "category": "手机"
          }
        },
        {
          "range": {
            "price": {
              "gte": 5000,
              "lte": 10000
            }
          }
        }
      ]
    }
  }
}

filter vs must：
- filter：不计算评分，可以缓存，性能更好
- must：计算评分，不能缓存

建议：
- 精确匹配、范围查询使用filter
- 全文搜索使用must
```

#### should子句（应该匹配）

```json
// 查询brand为"苹果"或"华为"的商品
GET /products/_search
{
  "query": {
    "bool": {
      "should": [
        {
          "term": {
            "brand": "苹果"
          }
        },
        {
          "term": {
            "brand": "华为"
          }
        }
      ]
    }
  }
}

等价于SQL：
SELECT * FROM products WHERE brand = '苹果' OR brand = '华为'

// 设置最小匹配数
GET /products/_search
{
  "query": {
    "bool": {
      "should": [
        { "term": { "brand": "苹果" } },
        { "term": { "brand": "华为" } },
        { "term": { "brand": "小米" } }
      ],
      "minimum_should_match": 2
    }
  }
}

说明：至少匹配2个条件
```

#### must_not子句（必须不匹配）

```json
// 查询category为"手机"但brand不是"苹果"的商品
GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "category": "手机"
          }
        }
      ],
      "must_not": [
        {
          "term": {
            "brand": "苹果"
          }
        }
      ]
    }
  }
}

等价于SQL：
SELECT * FROM products WHERE category = '手机' AND brand != '苹果'
```

#### 复杂组合

```json
// 查询：
// 1. category为"手机"
// 2. price在5000-10000之间
// 3. brand为"苹果"或"华为"
// 4. stock大于0
// 5. 不包含"翻新"

GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        {
          "match": {
            "name": "手机"
          }
        }
      ],
      "filter": [
        {
          "term": {
            "category": "手机"
          }
        },
        {
          "range": {
            "price": {
              "gte": 5000,
              "lte": 10000
            }
          }
        },
        {
          "range": {
            "stock": {
              "gt": 0
            }
          }
        }
      ],
      "should": [
        {
          "term": {
            "brand": "苹果"
          }
        },
        {
          "term": {
            "brand": "华为"
          }
        }
      ],
      "must_not": [
        {
          "match": {
            "name": "翻新"
          }
        }
      ],
      "minimum_should_match": 1
    }
  }
}
```

### 3.2 Boosting Query（提升查询）

```json
// 提升包含"新款"的文档评分，降低包含"旧款"的文档评分
GET /products/_search
{
  "query": {
    "boosting": {
      "positive": {
        "match": {
          "name": "手机"
        }
      },
      "negative": {
        "match": {
          "name": "旧款"
        }
      },
      "negative_boost": 0.5
    }
  }
}

说明：
- positive：正向查询，正常评分
- negative：负向查询，降低评分
- negative_boost：负向查询的评分系数（0-1）
```

### 3.3 Constant Score Query（固定评分查询）

```json
// 所有匹配的文档评分都为1.0
GET /products/_search
{
  "query": {
    "constant_score": {
      "filter": {
        "term": {
          "category": "手机"
        }
      },
      "boost": 1.0
    }
  }
}

使用场景：
- 不关心相关性评分
- 只需要过滤结果
- 提升查询性能
```

---

## 问题4：聚合查询如何实现？

### 4.1 Metrics Aggregation（指标聚合）

#### 1. Avg（平均值）

```json
// 计算商品平均价格
GET /products/_search
{
  "size": 0,
  "aggs": {
    "avg_price": {
      "avg": {
        "field": "price"
      }
    }
  }
}

响应：
{
  "aggregations": {
    "avg_price": {
      "value": 3999.5
    }
  }
}
```

#### 2. Sum（求和）

```json
// 计算商品总价值
GET /products/_search
{
  "size": 0,
  "aggs": {
    "total_value": {
      "sum": {
        "field": "price"
      }
    }
  }
}
```

#### 3. Min/Max（最小值/最大值）

```json
// 计算最低价和最高价
GET /products/_search
{
  "size": 0,
  "aggs": {
    "min_price": {
      "min": {
        "field": "price"
      }
    },
    "max_price": {
      "max": {
        "field": "price"
      }
    }
  }
}
```

#### 4. Stats（统计）

```json
// 一次性获取count、min、max、avg、sum
GET /products/_search
{
  "size": 0,
  "aggs": {
    "price_stats": {
      "stats": {
        "field": "price"
      }
    }
  }
}

响应：
{
  "aggregations": {
    "price_stats": {
      "count": 100,
      "min": 999.0,
      "max": 9999.0,
      "avg": 3999.5,
      "sum": 399950.0
    }
  }
}
```

#### 5. Cardinality（去重计数）

```json
// 统计有多少个不同的品牌
GET /products/_search
{
  "size": 0,
  "aggs": {
    "brand_count": {
      "cardinality": {
        "field": "brand"
      }
    }
  }
}

响应：
{
  "aggregations": {
    "brand_count": {
      "value": 10
    }
  }
}

等价于SQL：
SELECT COUNT(DISTINCT brand) FROM products
```

### 4.2 Bucket Aggregation（桶聚合）

#### 1. Terms（分组）

```json
// 按品牌分组，统计每个品牌的商品数量
GET /products/_search
{
  "size": 0,
  "aggs": {
    "brands": {
      "terms": {
        "field": "brand",
        "size": 10
      }
    }
  }
}

响应：
{
  "aggregations": {
    "brands": {
      "buckets": [
        {
          "key": "苹果",
          "doc_count": 30
        },
        {
          "key": "华为",
          "doc_count": 25
        },
        {
          "key": "小米",
          "doc_count": 20
        }
      ]
    }
  }
}

等价于SQL：
SELECT brand, COUNT(*) FROM products GROUP BY brand
```

#### 2. Range（范围分组）

```json
// 按价格区间分组
GET /products/_search
{
  "size": 0,
  "aggs": {
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "to": 2000 },
          { "from": 2000, "to": 5000 },
          { "from": 5000, "to": 10000 },
          { "from": 10000 }
        ]
      }
    }
  }
}

响应：
{
  "aggregations": {
    "price_ranges": {
      "buckets": [
        {
          "key": "*-2000.0",
          "to": 2000.0,
          "doc_count": 10
        },
        {
          "key": "2000.0-5000.0",
          "from": 2000.0,
          "to": 5000.0,
          "doc_count": 30
        },
        {
          "key": "5000.0-10000.0",
          "from": 5000.0,
          "to": 10000.0,
          "doc_count": 40
        },
        {
          "key": "10000.0-*",
          "from": 10000.0,
          "doc_count": 20
        }
      ]
    }
  }
}
```

#### 3. Date Histogram（日期直方图）

```json
// 按月统计商品创建数量
GET /products/_search
{
  "size": 0,
  "aggs": {
    "products_over_time": {
      "date_histogram": {
        "field": "created_at",
        "calendar_interval": "month",
        "format": "yyyy-MM"
      }
    }
  }
}

响应：
{
  "aggregations": {
    "products_over_time": {
      "buckets": [
        {
          "key_as_string": "2024-01",
          "key": 1704067200000,
          "doc_count": 50
        },
        {
          "key_as_string": "2024-02",
          "key": 1706745600000,
          "doc_count": 60
        }
      ]
    }
  }
}

interval选项：
- calendar_interval：日历间隔（year、month、week、day、hour）
- fixed_interval：固定间隔（1d、12h、30m）
```

#### 4. Histogram（数值直方图）

```json
// 按价格区间（每1000元）统计
GET /products/_search
{
  "size": 0,
  "aggs": {
    "price_histogram": {
      "histogram": {
        "field": "price",
        "interval": 1000,
        "min_doc_count": 1
      }
    }
  }
}

响应：
{
  "aggregations": {
    "price_histogram": {
      "buckets": [
        {
          "key": 0.0,
          "doc_count": 10
        },
        {
          "key": 1000.0,
          "doc_count": 20
        },
        {
          "key": 2000.0,
          "doc_count": 30
        }
      ]
    }
  }
}
```

### 4.3 嵌套聚合

```json
// 按品牌分组，计算每个品牌的平均价格和最高价
GET /products/_search
{
  "size": 0,
  "aggs": {
    "brands": {
      "terms": {
        "field": "brand",
        "size": 10
      },
      "aggs": {
        "avg_price": {
          "avg": {
            "field": "price"
          }
        },
        "max_price": {
          "max": {
            "field": "price"
          }
        }
      }
    }
  }
}

响应：
{
  "aggregations": {
    "brands": {
      "buckets": [
        {
          "key": "苹果",
          "doc_count": 30,
          "avg_price": {
            "value": 6999.0
          },
          "max_price": {
            "value": 9999.0
          }
        },
        {
          "key": "华为",
          "doc_count": 25,
          "avg_price": {
            "value": 4999.0
          },
          "max_price": {
            "value": 7999.0
          }
        }
      ]
    }
  }
}

等价于SQL：
SELECT brand, AVG(price), MAX(price) 
FROM products 
GROUP BY brand
```

### 4.4 Pipeline Aggregation（管道聚合）

```json
// 计算每个品牌的平均价格，然后找出平均价格最高的品牌
GET /products/_search
{
  "size": 0,
  "aggs": {
    "brands": {
      "terms": {
        "field": "brand",
        "size": 10
      },
      "aggs": {
        "avg_price": {
          "avg": {
            "field": "price"
          }
        }
      }
    },
    "max_avg_price": {
      "max_bucket": {
        "buckets_path": "brands>avg_price"
      }
    }
  }
}

响应：
{
  "aggregations": {
    "brands": {
      "buckets": [...]
    },
    "max_avg_price": {
      "value": 6999.0,
      "keys": ["苹果"]
    }
  }
}
```

---

## 问题5：如何实现高亮、排序、分页？

### 5.1 高亮（Highlight）

```json
// 高亮搜索结果
GET /products/_search
{
  "query": {
    "match": {
      "name": "手机"
    }
  },
  "highlight": {
    "fields": {
      "name": {}
    }
  }
}

响应：
{
  "hits": {
    "hits": [
      {
        "_source": {
          "name": "苹果手机 iPhone 14"
        },
        "highlight": {
          "name": [
            "苹果<em>手机</em> iPhone 14"
          ]
        }
      }
    ]
  }
}

// 自定义高亮标签
GET /products/_search
{
  "query": {
    "match": {
      "name": "手机"
    }
  },
  "highlight": {
    "pre_tags": ["<strong>"],
    "post_tags": ["</strong>"],
    "fields": {
      "name": {}
    }
  }
}

// 高亮多个字段
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "手机",
      "fields": ["name", "description"]
    }
  },
  "highlight": {
    "fields": {
      "name": {},
      "description": {}
    }
  }
}
```

### 5.2 排序（Sort）

#### 单字段排序

```json
// 按价格升序排序
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "price": {
        "order": "asc"
      }
    }
  ]
}

// 按价格降序排序
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "price": {
        "order": "desc"
      }
    }
  ]
}
```

#### 多字段排序

```json
// 先按价格降序，再按创建时间降序
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "price": {
        "order": "desc"
      }
    },
    {
      "created_at": {
        "order": "desc"
      }
    }
  ]
}
```

#### 处理缺失值

```json
// 将缺失值排在最后
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "sort": [
    {
      "price": {
        "order": "asc",
        "missing": "_last"
      }
    }
  ]
}

missing选项：
- _last：缺失值排在最后
- _first：缺失值排在最前
- 自定义值：例如0
```

#### 按评分和字段组合排序

```json
// 先按相关性评分，再按价格
GET /products/_search
{
  "query": {
    "match": {
      "name": "手机"
    }
  },
  "sort": [
    "_score",
    {
      "price": {
        "order": "asc"
      }
    }
  ]
}
```

### 5.3 分页（Pagination）

#### From/Size分页

```json
// 查询第1页，每页10条
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "from": 0,
  "size": 10
}

// 查询第2页
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "from": 10,
  "size": 10
}

// 查询第N页
from = (page - 1) * size

注意：
- from + size不能超过10000（默认限制）
- 深度分页性能差
```

#### Search After分页（推荐）

```json
// 第一次查询
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "size": 10,
  "sort": [
    {
      "price": "asc"
    },
    {
      "_id": "asc"
    }
  ]
}

// 响应
{
  "hits": {
    "hits": [
      {
        "_id": "10",
        "_source": {...},
        "sort": [999, "10"]
      }
    ]
  }
}

// 第二次查询（下一页）
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "size": 10,
  "sort": [
    {
      "price": "asc"
    },
    {
      "_id": "asc"
    }
  ],
  "search_after": [999, "10"]
}

优势：
- 性能稳定，不随页数增加而下降
- 适合实时滚动场景
- 不支持跳页
```

### 5.4 指定返回字段

```json
// 只返回指定字段
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "_source": ["name", "price", "brand"]
}

// 排除指定字段
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "_source": {
    "excludes": ["description"]
  }
}

// 包含和排除组合
GET /products/_search
{
  "query": {
    "match_all": {}
  },
  "_source": {
    "includes": ["name", "price"],
    "excludes": ["*.internal"]
  }
}
```

---

## 本章总结

### 核心要点

1. **全文查询**
   - match：分词匹配
   - match_phrase：短语匹配
   - multi_match：多字段匹配

2. **精确查询**
   - term：精确匹配
   - range：范围查询
   - exists：字段存在查询

3. **复合查询**
   - bool query：must、filter、should、must_not
   - filter vs must：filter不计算评分，性能更好

4. **聚合查询**
   - Metrics：avg、sum、min、max、stats
   - Bucket：terms、range、histogram
   - 嵌套聚合：多层分组统计

5. **高亮排序分页**
   - highlight：高亮搜索结果
   - sort：单字段、多字段排序
   - search_after：推荐的分页方式

### 最佳实践

```
✅ 查询优化：
1. 精确匹配使用filter，不是must
2. 避免使用wildcard和regexp
3. 使用search_after代替from/size深度分页
4. 合理使用minimum_should_match

✅ 聚合优化：
1. 设置size=0，不返回文档
2. 使用filter减少聚合范围
3. 避免过深的嵌套聚合
4. 使用cardinality时设置precision_threshold

✅ 性能优化：
1. 只返回需要的字段（_source）
2. 使用routing优化查询
3. 合理设置分片数
4. 开启查询缓存
```

---

## 下一章预告

**11-Java客户端使用详解**

下一章将详细讲解：
- RestHighLevelClient使用
- Spring Data Elasticsearch集成
- 完整CRUD操作示例
- 批量操作和异步操作
- 连接池配置和最佳实践

敬请期待！
