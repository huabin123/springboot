# 08-Elasticsearch安装与配置

## 本章概述

本章详细讲解Elasticsearch的安装部署和配置优化，重点解决以下问题：
- **问题1**：如何安装Elasticsearch？（单机、集群）
- **问题2**：重要配置参数有哪些？如何配置？
- **问题3**：JVM参数如何调优？
- **问题4**：如何配置安全认证？
- **问题5**：如何使用Docker/Kubernetes部署？

---

## 问题1：如何安装Elasticsearch？

### 1.1 环境要求

```
硬件要求：
- CPU：至少2核，推荐4核以上
- 内存：至少4GB，推荐16GB以上
- 磁盘：SSD推荐，至少50GB可用空间

软件要求：
- Java：JDK 8或JDK 11（ES 7.x自带JDK）
- 操作系统：Linux（推荐）、Windows、macOS
```

### 1.2 单机安装（Linux）

#### 方式1：使用tar.gz包安装

```bash
# 1. 下载Elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-7.10.0-linux-x86_64.tar.gz

# 2. 解压
tar -xzf elasticsearch-7.10.0-linux-x86_64.tar.gz
cd elasticsearch-7.10.0

# 3. 创建数据和日志目录
mkdir -p /data/elasticsearch/data
mkdir -p /data/elasticsearch/logs

# 4. 配置elasticsearch.yml
vim config/elasticsearch.yml

# 基本配置
cluster.name: my-cluster
node.name: node-1
path.data: /data/elasticsearch/data
path.logs: /data/elasticsearch/logs
network.host: 0.0.0.0
http.port: 9200
discovery.type: single-node  # 单节点模式

# 5. 启动Elasticsearch
./bin/elasticsearch

# 或后台启动
./bin/elasticsearch -d

# 6. 验证安装
curl http://localhost:9200

# 响应示例
{
  "name" : "node-1",
  "cluster_name" : "my-cluster",
  "cluster_uuid" : "xxx",
  "version" : {
    "number" : "7.10.0",
    "build_flavor" : "default",
    "build_type" : "tar",
    "build_hash" : "xxx",
    "build_date" : "2020-11-09T21:30:33.964949Z",
    "build_snapshot" : false,
    "lucene_version" : "8.7.0",
    "minimum_wire_compatibility_version" : "6.8.0",
    "minimum_index_compatibility_version" : "6.0.0-beta1"
  },
  "tagline" : "You Know, for Search"
}
```

#### 方式2：使用RPM包安装（CentOS/RHEL）

```bash
# 1. 导入Elasticsearch GPG密钥
rpm --import https://artifacts.elastic.co/GPG-KEY-elasticsearch

# 2. 创建yum仓库配置
cat > /etc/yum.repos.d/elasticsearch.repo <<EOF
[elasticsearch-7.x]
name=Elasticsearch repository for 7.x packages
baseurl=https://artifacts.elastic.co/packages/7.x/yum
gpgcheck=1
gpgkey=https://artifacts.elastic.co/GPG-KEY-elasticsearch
enabled=1
autorefresh=1
type=rpm-md
EOF

# 3. 安装Elasticsearch
yum install elasticsearch

# 4. 配置elasticsearch.yml
vim /etc/elasticsearch/elasticsearch.yml

# 5. 启动Elasticsearch
systemctl start elasticsearch
systemctl enable elasticsearch

# 6. 查看状态
systemctl status elasticsearch

# 7. 查看日志
journalctl -u elasticsearch -f
```

#### 方式3：使用DEB包安装（Ubuntu/Debian）

```bash
# 1. 导入Elasticsearch GPG密钥
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo apt-key add -

# 2. 添加APT仓库
echo "deb https://artifacts.elastic.co/packages/7.x/apt stable main" | sudo tee /etc/apt/sources.list.d/elastic-7.x.list

# 3. 更新并安装
apt-get update
apt-get install elasticsearch

# 4. 配置elasticsearch.yml
vim /etc/elasticsearch/elasticsearch.yml

# 5. 启动Elasticsearch
systemctl start elasticsearch
systemctl enable elasticsearch

# 6. 验证
curl http://localhost:9200
```

### 1.3 集群安装

#### 3节点集群配置

