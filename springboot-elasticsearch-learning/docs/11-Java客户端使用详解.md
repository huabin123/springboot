# 11-Java客户端使用详解

## 本章概述

本章详细讲解如何在Java项目中使用Elasticsearch，重点解决以下问题：
- **问题1**：如何使用RestHighLevelClient？
- **问题2**：如何集成Spring Data Elasticsearch？
- **问题3**：如何实现CRUD操作？
- **问题4**：如何实现批量操作和异步操作？
- **问题5**：如何优化客户端配置？

---

## 问题1：如何使用RestHighLevelClient？

### 1.1 添加依赖

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Elasticsearch Java客户端 -->
    <dependency>
        <groupId>org.elasticsearch.client</groupId>
        <artifactId>elasticsearch-rest-high-level-client</artifactId>
        <version>7.10.0</version>
    </dependency>
    
    <!-- Elasticsearch核心 -->
    <dependency>
        <groupId>org.elasticsearch</groupId>
        <artifactId>elasticsearch</artifactId>
        <version>7.10.0</version>
    </dependency>
    
    <!-- JSON处理 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.11.0</version>
    </dependency>
    
    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>1.7.30</version>
    </dependency>
</dependencies>
```

### 1.2 创建客户端

```java
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticsearchClientFactory {
    
    /**
     * 创建单节点客户端
     */
    public static RestHighLevelClient createClient() {
        return new RestHighLevelClient(
            RestClient.builder(
                new HttpHost("localhost", 9200, "http")
            )
        );
    }
    
    /**
     * 创建集群客户端
     */
    public static RestHighLevelClient createClusterClient() {
        return new RestHighLevelClient(
            RestClient.builder(
                new HttpHost("192.168.1.101", 9200, "http"),
                new HttpHost("192.168.1.102", 9200, "http"),
                new HttpHost("192.168.1.103", 9200, "http")
            )
        );
    }
    
    /**
     * 创建带认证的客户端
     */
    public static RestHighLevelClient createAuthClient() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials("elastic", "password")
        );
        
        return new RestHighLevelClient(
            RestClient.builder(
                new HttpHost("localhost", 9200, "https")
            ).setHttpClientConfigCallback(httpClientBuilder -> 
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            )
        );
    }
    
    /**
     * 关闭客户端
     */
    public static void closeClient(RestHighLevelClient client) {
        try {
            if (client != null) {
                client.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### 1.3 配置优化

```java
import org.apache.http.client.config.RequestConfig;
import org.elasticsearch.client.RestClientBuilder;

public class ElasticsearchClientConfig {
    
    public static RestHighLevelClient createOptimizedClient() {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost("localhost", 9200, "http")
        );
        
        // 设置请求超时
        builder.setRequestConfigCallback(requestConfigBuilder -> 
            requestConfigBuilder
                .setConnectTimeout(5000)        // 连接超时：5秒
                .setSocketTimeout(60000)        // 读取超时：60秒
                .setConnectionRequestTimeout(1000)  // 获取连接超时：1秒
        );
        
        // 设置连接池
        builder.setHttpClientConfigCallback(httpClientBuilder -> 
            httpClientBuilder
                .setMaxConnTotal(100)           // 最大连接数
                .setMaxConnPerRoute(50)         // 每个路由的最大连接数
        );
        
        // 设置失败监听器
        builder.setFailureListener(new RestClient.FailureListener() {
            @Override
            public void onFailure(Node node) {
                System.err.println("Node failed: " + node);
            }
        });
        
        return new RestHighLevelClient(builder);
    }
}
```

---

## 问题2：如何集成Spring Data Elasticsearch？

### 2.1 添加依赖

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
        <version>2.3.12.RELEASE</version>
    </dependency>
    
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>2.3.12.RELEASE</version>
    </dependency>
</dependencies>
```

### 2.2 配置文件

```yaml
# application.yml
spring:
  elasticsearch:
    rest:
      uris: http://localhost:9200
      username: elastic
      password: password
      connection-timeout: 5s
      read-timeout: 60s
```

### 2.3 配置类

```java
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.example.repository")
public class ElasticsearchConfig extends AbstractElasticsearchConfiguration {
    
    @Value("${spring.elasticsearch.rest.uris}")
    private String elasticsearchUrl;
    
    @Value("${spring.elasticsearch.rest.username:}")
    private String username;
    
    @Value("${spring.elasticsearch.rest.password:}")
    private String password;
    
    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = 
            ClientConfiguration.builder()
                .connectedTo(elasticsearchUrl.replace("http://", ""));
        
        // 如果配置了用户名密码
        if (username != null && !username.isEmpty()) {
            builder.withBasicAuth(username, password);
        }
        
        ClientConfiguration clientConfiguration = builder
            .withConnectTimeout(5000)
            .withSocketTimeout(60000)
            .build();
        
        return RestClients.create(clientConfiguration).rest();
    }
}
```

### 2.4 实体类

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.util.Date;
import java.util.List;

@Document(indexName = "products", createIndex = true)
@Setting(shards = 3, replicas = 1)
public class Product {
    
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;
    
    @Field(type = FieldType.Keyword)
    private String category;
    
    @Field(type = FieldType.Double)
    private Double price;
    
    @Field(type = FieldType.Integer)
    private Integer stock;
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;
    
    @Field(type = FieldType.Keyword)
    private List<String> tags;
    
    @Field(type = FieldType.Keyword)
    private String brand;
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public Double getPrice() {
        return price;
    }
    
    public void setPrice(Double price) {
        this.price = price;
    }
    
    public Integer getStock() {
        return stock;
    }
    
    public void setStock(Integer stock) {
        this.stock = stock;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public String getBrand() {
        return brand;
    }
    
    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Date getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

### 2.5 Repository接口

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends ElasticsearchRepository<Product, String> {
    
    // 根据名称查询
    List<Product> findByName(String name);
    
    // 根据分类查询
    List<Product> findByCategory(String category);
    
    // 根据品牌查询
    List<Product> findByBrand(String brand);
    
    // 根据价格区间查询
    List<Product> findByPriceBetween(Double minPrice, Double maxPrice);
    
    // 根据名称模糊查询（分页）
    Page<Product> findByNameLike(String name, Pageable pageable);
    
    // 组合查询
    List<Product> findByCategoryAndPriceBetween(
        String category, 
        Double minPrice, 
        Double maxPrice
    );
    
    // 排序查询
    List<Product> findByCategoryOrderByPriceDesc(String category);
}
```

---

## 问题3：如何实现CRUD操作？

### 3.1 使用RestHighLevelClient

```java
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ProductService {
    
    private RestHighLevelClient client;
    
    public ProductService(RestHighLevelClient client) {
        this.client = client;
    }
    
    /**
     * 创建文档
     */
    public String createProduct(Product product) throws IOException {
        IndexRequest request = new IndexRequest("products");
        request.id(product.getId());
        
        // 方式1：使用Map
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("name", product.getName());
        jsonMap.put("category", product.getCategory());
        jsonMap.put("price", product.getPrice());
        jsonMap.put("stock", product.getStock());
        jsonMap.put("description", product.getDescription());
        jsonMap.put("tags", product.getTags());
        jsonMap.put("brand", product.getBrand());
        jsonMap.put("created_at", product.getCreatedAt());
        
        request.source(jsonMap);
        
        // 方式2：使用JSON字符串
        // String json = new ObjectMapper().writeValueAsString(product);
        // request.source(json, XContentType.JSON);
        
        IndexResponse response = client.index(request, RequestOptions.DEFAULT);
        return response.getId();
    }
    
    /**
     * 查询文档
     */
    public Product getProduct(String id) throws IOException {
        GetRequest request = new GetRequest("products", id);
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        
        if (response.isExists()) {
            Map<String, Object> sourceAsMap = response.getSourceAsMap();
            Product product = new Product();
            product.setId(response.getId());
            product.setName((String) sourceAsMap.get("name"));
            product.setCategory((String) sourceAsMap.get("category"));
            product.setPrice((Double) sourceAsMap.get("price"));
            product.setStock((Integer) sourceAsMap.get("stock"));
            product.setDescription((String) sourceAsMap.get("description"));
            product.setBrand((String) sourceAsMap.get("brand"));
            // ... 其他字段
            return product;
        }
        return null;
    }
    
    /**
     * 更新文档
     */
    public void updateProduct(String id, Map<String, Object> updates) throws IOException {
        UpdateRequest request = new UpdateRequest("products", id);
        request.doc(updates);
        
        UpdateResponse response = client.update(request, RequestOptions.DEFAULT);
        System.out.println("Update result: " + response.getResult());
    }
    
    /**
     * 删除文档
     */
    public void deleteProduct(String id) throws IOException {
        DeleteRequest request = new DeleteRequest("products", id);
        DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
        System.out.println("Delete result: " + response.getResult());
    }
}
```

### 3.2 使用Spring Data Elasticsearch

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ProductServiceImpl {
    
    @Autowired
    private ProductRepository productRepository;
    
    /**
     * 创建商品
     */
    public Product createProduct(Product product) {
        product.setCreatedAt(new Date());
        product.setUpdatedAt(new Date());
        return productRepository.save(product);
    }
    
    /**
     * 根据ID查询商品
     */
    public Product getProductById(String id) {
        Optional<Product> optional = productRepository.findById(id);
        return optional.orElse(null);
    }
    
    /**
     * 更新商品
     */
    public Product updateProduct(Product product) {
        product.setUpdatedAt(new Date());
        return productRepository.save(product);
    }
    
    /**
     * 删除商品
     */
    public void deleteProduct(String id) {
        productRepository.deleteById(id);
    }
    
    /**
     * 查询所有商品
     */
    public List<Product> getAllProducts() {
        Iterable<Product> iterable = productRepository.findAll();
        List<Product> products = new ArrayList<>();
        iterable.forEach(products::add);
        return products;
    }
    
    /**
     * 根据分类查询商品
     */
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    
    /**
     * 根据价格区间查询商品
     */
    public List<Product> getProductsByPriceRange(Double minPrice, Double maxPrice) {
        return productRepository.findByPriceBetween(minPrice, maxPrice);
    }
}
```

### 3.3 复杂查询

```java
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    /**
     * 复杂查询：根据多个条件查询商品
     */
    public List<Product> searchProducts(
        String keyword,
        String category,
        Double minPrice,
        Double maxPrice,
        int page,
        int size
    ) {
        // 构建Bool查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        
        // 关键词搜索（must）
        if (keyword != null && !keyword.isEmpty()) {
            boolQuery.must(
                QueryBuilders.multiMatchQuery(keyword, "name", "description")
            );
        }
        
        // 分类过滤（filter）
        if (category != null && !category.isEmpty()) {
            boolQuery.filter(
                QueryBuilders.termQuery("category", category)
            );
        }
        
        // 价格区间过滤（filter）
        if (minPrice != null || maxPrice != null) {
            boolQuery.filter(
                QueryBuilders.rangeQuery("price")
                    .gte(minPrice != null ? minPrice : 0)
                    .lte(maxPrice != null ? maxPrice : Double.MAX_VALUE)
            );
        }
        
        // 构建查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(boolQuery)
            .withPageable(PageRequest.of(page, size))
            .withSorts(SortBuilders.fieldSort("price").order(SortOrder.ASC))
            .build();
        
        // 执行查询
        SearchHits<Product> searchHits = elasticsearchTemplate.search(
            searchQuery, 
            Product.class
        );
        
        // 提取结果
        return searchHits.stream()
            .map(SearchHit::getContent)
            .collect(Collectors.toList());
    }
    
    /**
     * 聚合查询：统计每个分类的商品数量
     */
    public Map<String, Long> countByCategory() {
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchAllQuery())
            .addAggregation(
                AggregationBuilders.terms("category_count")
                    .field("category")
                    .size(100)
            )
            .build();
        
        SearchHits<Product> searchHits = elasticsearchTemplate.search(
            searchQuery, 
            Product.class
        );
        
        Map<String, Long> result = new HashMap<>();
        
        Aggregations aggregations = searchHits.getAggregations();
        if (aggregations != null) {
            Terms categoryAgg = aggregations.get("category_count");
            for (Terms.Bucket bucket : categoryAgg.getBuckets()) {
                result.put(bucket.getKeyAsString(), bucket.getDocCount());
            }
        }
        
        return result;
    }
}
```

---

## 问题4：如何实现批量操作和异步操作？

### 4.1 批量操作

```java
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BulkOperationService {
    
    private RestHighLevelClient client;
    
    public BulkOperationService(RestHighLevelClient client) {
        this.client = client;
    }
    
    /**
     * 批量创建文档
     */
    public void bulkCreate(List<Product> products) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        
        for (Product product : products) {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("name", product.getName());
            jsonMap.put("category", product.getCategory());
            jsonMap.put("price", product.getPrice());
            jsonMap.put("stock", product.getStock());
            jsonMap.put("brand", product.getBrand());
            
            IndexRequest indexRequest = new IndexRequest("products")
                .id(product.getId())
                .source(jsonMap);
            
            bulkRequest.add(indexRequest);
        }
        
        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        
        if (bulkResponse.hasFailures()) {
            System.err.println("Bulk operation has failures: " + 
                bulkResponse.buildFailureMessage());
        } else {
            System.out.println("Bulk operation completed successfully. " +
                "Items: " + bulkResponse.getItems().length);
        }
    }
    
    /**
     * 批量更新文档
     */
    public void bulkUpdate(Map<String, Map<String, Object>> updates) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        
        for (Map.Entry<String, Map<String, Object>> entry : updates.entrySet()) {
            UpdateRequest updateRequest = new UpdateRequest("products", entry.getKey())
                .doc(entry.getValue());
            bulkRequest.add(updateRequest);
        }
        
        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        
        if (bulkResponse.hasFailures()) {
            System.err.println("Bulk update failed: " + 
                bulkResponse.buildFailureMessage());
        }
    }
    
    /**
     * 批量删除文档
     */
    public void bulkDelete(List<String> ids) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        
        for (String id : ids) {
            DeleteRequest deleteRequest = new DeleteRequest("products", id);
            bulkRequest.add(deleteRequest);
        }
        
        BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        
        if (bulkResponse.hasFailures()) {
            System.err.println("Bulk delete failed: " + 
                bulkResponse.buildFailureMessage());
        }
    }
}
```

### 4.2 使用Spring Data批量操作

```java
@Service
public class BulkProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    /**
     * 批量保存商品
     */
    public void bulkSaveProducts(List<Product> products) {
        productRepository.saveAll(products);
    }
    
    /**
     * 批量删除商品
     */
    public void bulkDeleteProducts(List<String> ids) {
        List<Product> products = new ArrayList<>();
        for (String id : ids) {
            Product product = new Product();
            product.setId(id);
            products.add(product);
        }
        productRepository.deleteAll(products);
    }
}
```

### 4.3 异步操作

```java
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AsyncOperationService {
    
    private RestHighLevelClient client;
    
    public AsyncOperationService(RestHighLevelClient client) {
        this.client = client;
    }
    
    /**
     * 异步创建文档
     */
    public CompletableFuture<String> asyncCreateProduct(Product product) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        IndexRequest request = new IndexRequest("products");
        request.id(product.getId());
        
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put("name", product.getName());
        jsonMap.put("price", product.getPrice());
        // ... 其他字段
        
        request.source(jsonMap);
        
        client.indexAsync(request, RequestOptions.DEFAULT, new ActionListener<IndexResponse>() {
            @Override
            public void onResponse(IndexResponse response) {
                future.complete(response.getId());
            }
            
            @Override
            public void onFailure(Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
    
    /**
     * 异步批量创建文档
     */
    public CompletableFuture<Void> asyncBulkCreate(List<Product> products) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        BulkRequest bulkRequest = new BulkRequest();
        
        for (Product product : products) {
            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("name", product.getName());
            jsonMap.put("price", product.getPrice());
            
            IndexRequest indexRequest = new IndexRequest("products")
                .id(product.getId())
                .source(jsonMap);
            
            bulkRequest.add(indexRequest);
        }
        
        client.bulkAsync(bulkRequest, RequestOptions.DEFAULT, new ActionListener<BulkResponse>() {
            @Override
            public void onResponse(BulkResponse response) {
                if (response.hasFailures()) {
                    future.completeExceptionally(
                        new RuntimeException(response.buildFailureMessage())
                    );
                } else {
                    future.complete(null);
                }
            }
            
            @Override
            public void onFailure(Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}
```

---

## 问题5：如何优化客户端配置？

### 5.1 连接池配置

```java
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClientBuilder;

public class ConnectionPoolConfig {
    
    public static RestHighLevelClient createOptimizedClient() {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost("localhost", 9200, "http")
        );
        
        builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(
                HttpAsyncClientBuilder httpClientBuilder
            ) {
                return httpClientBuilder
                    .setMaxConnTotal(100)           // 最大连接数
                    .setMaxConnPerRoute(50)         // 每个路由的最大连接数
                    .setKeepAliveStrategy((response, context) -> 60000);  // 保持连接60秒
            }
        });
        
        return new RestHighLevelClient(builder);
    }
}
```

### 5.2 重试配置

```java
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClient;

public class RetryConfig {
    
    public static RestHighLevelClient createClientWithRetry() {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost("localhost", 9200, "http")
        );
        
        // 设置重试策略
        builder.setMaxRetryTimeoutMillis(60000);  // 最大重试超时时间
        
        // 设置节点选择器（优先选择本地节点）
        builder.setNodeSelector(NodeSelector.SKIP_DEDICATED_MASTERS);
        
        return new RestHighLevelClient(builder);
    }
}
```

### 5.3 完整的配置示例

```java
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticsearchClientBuilder {
    
    private String[] hosts;
    private String username;
    private String password;
    private int connectTimeout = 5000;
    private int socketTimeout = 60000;
    private int maxConnTotal = 100;
    private int maxConnPerRoute = 50;
    
    public ElasticsearchClientBuilder hosts(String... hosts) {
        this.hosts = hosts;
        return this;
    }
    
    public ElasticsearchClientBuilder auth(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }
    
    public ElasticsearchClientBuilder connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }
    
    public ElasticsearchClientBuilder socketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }
    
    public ElasticsearchClientBuilder maxConnTotal(int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
        return this;
    }
    
    public ElasticsearchClientBuilder maxConnPerRoute(int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
        return this;
    }
    
    public RestHighLevelClient build() {
        HttpHost[] httpHosts = new HttpHost[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            String[] parts = hosts[i].split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
            httpHosts[i] = new HttpHost(host, port, "http");
        }
        
        RestClientBuilder builder = RestClient.builder(httpHosts);
        
        // 设置超时
        builder.setRequestConfigCallback(requestConfigBuilder -> 
            requestConfigBuilder
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout)
        );
        
        // 设置连接池和认证
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder
                .setMaxConnTotal(maxConnTotal)
                .setMaxConnPerRoute(maxConnPerRoute);
            
            if (username != null && password != null) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password)
                );
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            
            return httpClientBuilder;
        });
        
        return new RestHighLevelClient(builder);
    }
}

