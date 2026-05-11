# hm-dianping 项目结构文档

## 项目概述

**hm-dianping** 是一个基于 Spring Boot 2.3.12 的后端项目，仿大众点评风格的商户点评与社交电商平台。项目源自"虎哥"教学课程，涵盖了电商/点评平台的核心业务场景：用户登录、商户查询、优惠券秒杀、社交博客、关注动态等。

### 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 2.3.12.RELEASE |
| 语言 | Java 8 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.4.3 |
| 数据库 | MySQL 5.x (数据库名 `hm_dianping`) |
| 缓存/分布式 | Redis (Lettuce + Redisson 3.27.2) |
| 工具库 | Hutool 5.7.17、Lombok 1.18.30 |
| AOP | AspectJ Weaver |

### 启动入口

```
com.hmdp.HmDianPingApplication
```

注解说明：
- `@SpringBootApplication` — 标准 Spring Boot 入口
- `@MapperScan("com.hmdp.mapper")` — 自动扫描 MyBatis-Plus Mapper
- `@EnableCaching` — 启用 Spring Cache（Redis 作为后端）
- `@EnableAspectJAutoProxy(exposeProxy = true)` — 暴露 AOP 代理（秒杀订单中通过 `AopContext.currentProxy()` 解决事务代理失效问题）

### 服务配置（application.yaml）

- 端口：`8081`
- MySQL：`localhost:3306/hm_dianping`
- Redis：`127.0.0.1:6379`，database 1，Lettuce 连接池（max-active=10）
- Jackson：序列化时排除 null 字段

---

## 目录结构

```
hm-dianping/
├── pom.xml
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/com/hmdp/
│   │   │   ├── HmDianPingApplication.java          # 启动类
│   │   │   ├── config/                              # Spring 配置（4 个文件）
│   │   │   ├── controller/                          # REST 控制器（9 个文件）
│   │   │   ├── dto/                                 # 数据传输对象（4 个文件）
│   │   │   ├── entity/                              # 数据库实体（10 个文件）
│   │   │   ├── mapper/                              # MyBatis-Plus Mapper（10 个文件）
│   │   │   ├── service/                             # Service 接口（10 个文件）
│   │   │   │   └── impl/                            # Service 实现（10 个文件）
│   │   │   └── utils/                               # 工具类（12 个文件）
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── db/
│   │       │   └── hmdp.sql                         # 完整建表语句 + 种子数据
│   │       ├── lua/                                 # Redis Lua 脚本
│   │       │   ├── ReleaseRedisLockScript.lua       # 分布式锁安全释放
│   │       │   └── SeckillOrderServiceScript.lua    # 秒杀订单原子操作
│   │       └── mapper/
│   │           └── VoucherMapper.xml                # 自定义 SQL（优惠券连表查询）
│   └── test/
│       └── java/com/hmdp/
│           └── HmDianPingApplicationTests.java      # 并发 ID 生成测试
└── target/                                          # 编译输出
```

---

## 分层详解

### 1. 配置层（config/）

| 文件 | 职责 |
|------|------|
| `MvcConfig.java` | 注册两个拦截器：`RefreshTokenInterceptor`（所有路径，从 Header 读取 token 填充 ThreadLocal，刷新 TTL）和 `LoginInterceptor`（非公开路径，未登录返回 401）。公开路径：`/shop/**`、`/shop-type/**`、`/voucher/**`、`/blog/hot`、`/user/code`、`/user/login` |
| `MybatisConfig.java` | 配置 MyBatis-Plus MySQL 分页插件 |
| `RedisConfiguration.java` | 三个 Bean：(1) `RedisTemplate`（String key + GenericJackson2Json value 序列化）；(2) Spring `CacheManager`（Redis 缓存，30 分钟 TTL，不缓存 null）；(3) `RedissonClient` 分布式锁客户端 |
| `WebExceptionAdvice.java` | 全局异常处理 `@RestControllerAdvice`，捕获 `RuntimeException` 返回统一错误响应 |

### 2. 控制器层（controller/）

| 控制器 | 路径 | 核心接口 |
|--------|------|----------|
| `UserController` | `/user` | `POST /code` 发送验证码、`POST /login` 登录、`GET /me` 获取当前用户、`GET /info/{id}` 用户详情 |
| `ShopController` | `/shop` | `GET /{id}` 带缓存查询、`POST` 创建、`PUT` 更新并删缓存、`GET /of/type` 按分类分页、`GET /of/name` 按名称搜索 |
| `ShopTypeController` | `/shop-type` | `GET /list` 全部分类（`@Cacheable` 缓存） |
| `VoucherController` | `/voucher` | `POST` 添加普通券、`POST /seckill` 添加秒杀券、`GET /list/{shopId}` 商户券列表 |
| `VoucherOrderController` | `/voucher-order` | `POST /seckill/{id}` 秒杀下单 |
| `BlogController` | `/blog` | `POST` 发帖、`PUT /like/{id}` 点赞、`GET /of/me` 我的帖子、`GET /hot` 热门帖子 |
| `FollowController` | `/follow` | 待实现 |
| `BlogCommentsController` | `/blog-comments` | 待实现 |
| `UploadController` | `/upload` | `POST /blog` 上传图片到本地磁盘、`GET /blog/delete` 删除图片 |