**节点1配置（node-1）**：
```yaml
# /etc/elasticsearch/elasticsearch.yml

# 集群名称（所有节点必须相同）
cluster.name: my-cluster

# 节点名称（每个节点必须唯一）
node.name: node-1

# 节点角色
node.master: true
node.data: true
node.ingest: true

# 数据和日志路径
path.data: /data/elasticsearch/data
path.logs: /data/elasticsearch/logs

# 网络配置
network.host: 192.168.1.101
http.port: 9200
transport.port: 9300

# 集群发现配置（ES 7.x+）
discovery.seed_hosts: ["192.168.1.101", "192.168.1.102", "192.168.1.103"]
cluster.initial_master_nodes: ["node-1", "node-2", "node-3"]

# 跨域配置（可选）
http.cors.enabled: true
http.cors.allow-origin: "*"
```

**节点2配置（node-2）**：
```yaml
# /etc/elasticsearch/elasticsearch.yml

cluster.name: my-cluster
node.name: node-2
node.master: true
node.data: true
node.ingest: true

path.data: /data/elasticsearch/data
path.logs: /data/elasticsearch/logs

network.host: 192.168.1.102
http.port: 9200
transport.port: 9300

discovery.seed_hosts: ["192.168.1.101", "192.168.1.102", "192.168.1.103"]
cluster.initial_master_nodes: ["node-1", "node-2", "node-3"]
```

**节点3配置（node-3）**：
```yaml
# /etc/elasticsearch/elasticsearch.yml

cluster.name: my-cluster
node.name: node-3
node.master: true
node.data: true
node.ingest: true

path.data: /data/elasticsearch/data
path.logs: /data/elasticsearch/logs

network.host: 192.168.1.103
http.port: 9200
transport.port: 9300

discovery.seed_hosts: ["192.168.1.101", "192.168.1.102", "192.168.1.103"]
cluster.initial_master_nodes: ["node-1", "node-2", "node-3"]
```

**启动集群**：
```bash
# 在每个节点上启动Elasticsearch
systemctl start elasticsearch

# 验证集群状态
curl http://192.168.1.101:9200/_cluster/health?pretty

# 响应示例
{
  "cluster_name" : "my-cluster",
  "status" : "green",
  "timed_out" : false,
  "number_of_nodes" : 3,
  "number_of_data_nodes" : 3,
  "active_primary_shards" : 0,
  "active_shards" : 0,
  "relocating_shards" : 0,
  "initializing_shards" : 0,
  "unassigned_shards" : 0,
  "delayed_unassigned_shards" : 0,
  "number_of_pending_tasks" : 0,
  "number_of_in_flight_fetch" : 0,
  "task_max_waiting_in_queue_millis" : 0,
  "active_shards_percent_as_number" : 100.0
}

# 查看节点列表
curl http://192.168.1.101:9200/_cat/nodes?v
```

### 1.4 系统配置优化

#### 1. 文件描述符限制

```bash
# 查看当前限制
ulimit -n

# 临时修改（重启后失效）
ulimit -n 65535

# 永久修改
vim /etc/security/limits.conf

# 添加以下内容
elasticsearch soft nofile 65535
elasticsearch hard nofile 65535
elasticsearch soft nproc 4096
elasticsearch hard nproc 4096

# 验证
su - elasticsearch
ulimit -n
```

#### 2. 虚拟内存配置

```bash
# 查看当前配置
sysctl vm.max_map_count

# 临时修改
sysctl -w vm.max_map_count=262144

# 永久修改
echo "vm.max_map_count=262144" >> /etc/sysctl.conf
sysctl -p

# 说明：
# ES使用mmapfs存储索引，需要大量的虚拟内存映射区域
# 默认值65530太小，会导致启动失败
```

#### 3. 禁用swap

```bash
# 临时禁用
swapoff -a

# 永久禁用
vim /etc/fstab
# 注释掉swap行

# 或者配置ES锁定内存
vim /etc/elasticsearch/elasticsearch.yml
bootstrap.memory_lock: true

# 验证
curl http://localhost:9200/_nodes?filter_path=**.mlockall
```

#### 4. 调整TCP参数

```bash
# 编辑sysctl.conf
vim /etc/sysctl.conf

# 添加以下内容
net.ipv4.tcp_retries2 = 5
net.ipv4.tcp_keepalive_time = 300
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_keepalive_intvl = 15

# 应用配置
sysctl -p
```

---

## 问题2：重要配置参数有哪些？

### 2.1 集群配置

