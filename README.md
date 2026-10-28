# 在线出租车 online-taxi

基于 **Spring Cloud Alibaba** 的网约车微服务项目，覆盖乘客端、司机端、运营后台三端，跑通「登录鉴权 → 价格预估 → 自动派单 → 行程流转 → 在线支付」的完整闭环。

> 教学级项目，代码优先保证链路通顺与可读性，生产级特性（限流、熔断、分布式事务、链路追踪）在「已知局限与改进方向」一节如实说明。

---

## 技术栈

| 分类 | 选型 |
|---|---|
| 运行框架 | Spring Boot 2.4.13 / Spring Cloud 2020.0.1 / Spring Cloud Alibaba 2021.1 / JDK 8 |
| 注册配置 | Nacos（`spring-cloud-starter-alibaba-nacos-discovery`） |
| 服务间调用 | OpenFeign + feign-httpclient |
| 持久层 | MySQL（每服务独立库）+ MyBatis-Plus |
| 缓存 / 锁 | Spring Data Redis + Redisson（派单分布式锁） |
| 鉴权 | JWT（`com.auth0:java-jwt`）+ 接入层拦截器 |
| 实时推送 | SSE（`SseEmitter`） |
| 地图能力 | 高德开放平台 REST API（路径规划 / 周边搜车 / 行驶轨迹） |
| 支付 | 支付宝沙箱（`alipay-easysdk`） |
| 前端 | Vue + Vite（乘客端 / 司机端） |


---

## 项目结构

```
online-taxi-public/
├── api-passenger/         # 乘客端入口（登录/估价/下单/取消/当前订单）
├── api-driver/           # 司机端入口（上线/接单/行程推进/收款）
├── api-boss/             # 运营后台（司机/车辆/绑定关系管理）
├── service-verificationcode/   # 验证码服务
├── service-passenger-user/     # 乘客账户
├── service-driver-user/        # 司机/车辆/绑定关系/工作状态
├── service-price/               # 计价规则 + 预估/实际计价
├── serice-map/                  # 高德地图封装（路径/周边搜/轨迹）
├── service-order/               # 订单状态机 + 自动派单（核心）
├── service-sse-push/            # SSE 实时推送
├── service-pay/                 # 支付宝沙箱收款
└── internal-common/             # 公共 DTO / 常量 / 工具类
```

---

## 核心业务流程

### 1. 登录鉴权
手机号 → 验证码（存 Redis）→ 校验通过 → 签发 JWT。
JWT payload 中通过 `identity` 字段区分乘客（`1`）与司机（`2`），接入层拦截器统一校验。

### 2. 价格预估
乘客传起终点经纬度 → `service-price` 调 `serice-map` 拿高德路径规划的距离(米)/时长(秒) → 读 `price_rule` 最新版本计价规则：

```
价格 = 起步价
     + max(0, 里程(km) - 起步里程) × 计程单价
     + 时长(分) × 计时单价
```

计价规则按 `cityCode + vehicleType` 维度版本化（`fareType` / `fareVersion`），下单时快照版本，线上调价不影响进行中的订单。

### 3. 下单与自动派单（核心）
前置校验：城市有司机 → 计价规则为最新版本 → 城市已开通 → 乘客无进行中订单。

订单落库（状态 = `1 订单开始`）后进入派单循环：

```
for radius in [2km, 4km, 5km]:
    以上车点为中心，调高德周边搜车接口找空闲车辆
    对每辆车：
        查绑定关系 → 司机是否出车中 → 车型是否匹配
        Redisson 锁 driverId
        锁内二次校验该司机无进行中订单
        命中 → 回写订单（司机/车辆/接驾位置），状态 = 2
             SSE 推司机（新单详情）
             SSE 推乘客（车牌/车型/司机位置）
             break
```

最多重试 6 次，每次间隔 20s，全部失败则订单置为 `0 无效`。

### 4. 订单状态机

```
1 下单 → 2 司机接单 → 3 去接乘客 → 4 到达上车点
      → 5 乘客上车 → 6 到达目的地 → 7 发起收款 → 8 支付完成
                ↘ 9 订单取消（按操作方 + 当前状态判定提前取消 / 违约取消）
                ↘ 0 订单无效（派单超时）
```

到达目的地时，调高德 `trsearch` 轨迹查询拿实际行驶里程和时长，再算实际车费。

取消规则：接单后 **1 分钟内**取消算「提前取消」，超过 1 分钟算「违约取消」。

### 5. 支付
司机发起收款 → 订单状态 = `7` → 乘客在支付宝沙箱完成支付 → `service-pay` 回调 → 订单状态 = `8 支付完成`。

---

## 快速开始

### 环境要求
- JDK 8
- Maven 3.6+
- MySQL 8.x（每服务独立建库）
- Redis 5.x+
- Nacos 2.x（本地 8848）

### 启动顺序
1. 启动中间件：MySQL、Redis、Nacos
2. 启动基础业务服务（无上游依赖）：
   `service-verificationcode` → `service-passenger-user` → `service-driver-user` → `service-price` → `serice-map`
3. 启动核心服务：`service-order`、`service-sse-push`、`service-pay`
4. 启动接入层：`api-passenger`、`api-driver`、`api-boss`

### 配置占位符
每个模块的 `src/main/resources/application.yml` 中以下字段需要按本地环境填写：

```yaml
spring:
  datasource:
    password: YOUR_MYSQL_PASSWORD   # 改成你的本地 MySQL 密码

alipay:
  appId: YOUR_ALIPAY_APP_ID
  appPrivateKey: YOUR_ALIPAY_APP_PRIVATE_KEY
  publicKey: YOUR_ALIPAY_PUBLIC_KEY
  notifyUrl: YOUR_ALIPAY_NOTIFY_URL
```

> 仓库中已将真实密钥脱敏为占位符，避免公开泄露。

---

## 端口一览

| 服务 | 端口 |
|---|---|
| api-passenger | 8081 |
| service-verificationcode | 8082 |
| service-passenger-user | 8083 |
| service-price | 8084 |
| serice-map | 8085 |
| service-driver-user | 8086 |
| api-boss | 8087 |
| api-driver | 8088 |
| service-order | 8007 |
| service-sse-push | 9000 |
| service-pay（支付宝测试） | 9001 |

---

## 已知局限与改进方向

教学项目，以下为有意简化或尚未落地的部分：

| 现状 | 生产级改进方向 |
|---|---|
| 派单用 `Thread.sleep(20s)` 同步阻塞重试 | 改为 MQ 延迟队列异步化，释放 Tomcat 线程 |
| SSE 连接存单机内存 `Map` | 多实例下换 Redis Pub/Sub 或 MQ 广播 |
| 订单与支付跨库但无分布式事务 | 引入 Seata AT 或本地消息表做最终一致性 |
| 接入层是普通 Spring Boot + 手写拦截器 | 替换为独立 Spring Cloud Gateway，统一限流/鉴权/路由 |
| 无限流熔断 | 接入 Sentinel，网关层 + 服务层双层限流 |
| 无链路追踪 | 接入 SkyWalking，TraceId 透传排查慢调用 |
| 配置硬编码 localhost | 改用 Nacos 配置中心按环境隔离 |

---

## License

MIT