### 3. 实体层（entity/）

所有实体使用 MyBatis-Plus 注解（`@TableName`、`@TableId`、`@TableField`）和 Lombok（`@Data`、`@EqualsAndHashCode`、`@Accessors(chain=true)`）。

| 实体 | 对应表 | 说明 |
|------|--------|------|
| `User` | `tb_user` | 用户账号（手机号、密码、昵称、头像） |
| `UserInfo` | `tb_user_info` | 用户扩展资料（城市、简介、粉丝、关注数、性别、生日、积分、等级） |
| `Shop` | `tb_shop` | 商户（名称、类型、图片、区域、地址、经纬度、均价、销量、评分、营业时间）。含 transient `distance` 字段用于距离计算 |
| `ShopType` | `tb_shop_type` | 商户分类（名称、图标、排序） |
| `Voucher` | `tb_voucher` | 优惠券（标题、副标题、规则、支付价、实际价值、类型、状态）。含 transient 字段 `stock`、`beginTime`、`endTime` |
| `SeckillVoucher` | `tb_seckill_voucher` | 秒杀券（库存、开始/结束时间），与 tb_voucher 一对一 |
| `VoucherOrder` | `tb_voucher_order` | 订单（用户ID、券ID、支付类型、状态）。状态流转：未支付→已支付→已使用→已取消→退费中→已退费 |
| `Blog` | `tb_blog` | 用户帖子/评测（商户ID、用户ID、标题、图片、内容、点赞数、评论数）。含 transient 字段 `icon`、`name`、`isLike` |
| `BlogComments` | `tb_blog_comments` | 评论（用户ID、博客ID、父ID 用于楼层嵌套、回复ID、内容、状态） |
| `Follow` | `tb_follow` | 社交关注关系（用户ID、被关注用户ID） |

### 4. Mapper 层（mapper/）

全部继承 `BaseMapper<T>`，自带标准 CRUD。唯一定制 SQL 在 `VoucherMapper.xml`：

- **VoucherMapper.xml** — `queryVoucherOfShop` 查询，连接 `tb_voucher` 与 `tb_seckill_voucher`，一次性返回券信息及库存、时间窗口。

Mapper 列表：`BlogCommentsMapper`、`BlogMapper`、`FollowMapper`、`SeckillVoucherMapper`、`ShopMapper`、`ShopTypeMapper`、`UserInfoMapper`、`UserMapper`、`VoucherMapper`、`VoucherOrderMapper`。

### 5. Service 层（service/ + service/impl/）

全部实现继承 `ServiceImpl<Mapper, Entity>`。

| Service | 核心方法 |
|---------|----------|
| `IUserService` / `UserServiceImpl` | `sendCode()` — 生成 6 位验证码存 Redis；`loginService()` — 校验验证码，新用户自动注册，生成 UUID token，UserDTO 存入 Redis Hash |
| `IShopService` / `ShopServiceImpl` | `igetById()` — Cache-Aside 模式 + 互斥锁防缓存穿透/击穿；`iupdate()` — 先写库再删缓存 |
| `IShopTypeService` / `ShopTypeServiceImpl` | 基础 CRUD，按 sort 排序列表查询 |
| `IVoucherService` / `VoucherServiceImpl` | `addSeckillVoucher()` — 事务方法，保存券 + 秒杀记录 + 预加载库存到 Redis；`queryVoucherOfShop()` — 调用自定义 Mapper |
| `IVoucherOrderService` / `VoucherOrderServiceImpl` | **最复杂的 Service**。`seckillVoucherOrder2()` 使用 Lua 脚本实现原子库存校验 + 订单去重，然后通过 `BlockingQueue` 异步落库。还包含使用 `synchronized` 和 `Redisson` 分布式锁的旧实现。使用 `AopContext.currentProxy()` 解决事务代理问题 |
| `IBlogService` / `BlogServiceImpl` | 基础 CRUD |
| `IFollowService` / `FollowServiceImpl` | 基础 CRUD |
| `IUserInfoService` / `UserInfoServiceImpl` | 基础 CRUD |
| `IBlogCommentsService` / `BlogCommentsServiceImpl` | 基础 CRUD |
| `ISeckillVoucherService` / `SeckillVoucherServiceImpl` | 基础 CRUD |

### 6. DTO 层（dto/）