```yaml
# elasticsearch.yml

# 集群名称
cluster.name: my-cluster
# 说明：同一集群的所有节点必须使用相同的集群名称

# 节点名称
node.name: node-1
# 说明：每个节点的名称必须唯一，建议使用有意义的名称

# 节点角色（ES 7.9+推荐使用roles）
node.roles: [ master, data, ingest ]
# 可选值：master, data, data_content, data_hot, data_warm, data_cold, ingest, ml, remote_cluster_client, transform

# 或使用旧版配置
node.master: true
node.data: true
node.ingest: true
node.ml: false
```

### 2.2 路径配置

```yaml
# 数据存储路径
path.data: /data/elasticsearch/data
# 说明：可以配置多个路径，用逗号分隔
# path.data: ["/data1/elasticsearch", "/data2/elasticsearch"]

# 日志路径
path.logs: /var/log/elasticsearch

# 临时文件路径
path.repo: ["/backup/elasticsearch"]
# 说明：用于快照备份
```

### 2.3 网络配置

```yaml
# 绑定地址
network.host: 192.168.1.101
# 说明：
# - 0.0.0.0：绑定所有网卡
# - _local_：绑定本地回环地址
# - _site_：绑定内网地址
# - _global_：绑定外网地址

# HTTP端口
http.port: 9200
# 说明：客户端访问端口

# Transport端口
transport.port: 9300
# 说明：节点间通信端口

# 跨域配置
http.cors.enabled: true
http.cors.allow-origin: "*"
# 说明：允许Kibana等工具跨域访问
```

### 2.4 发现配置

```yaml
# 种子节点（ES 7.x+）
discovery.seed_hosts: ["192.168.1.101", "192.168.1.102", "192.168.1.103"]
# 说明：用于节点发现，可以是IP或主机名

# 初始Master候选节点（ES 7.x+，仅首次启动需要）
cluster.initial_master_nodes: ["node-1", "node-2", "node-3"]
# 说明：首次启动集群时，指定哪些节点参与Master选举

# 单节点模式
discovery.type: single-node
# 说明：单节点部署时使用，跳过集群发现过程

# 最小Master节点数（ES 7.x之前）
discovery.zen.minimum_master_nodes: 2
# 说明：防止脑裂，公式：(master候选节点数 / 2) + 1
# ES 7.x+不需要配置，自动计算
```

### 2.5 内存配置

```yaml
# 锁定内存
bootstrap.memory_lock: true
# 说明：防止ES内存被swap到磁盘，提升性能

# JVM堆内存配置（在jvm.options中配置）
# config/jvm.options
-Xms16g
-Xmx16g
# 说明：
# - Xms和Xmx设置为相同值
# - 不超过物理内存的50%
# - 不超过32GB（压缩指针限制）
```

### 2.6 分片配置

```yaml
# 默认主分片数（ES 7.x+默认为1）
index.number_of_shards: 5

# 默认副本数
index.number_of_replicas: 1

# 单节点最大分片数
cluster.max_shards_per_node: 1000
# 说明：防止分片过多导致性能问题
```

### 2.7 线程池配置

```yaml
# 搜索线程池
thread_pool.search.size: 30
thread_pool.search.queue_size: 1000

# 写入线程池
thread_pool.write.size: 30
thread_pool.write.queue_size: 1000

# 说明：
# - size：线程数，默认为CPU核数 + 1
# - queue_size：队列大小，默认1000
```

### 2.8 慢查询日志

```yaml
# 慢查询阈值
index.search.slowlog.threshold.query.warn: 10s
index.search.slowlog.threshold.query.info: 5s
index.search.slowlog.threshold.query.debug: 2s
index.search.slowlog.threshold.query.trace: 500ms

# 慢索引阈值
index.indexing.slowlog.threshold.index.warn: 10s
index.indexing.slowlog.threshold.index.info: 5s
index.indexing.slowlog.threshold.index.debug: 2s
index.indexing.slowlog.threshold.index.trace: 500ms

# 慢查询日志级别
index.search.slowlog.level: info
index.indexing.slowlog.level: info
```

---

## 问题3：JVM参数如何调优？

### 3.1 堆内存配置

```bash
# config/jvm.options

# 堆内存大小
-Xms16g
-Xmx16g

# 原则：
# 1. Xms和Xmx设置为相同值（避免动态调整堆大小）
# 2. 不超过物理内存的50%（留给OS Cache）
# 3. 不超过32GB（压缩指针限制）

# 为什么不超过32GB？
# - JVM使用压缩指针（Compressed Oops）优化内存
# - 堆内存 <= 32GB时，指针占用4字节
# - 堆内存 > 32GB时，指针占用8字节
# - 超过32GB后，实际可用内存反而减少

# 验证压缩指针是否启用
curl http://localhost:9200/_nodes/jvm?filter_path=**.using_compressed_ordinary_object_pointers
```

