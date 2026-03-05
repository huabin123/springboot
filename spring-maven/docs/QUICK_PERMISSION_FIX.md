# Maven上传权限快速修复指南

> 🚀 快速解决405/403权限错误的3分钟指南

---

## 🎯 问题症状

- ❌ **405 Method Not Allowed** - 仓库不支持上传
- ❌ **403 Forbidden** - 用户没有上传权限
- ❌ **401 Unauthorized** - 认证失败

---

## ⚡ 快速修复步骤

### Step 1: 登录Maven私服管理界面

```bash
# Nexus 3
http://your-server:8081

# 默认账号
用户名: admin
密码: 查看 $NEXUS_DATA/admin.password 或 admin123
```

### Step 2: 创建部署用户（3个点击）

```
1. Security → Users → Create local user
2. 填写信息:
   - User ID: deployer
   - Password: <设置强密码>
   - Status: Active
3. 点击 Create
```

### Step 3: 分配部署权限（2个点击）

```
1. 编辑刚创建的用户 → Roles 标签
2. 添加角色: nx-deploy
3. 点击 Save
```

### Step 4: 检查仓库配置（1个点击）

```
1. Repository → Repositories → maven-releases
2. 确认配置:
   ✅ Type: hosted
   ✅ Deployment policy: Allow redeploy (或 Disable redeploy)
   ❌ 不能是: Read-only
```

### Step 5: 配置Maven认证（编辑1个文件）

编辑 `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>releases</id>
            <username>deployer</username>
            <password>你设置的密码</password>
        </server>
    </servers>
</settings>
```

### Step 6: 测试上传（1条命令）

```bash
curl -u deployer:password \
  http://your-server:8081/repository/maven-releases/

# 预期结果: 200 OK (不是405或403)
```

---

## 🔍 常见错误速查

### 错误1: 405 Method Not Allowed

**原因**: 仓库类型错误或URL错误

**快速修复**:
```bash
# ❌ 错误URL (group/proxy类型)
http://nexus:8081/repository/maven-public/

# ✅ 正确URL (hosted类型)
http://nexus:8081/repository/maven-releases/
```

### 错误2: 403 Forbidden

**原因**: 用户没有部署权限

**快速修复**:
```bash
Security → Users → deployer → Roles
添加: nx-deploy 或 maven-deployer
```

### 错误3: 401 Unauthorized

**原因**: 用户名密码错误或未配置

**快速修复**:
```bash
# 检查settings.xml中的用户名密码
cat ~/.m2/settings.xml

# 确保server id匹配
pom.xml:  <repository><id>releases</id>
settings: <server><id>releases</id>  # 必须一致
```

---

## 📋 验证清单（30秒检查）

在上传前，快速确认：

- [ ] 用户已创建（deployer）
- [ ] 用户有nx-deploy角色
- [ ] 仓库类型是hosted
- [ ] Deployment policy不是Read-only
- [ ] settings.xml配置了认证
- [ ] server id与repository id一致
- [ ] curl测试返回200

---

## 🎨 Nexus 3 权限配置可视化

```
┌─────────────────────────────────────────┐
│  Security → Users → Create local user   │
├─────────────────────────────────────────┤
│  User ID: deployer                      │
│  Password: ********                     │
│  Status: ✅ Active                      │
│  Roles: [nx-deploy]                     │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  Repository → maven-releases            │
├─────────────────────────────────────────┤
│  Type: hosted                           │
│  Deployment policy: Allow redeploy      │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  ~/.m2/settings.xml                     │
├─────────────────────────────────────────┤
│  <server>                               │
│    <id>releases</id>                    │
│    <username>deployer</username>        │
│    <password>********</password>        │
│  </server>                              │
└─────────────────────────────────────────┘
              ↓
         ✅ 上传成功
```

---

## 🚀 一键测试脚本

保存为 `test-maven-upload.sh`:

```bash
#!/bin/bash

NEXUS_URL="http://your-server:8081"
REPO_PATH="repository/maven-releases"
USERNAME="deployer"
PASSWORD="your-password"

echo "🔍 测试Maven仓库上传权限..."

# 测试1: 认证
echo -n "1. 测试认证... "
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $USERNAME:$PASSWORD $NEXUS_URL/$REPO_PATH/)
if [ $HTTP_CODE -eq 200 ]; then
    echo "✅ 通过 (200)"
else
    echo "❌ 失败 ($HTTP_CODE)"
    exit 1
fi

# 测试2: 上传权限
echo -n "2. 测试上传权限... "
echo "test" > /tmp/test.txt
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $USERNAME:$PASSWORD \
    -X PUT --upload-file /tmp/test.txt \
    $NEXUS_URL/$REPO_PATH/com/test/test/1.0.0/test.txt)
if [ $HTTP_CODE -eq 201 ] || [ $HTTP_CODE -eq 200 ]; then
    echo "✅ 通过 ($HTTP_CODE)"
else
    echo "❌ 失败 ($HTTP_CODE)"
    exit 1
fi

rm /tmp/test.txt
echo ""
echo "🎉 所有测试通过！可以开始上传JAR包了。"
```

使用方法:
```bash
chmod +x test-maven-upload.sh
./test-maven-upload.sh
```

---

## 📚 详细文档

如需更详细的配置说明，请查看:
- [MAVEN_PERMISSION_GUIDE.md](./MAVEN_PERMISSION_GUIDE.md) - 完整权限配置指南
- [MAVEN_DEPLOY_GUIDE.md](./MAVEN_DEPLOY_GUIDE.md) - 部署和上传指南

---

## 💡 最佳实践

1. **不要使用admin账号部署** - 创建专用deployer账号
2. **使用Token认证** - 比密码更安全（Nexus: Security → User Token）
3. **分离Release和Snapshot权限** - 不同环境使用不同账号
4. **启用审计日志** - 追踪谁上传了什么
5. **定期轮换密码** - 至少每季度更换一次

---

**最后更新**: 2026-02-09