| 类 | 说明 |
|----|------|
| `Result` | 统一 API 响应封装，含 `success`、`errorMsg`、`data`、`total` 字段，工厂方法 `ok()`、`ok(data)`、`fail(msg)` |
| `UserDTO` | 轻量用户信息（id、nickName、icon），存入 Redis 会话 |
| `LoginFormDTO` | 登录请求体（phone、code、password） |
| `ScrollResult` | 游标分页结果（list、minTime、offset） |

### 7. 工具层（utils/）

| 类 | 职责 |
|----|------|
| `UserHolder` | ThreadLocal 持有当前登录用户 `UserDTO` |
| `LoginInterceptor` | 检查 `UserHolder.getUser()`，未登录返回 401，`afterCompletion` 清理 ThreadLocal |
| `RefreshTokenInterceptor` | 读取 `authorization` Header，从 Redis Hash 查用户，填充 `UserHolder`，刷新 token TTL |
| `RedisConstants` | Redis Key 前缀和 TTL 集中管理（登录验证码、登录 token、商户缓存、分布式锁、秒杀库存、博客点赞、Feed、商户地理、用户签到） |
| `SimpleRedisLock` | 自定义 Redis 分布式锁：`SET NX` + 唯一线程标识 + Lua 脚本安全释放 |
| `Lock` | 分布式锁接口，定义 `tryLock(keyName, ttl)` 和 `releaseLock(keyName)` |
| `RedisData` | 逻辑过期封装（expireTime + data 对象） |
| `RedisUtils` | 通用 Redis 缓存工具，两种策略：(1) `getObject1` — 互斥锁重建缓存（防穿透）；(2) `getObject2` — 逻辑过期 + 异步后台重建 |
| `RegexUtils` / `RegexPatterns` | 手机号、邮箱、密码、验证码正则校验 |
| `PasswordEncoder` | 自定义加盐 MD5 密码哈希 |
| `SystemConstants` | 系统常量：图片上传目录（nginx 路径）、分页大小、错误消息 |
| `GlobalUniqueIDGenerator` | 类 Snowflake 分布式 ID 生成器，使用 Redis `INCR` + 位运算（时间戳位 + 计数器位） |

---

## Lua 脚本

### SeckillOrderServiceScript.lua — 秒杀订单原子操作

1. 从 Redis 检查库存 `stock:seckillvoucher:{voucherId}`
2. 检查用户是否已下单 `SISMEMBER` 查询 `order:seckillvoucher:{voucherId}`
3. 两项均通过：`INCRBY` 扣减库存 + `SADD` 添加用户到订单集合
4. 返回值：0 = 成功，1 = 库存不足，2 = 重复下单

### ReleaseRedisLockScript.lua — 分布式锁安全释放

仅在当前线程标识与锁中存储的标识匹配时才执行 `DEL`，防止误删其他线程的锁。

---

## 数据库表概览

数据库名：`hm_dianping`，完整建表语句在 `src/main/resources/db/hmdp.sql`。

| 表名 | 实体 | 用途 |
|------|------|------|
| `tb_user` | `User` | 用户账号 |
| `tb_user_info` | `UserInfo` | 用户扩展资料 |
| `tb_shop` | `Shop` | 商户信息 |
| `tb_shop_type` | `ShopType` | 商户分类 |
| `tb_voucher` | `Voucher` | 优惠券 |
| `tb_seckill_voucher` | `SeckillVoucher` | 秒杀券 |
| `tb_voucher_order` | `VoucherOrder` | 订单 |
| `tb_blog` | `Blog` | 用户帖子/评测 |
| `tb_blog_comments` | `BlogComments` | 评论 |
| `tb_follow` | `Follow` | 关注关系 |

---

## 核心架构模式

```
HTTP 请求
    │
    ▼
拦截器链（RefreshTokenInterceptor → LoginInterceptor）
    │
    ▼
Controller（REST 端点，请求/响应映射）
    │
    ▼
Service（业务逻辑、缓存策略、分布式锁、事务）
    │
    ▼
Mapper（MyBatis-Plus ORM，BaseMapper CRUD + 自定义 XML SQL）
    │
    ▼
MySQL 数据库
```

### 关键技术实践

| 模式 | 实现方式 |
|------|----------|
| **认证** | Token 机制，Redis Hash 存储 UserDTO，非 HttpSession |
| **缓存** | 多级策略：Spring Cache 注解、手动 Cache-Aside + 互斥锁、逻辑过期 + 异步重建 |
| **分布式锁** | 双重实现：自定义 `SimpleRedisLock`（SET NX + Lua）和 Redisson |
| **异步下单** | `BlockingQueue` + 单线程 `ExecutorService` 异步落库，应对秒杀高并发 |
| **原子操作** | Lua 脚本保证库存扣减与订单去重的原子性 |
| **全局 ID** | Redis `INCR` + 位运算的类 Snowflake ID 生成器 |