### 3.2 垃圾回收配置

#### G1 GC（ES 7.x+默认）

```bash
# config/jvm.options

# 使用G1 GC
-XX:+UseG1GC

# G1 GC相关参数
-XX:G1ReservePercent=25
-XX:InitiatingHeapOccupancyPercent=30

# 说明：
# - G1ReservePercent：保留内存百分比，用于应对突发流量
# - InitiatingHeapOccupancyPercent：触发并发GC的堆占用阈值
```

#### CMS GC（ES 6.x默认，已废弃）

```bash
# config/jvm.options

# 使用CMS GC
-XX:+UseConcMarkSweepGC
-XX:CMSInitiatingOccupancyFraction=75
-XX:+UseCMSInitiatingOccupancyOnly

# 说明：
# - CMSInitiatingOccupancyFraction：触发CMS GC的堆占用阈值
# - UseCMSInitiatingOccupancyOnly：只使用设定的阈值，不自动调整
```

### 3.3 GC日志配置

```bash
# config/jvm.options

# GC日志（JDK 8）
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+PrintTenuringDistribution
-XX:+PrintGCApplicationStoppedTime
-Xloggc:/var/log/elasticsearch/gc.log
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=32
-XX:GCLogFileSize=64m

# GC日志（JDK 9+）
-Xlog:gc*,gc+age=trace,safepoint:file=/var/log/elasticsearch/gc.log:utctime,pid,tags:filecount=32,filesize=64m
```

### 3.4 其他JVM参数

```bash
# config/jvm.options

# 堆外内存
-XX:MaxDirectMemorySize=1g

# 禁用显式GC
-XX:+DisableExplicitGC

# OOM时生成堆转储
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/elasticsearch/heapdump.hprof

# 错误日志
-XX:ErrorFile=/var/log/elasticsearch/hs_err_pid%p.log

# 优化字符串去重（G1 GC）
-XX:+UseStringDeduplication
```

### 3.5 JVM调优建议

```
场景1：搜索为主
- 堆内存：物理内存的50%，不超过32GB
- GC：G1 GC
- 其他：增大OS Cache，提升搜索性能

场景2：写入为主
- 堆内存：物理内存的50%，不超过32GB
- GC：G1 GC
- 其他：调整Refresh间隔，减少GC压力

场景3：大数据量聚合
- 堆内存：接近32GB
- GC：G1 GC
- 其他：使用Doc Values，减少堆内存占用

场景4：小内存环境（< 8GB）
- 堆内存：4GB
- GC：G1 GC
- 其他：减少分片数，降低资源消耗
```

---

## 问题4：如何配置安全认证？

### 4.1 启用X-Pack Security

```yaml
# elasticsearch.yml

# 启用安全功能
xpack.security.enabled: true

# 启用审计日志
xpack.security.audit.enabled: true

# 配置传输层加密（节点间通信）
xpack.security.transport.ssl.enabled: true
xpack.security.transport.ssl.verification_mode: certificate
xpack.security.transport.ssl.keystore.path: certs/elastic-certificates.p12
xpack.security.transport.ssl.truststore.path: certs/elastic-certificates.p12

# 配置HTTP层加密（客户端访问）
xpack.security.http.ssl.enabled: true
xpack.security.http.ssl.keystore.path: certs/elastic-certificates.p12
xpack.security.http.ssl.truststore.path: certs/elastic-certificates.p12
```

### 4.2 生成证书

```bash
# 1. 生成CA证书
bin/elasticsearch-certutil ca
# 输出：elastic-stack-ca.p12

# 2. 生成节点证书
bin/elasticsearch-certutil cert --ca elastic-stack-ca.p12
# 输出：elastic-certificates.p12

# 3. 复制证书到config目录
mkdir config/certs
cp elastic-certificates.p12 config/certs/

# 4. 设置证书权限
chmod 640 config/certs/elastic-certificates.p12
chown elasticsearch:elasticsearch config/certs/elastic-certificates.p12
```

### 4.3 设置内置用户密码

```bash
# 自动生成随机密码
bin/elasticsearch-setup-passwords auto

# 或手动设置密码
bin/elasticsearch-setup-passwords interactive

# 内置用户：
# - elastic：超级用户
# - kibana_system：Kibana连接ES使用
# - logstash_system：Logstash连接ES使用
# - beats_system：Beats连接ES使用
# - apm_system：APM连接ES使用
# - remote_monitoring_user：监控使用

# 保存密码到安全的地方
```