// 使用示例
RestHighLevelClient client = new ElasticsearchClientBuilder()
    .hosts("192.168.1.101:9200", "192.168.1.102:9200", "192.168.1.103:9200")
    .auth("elastic", "password")
    .connectTimeout(5000)
    .socketTimeout(60000)
    .maxConnTotal(100)
    .maxConnPerRoute(50)
    .build();
```

### 5.4 Spring Boot自动配置

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {
    
    private List<String> hosts;
    private String username;
    private String password;
    private int connectTimeout = 5000;
    private int socketTimeout = 60000;
    private int maxConnTotal = 100;
    private int maxConnPerRoute = 50;
    
    // Getters and Setters
    public List<String> getHosts() {
        return hosts;
    }
    
    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    public int getSocketTimeout() {
        return socketTimeout;
    }
    
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    
    public int getMaxConnTotal() {
        return maxConnTotal;
    }
    
    public void setMaxConnTotal(int maxConnTotal) {
        this.maxConnTotal = maxConnTotal;
    }
    
    public int getMaxConnPerRoute() {
        return maxConnPerRoute;
    }
    
    public void setMaxConnPerRoute(int maxConnPerRoute) {
        this.maxConnPerRoute = maxConnPerRoute;
    }
}
```

```yaml
# application.yml
elasticsearch:
  hosts:
    - 192.168.1.101:9200
    - 192.168.1.102:9200
    - 192.168.1.103:9200
  username: elastic
  password: password
  connect-timeout: 5000
  socket-timeout: 60000
  max-conn-total: 100
  max-conn-per-route: 50
```

```java
@Configuration
public class ElasticsearchAutoConfig {
    
    @Autowired
    private ElasticsearchProperties properties;
    
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        String[] hosts = properties.getHosts().toArray(new String[0]);
        
        return new ElasticsearchClientBuilder()
            .hosts(hosts)
            .auth(properties.getUsername(), properties.getPassword())
            .connectTimeout(properties.getConnectTimeout())
            .socketTimeout(properties.getSocketTimeout())
            .maxConnTotal(properties.getMaxConnTotal())
            .maxConnPerRoute(properties.getMaxConnPerRoute())
            .build();
    }
    
    @PreDestroy
    public void destroy() throws IOException {
        if (elasticsearchClient() != null) {
            elasticsearchClient().close();
        }
    }
}
```

---

## 本章总结

### 核心要点

1. **RestHighLevelClient**
   - 官方推荐的Java客户端
   - 支持同步和异步操作
   - 需要手动管理连接

2. **Spring Data Elasticsearch**
   - 简化开发，自动配置
   - 提供Repository接口
   - 支持方法名查询

3. **CRUD操作**
   - 创建：IndexRequest
   - 查询：GetRequest
   - 更新：UpdateRequest
   - 删除：DeleteRequest

4. **批量操作**
   - 使用BulkRequest
   - 提升性能
   - 注意处理失败情况

5. **客户端优化**
   - 连接池配置
   - 超时配置
   - 重试策略

### 最佳实践

```
✅ 客户端配置：
1. 使用连接池（maxConnTotal=100）
2. 设置合理的超时时间
3. 配置重试策略
4. 使用Builder模式创建客户端

✅ 操作优化：
1. 批量操作使用BulkRequest
2. 大量数据使用异步操作
3. 及时关闭客户端
4. 复用客户端实例

✅ Spring集成：
1. 使用Spring Data简化开发
2. 配置文件管理连接信息
3. 使用@PreDestroy关闭客户端
4. 合理使用Repository和Template

✅ 错误处理：
1. 捕获IOException
2. 处理BulkResponse的失败项
3. 记录错误日志
4. 实现重试机制
```

---

## 下一章预告

**12-性能优化与踩坑指南**

下一章将详细讲解：
- 写入性能优化
- 查询性能优化
- 常见的坑和解决方案
- 性能监控和调优
- 生产环境最佳实践

敬请期待！