### 4.4 创建自定义用户和角色

```bash
# 创建角色
POST /_security/role/my_admin_role
{
  "cluster": ["all"],
  "indices": [
    {
      "names": ["*"],
      "privileges": ["all"]
    }
  ]
}

# 创建用户
POST /_security/user/my_admin
{
  "password": "my_password",
  "roles": ["my_admin_role"],
  "full_name": "My Admin",
  "email": "admin@example.com"
}

# 创建只读角色
POST /_security/role/read_only_role
{
  "cluster": ["monitor"],
  "indices": [
    {
      "names": ["products*"],
      "privileges": ["read"]
    }
  ]
}

# 创建只读用户
POST /_security/user/reader
{
  "password": "reader_password",
  "roles": ["read_only_role"]
}
```

### 4.5 使用认证访问

```bash
# curl访问
curl -u elastic:password http://localhost:9200

# Java客户端
RestHighLevelClient client = new RestHighLevelClient(
    RestClient.builder(new HttpHost("localhost", 9200, "https"))
        .setHttpClientConfigCallback(httpClientBuilder -> {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "password")
            );
            return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        })
);
```

---

## 问题5：如何使用Docker/Kubernetes部署？

### 5.1 Docker单节点部署

```bash
# 拉取镜像
docker pull elasticsearch:7.10.0

# 运行单节点
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "ES_JAVA_OPTS=-Xms2g -Xmx2g" \
  -v es-data:/usr/share/elasticsearch/data \
  elasticsearch:7.10.0

# 验证
curl http://localhost:9200

# 查看日志
docker logs -f elasticsearch
```

### 5.2 Docker Compose集群部署

```yaml
# docker-compose.yml
version: '3.8'

services:
  es01:
    image: elasticsearch:7.10.0
    container_name: es01
    environment:
      - node.name=es01
      - cluster.name=es-cluster
      - discovery.seed_hosts=es02,es03
      - cluster.initial_master_nodes=es01,es02,es03
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms2g -Xmx2g"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - es01-data:/usr/share/elasticsearch/data
    ports:
      - 9200:9200
    networks:
      - elastic

  es02:
    image: elasticsearch:7.10.0
    container_name: es02
    environment:
      - node.name=es02
      - cluster.name=es-cluster
      - discovery.seed_hosts=es01,es03
      - cluster.initial_master_nodes=es01,es02,es03
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms2g -Xmx2g"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - es02-data:/usr/share/elasticsearch/data
    networks:
      - elastic

  es03:
    image: elasticsearch:7.10.0
    container_name: es03
    environment:
      - node.name=es03
      - cluster.name=es-cluster
      - discovery.seed_hosts=es01,es02
      - cluster.initial_master_nodes=es01,es02,es03
      - bootstrap.memory_lock=true
      - "ES_JAVA_OPTS=-Xms2g -Xmx2g"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    volumes:
      - es03-data:/usr/share/elasticsearch/data
    networks:
      - elastic

volumes:
  es01-data:
  es02-data:
  es03-data:

networks:
  elastic:
    driver: bridge
```

```bash
# 启动集群
docker-compose up -d

# 查看集群状态
curl http://localhost:9200/_cluster/health?pretty

# 查看节点
curl http://localhost:9200/_cat/nodes?v

# 停止集群
docker-compose down

# 停止并删除数据
docker-compose down -v
```

### 5.3 Kubernetes部署

#### StatefulSet配置

```yaml
# elasticsearch-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: elasticsearch
  namespace: elastic
spec:
  serviceName: elasticsearch
  replicas: 3
  selector:
    matchLabels:
      app: elasticsearch
  template:
    metadata:
      labels:
        app: elasticsearch
    spec:
      initContainers:
      - name: increase-vm-max-map
        image: busybox
        command: ["sysctl", "-w", "vm.max_map_count=262144"]
        securityContext:
          privileged: true
      - name: increase-fd-ulimit
        image: busybox
        command: ["sh", "-c", "ulimit -n 65536"]
        securityContext:
          privileged: true
      containers:
      - name: elasticsearch
        image: elasticsearch:7.10.0
        resources:
          limits:
            cpu: 2
            memory: 4Gi
          requests:
            cpu: 1
            memory: 2Gi
        ports:
        - containerPort: 9200
          name: http
        - containerPort: 9300
          name: transport
        env:
        - name: cluster.name
          value: "es-cluster"
        - name: node.name
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: discovery.seed_hosts
          value: "elasticsearch-0.elasticsearch,elasticsearch-1.elasticsearch,elasticsearch-2.elasticsearch"
        - name: cluster.initial_master_nodes
          value: "elasticsearch-0,elasticsearch-1,elasticsearch-2"
        - name: ES_JAVA_OPTS
          value: "-Xms2g -Xmx2g"
        volumeMounts:
        - name: data
          mountPath: /usr/share/elasticsearch/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: "standard"
      resources:
        requests:
          storage: 50Gi
```

#### Service配置

```yaml
# elasticsearch-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch
  namespace: elastic
spec:
  clusterIP: None
  selector:
    app: elasticsearch
  ports:
  - port: 9200
    name: http
  - port: 9300
    name: transport
---
apiVersion: v1
kind: Service
metadata:
  name: elasticsearch-http
  namespace: elastic
spec:
  type: LoadBalancer
  selector:
    app: elasticsearch
  ports:
  - port: 9200
    targetPort: 9200
    name: http
```

#### 部署

```bash
# 创建命名空间
kubectl create namespace elastic

# 部署StatefulSet
kubectl apply -f elasticsearch-statefulset.yaml

# 部署Service
kubectl apply -f elasticsearch-service.yaml

# 查看Pod状态
kubectl get pods -n elastic -w

# 查看Service
kubectl get svc -n elastic

# 查看集群状态
kubectl exec -it elasticsearch-0 -n elastic -- curl http://localhost:9200/_cluster/health?pretty

# 查看日志
kubectl logs -f elasticsearch-0 -n elastic

# 扩容
kubectl scale statefulset elasticsearch --replicas=5 -n elastic

# 删除
kubectl delete -f elasticsearch-statefulset.yaml
kubectl delete -f elasticsearch-service.yaml
```

---

## 本章总结

### 核心要点

1. **安装方式**
   - tar.gz：灵活，适合开发测试
   - RPM/DEB：方便管理，适合生产环境
   - Docker：容器化部署
   - Kubernetes：云原生部署

2. **重要配置**
   - 集群配置：cluster.name, node.name, node.roles
   - 网络配置：network.host, http.port, transport.port
   - 发现配置：discovery.seed_hosts, cluster.initial_master_nodes
   - 内存配置：bootstrap.memory_lock

3. **JVM调优**
   - 堆内存：不超过物理内存50%，不超过32GB
   - GC：推荐G1 GC
   - GC日志：开启GC日志，便于问题排查

4. **安全配置**
   - 启用X-Pack Security
   - 配置SSL/TLS加密
   - 创建用户和角色
   - 使用认证访问

5. **容器化部署**
   - Docker：适合开发测试
   - Docker Compose：适合小型集群
   - Kubernetes：适合大规模生产环境

### 最佳实践

```
✅ 系统配置：
1. 调整文件描述符限制（65535）
2. 调整虚拟内存映射数（262144）
3. 禁用swap
4. 调整TCP参数

✅ JVM配置：
1. Xms和Xmx设置为相同值
2. 堆内存不超过32GB
3. 使用G1 GC
4. 开启GC日志

✅ 集群配置：
1. 至少3个节点
2. 3或5个Master候选节点
3. 配置合理的分片数和副本数
4. 开启慢查询日志

✅ 安全配置：
1. 启用X-Pack Security
2. 配置SSL/TLS加密
3. 使用强密码
4. 定期更新密码
```

### 常见问题

```
Q1：为什么ES启动失败？
A1：检查日志，常见原因：
- 文件描述符限制太小
- 虚拟内存映射数太小
- 端口被占用
- JVM堆内存配置不当

Q2：为什么堆内存不超过32GB？
A2：超过32GB后，JVM无法使用压缩指针，实际可用内存反而减少

Q3：如何选择GC算法？
A3：ES 7.x+推荐使用G1 GC，性能更好

Q4：如何配置SSL/TLS？
A4：使用elasticsearch-certutil生成证书，配置xpack.security.http.ssl.enabled

Q5：Docker部署和物理机部署有什么区别？
A5：Docker部署更灵活，但需要注意资源限制和网络配置
```

---

## 下一章预告

**09-索引设计与Mapping配置**

下一章将详细讲解：
- 如何设计索引结构？
- Mapping字段类型详解
- 动态Mapping vs 静态Mapping
- 索引模板（Index Template）
- 索引生命周期管理（ILM）

敬请期待！
