# 城市社区无人货柜 · 补货盘点临期下架服务

面向社区 / 办公楼 / 医院无人货柜的后端服务，覆盖 **补货上架 → 开门购物识别 → 关门对账成单 → 温控/临期处置 → 盘点 → 售后证据回放/退款 → 批次全链路 → 供应商结算 → 运营分析**。

技术栈：Java 17、Spring Boot 3.3、Spring Security、Spring Data JPA、PostgreSQL 15、Flyway、Kafka（Spring Kafka）、springdoc-openapi（Swagger UI）。

---

## 一、一键启动（Docker Compose，含 PostgreSQL + Kafka）

```bash
docker compose up --build
```

启动后：

- 服务： http://localhost:8080
- Swagger UI： http://localhost:8080/swagger-ui.html
- OpenAPI JSON： http://localhost:8080/v3/api-docs
- PostgreSQL： localhost:5432（库 `uvend` / 用户 `app` / 密码 `app`）
- Kafka（KRaft）： localhost:9092

Compose 中 `app` 的 `EVENTBUS=kafka`，领域事件经真实 Kafka 投递，同时先落 `domain_event_log`（outbox），由定时 relay 兜底重放。**不需要 Kafka 时**把 `EVENTBUS` 改成 `local`（或仅起本机进程，见下节），事件走进程内总线，outbox 照常持久化、可回放。

---

## 二、本地开发与验证

### 1. 准备 PostgreSQL

任选其一：

```bash
# 方式 A：Docker
docker run -d --name uvend-pg -p 5432:5432 \
  -e POSTGRES_DB=uvend -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app postgres:15-alpine

# 方式 B：本机已有 PostgreSQL，建库即可
createdb -h localhost -U app uvend   # 表结构由 Flyway 自动迁移
```

### 2. 运行（默认本地事件总线，无需 Kafka）

```bash
mvn spring-boot:run
# 或
mvn -DskipTests package
DB_HOST=localhost DB_PORT=5432 DB_NAME=uvend DB_USER=app DB_PASSWORD=app \
  java -jar target/uvend-service-1.0.0.jar
```

要接真实 Kafka：先启动 Kafka（`docker compose up kafka`），再加 `EVENTBUS=kafka KAFKA_BOOTSTRAP=localhost:9092`。

### 3. 运行测试

```bash
mvn test
```

- `FullFlowIntegrationTest`：16 个有序用例，H2(PostgreSQL 兼容模式) 跑通全部业务场景；
- `KafkaEventBusTest`：嵌入式 KRaft Kafka，验证补货事件经 **真实 Kafka broker** 投递且 outbox 标记 `published_at`。

最近一次执行结果：`Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`。

### 4. 一键演示脚本（服务启动后）

```bash
bash scripts/demo.sh
```

脚本按真实顺序演示：补货（正常/放错/卡货）→ 双模态识别成单 → 温控锁柜报损 → 临期下架 → 盘点差异 → 投诉证据回放退款 → 结算 → 批次追踪 → 运营分析。

---

## 三、测试账号（HTTP Basic，密码均为 `password123`）

| 账号 | 角色 | 用途 |
|---|---|---|
| `admin` | ADMIN | 全部权限 |
| `operator` | OPERATOR | 运营：温控处置、下架、盘点、人工复核、投诉处理、品类调整、分析 |
| `staff` | STAFF | 补货员：补货、执行下架/盘点录入 |
| `finance` | FINANCE | 财务：供应商结算 |
| `supplier` | SUPPLIER | 供应商：按批次查看损耗与结算（绑定供应商 SUP01） |
| `customer` | CUSTOMER | 居民：开门购物、查看我的订单、发起投诉 |

> Swagger UI 点任意接口右上角 🔓 输入账号密码即可联调；命令行示例：`curl -u staff:password123 ...`。

种子数据：供应商「鲜每日乳业 SUP01」「咔哧零食 SUP02」；货柜 `VM-C001`（社区·冷藏）、`VM-O001`（办公楼·常温）、`VM-H001`（医院·冷藏）；商品鲜牛奶/低温酸奶/短保三明治/曲奇；含 **明日到期 `B-YOG-NEAR`** 与 **已过期 `B-BREAD-EXP`** 批次用于演示临期。

---

## 四、关键场景如何落地

| 无人场景难题 | 系统处理 |
|---|---|
| 记录补货要素 | 补货单含柜机、批次、保质期、数量、货道、温度要求、补货照片 |
| 重量/视觉/RFID 识别 | 开门期间原始信号全部落 `event_log`，识别结论落 `recognition_result`（方式/置信度/判定） |
| 识别错误、证据不足 | 关门多模态对账：低置信度（默认 <0.75）或模态冲突 → 订单 `PENDING + INSUFFICIENT`，**不自动扣款**，转人工 CONFIRM/REJECT |
| 拿起又放回 | PICK 减 PUT_BACK 得净拿取；净额 0 订单 `CANCELLED` 不扣款 |
| 货道卡货 | JAM 事件使该货道拿取判为未交付，挂起人工，不扣款 |
| 冷藏柜温度异常 | 越界即开告警、**锁柜**（拦截补货/售卖）、在柜批次 `quality_hold` 热链冻结；处置放行解冻或整柜报损 |
| 临期未及时下架 | 每日 02:00 扫描在柜批次，按保质期生成临期/过期下架任务，可手动触发；过期严禁售卖 |
| 补货员放错货道 | 设备复核 `actualSlotCode` 与申报不符 → 库存按实际货道入，生成 MISPLACED 归位任务，可 relocate |
| 盘点库存与系统不符 | 盘点快照系统数，录入实盘算差异；差异可处置为盘亏报损 / 找到错放 / 系统调账，全部留流水 |
| 用户被误扣款 | 投诉后客服查看开门记录、事件时间线、识别结果、视频片段、会话前后库存变化；支持全额/部分/按明细退款 |
| 按点位调整品类 | 变更货道陈列商品并留痕，校验商品温控与货柜类型匹配 |
| 批次全程追踪 | 每个批次可查 补货→售卖→下架→报损→结算 的库存流水、报损原因与承担方 |
| 财务结算供应商 | 按周期取**真实售卖（扣除已退）**与报损；供应商承担的损耗按成本从应付款扣减，出批次明细 |
| 证据可回放 | 开门/拿放/识别/库存变化发生在秒级窗口，全部带时间戳持久化，售后按会话回放，不依赖现场 |
| 运营补货决策 | 按点位/货柜统计销量、损耗成本、临期率、商品排行 |

库存任何变动（`RESTOCK/SALE/PUT_BACK/OFF_SHELF/LOSS/STOCKTAKE_ADJUST/MISPLACE_IN/OUT`）都写不可变流水 `inventory_ledger`，这是批次追踪、结算与售后回放的共同事实来源。

### 领域事件主题

`uvend.restock`、`uvend.shopping`、`uvend.order`、`uvend.inventory`、`uvend.temperature`、`uvend.expiry`、`uvend.stocktake`、`uvend.complaint`、`uvend.settlement`。所有事件先写 outbox 表 `domain_event_log` 再投递（事务提交后发送 + relay 定时重放，至少一次，消费端按单号幂等）。

---

## 五、主要接口

| 模块 | 方法与路径 |
|---|---|
| 补货 | `POST /api/staff/restock` |
| 开门/事件/关门 | `POST /api/shopping/sessions`、`POST /sessions/{id}/events`、`POST /sessions/{id}/close` |
| 人工复核 | `GET /api/shopping/orders/pending`、`POST /orders/{id}/review` |
| 温控 | `POST /api/temperature/reports`、`POST /api/temperature/alerts/{no}/resolve` |
| 临期下架 | `POST /api/off-shelf/scan`、`GET /api/off-shelf/tasks`、`POST /tasks/{no}/complete|relocate|skip` |
| 盘点 | `POST /api/stocktake/open`、`/{no}/count`、`/{no}/items/{id}/resolve`、`/{no}/close` |
| 投诉售后 | `POST /api/complaints`、`GET /{no}/evidence`、`POST /{no}/decision` |
| 批次追踪 | `GET /api/batches/{batchNo}/trace` |
| 财务结算 | `POST /api/finance/settlements`、`POST /settlements/{no}/confirm`、`GET /settlements/{no}` |
| 运营分析 | `GET /api/analytics/machines/{code}`、`GET /api/analytics/locations` |
| 品类调整 | `POST /api/assortment/adjust` |
| 查询 | `GET /api/query/my-orders`、`GET /api/query/machines/{code}/inventory` |

---

## 六、验证结论（本环境实际执行过）

以下为在本机真实环境中实际执行并通过的验证（非推断）：

1. `mvn test`：**17/17 通过**（16 业务集成测试 + 1 嵌入式 Kafka 投递/outbox 测试）。
2. 以真实 **PostgreSQL 15** 启动应用：Flyway `V1__init` 成功建表；种子数据加载正常。
3. HTTP 端到端实测（真实 PG 数据落库）：
   - 补货 3 行分别返回 `PLACED / MISPLACED(actualSlot=A04) / JAMMED`，卡货不入库存；
   - 双模态高置信度拿取关门后订单 `CONFIRMED / SUFFICIENT / 6.50`；
   - 上报 15℃ 产生告警并锁柜，锁定期补货返回 `409`，`CONDEMNED` 处置在柜 6 件全部报损、库存清零；
   - 临期酸奶扫描生成 `NEAR_EXPIRY` 任务，执行后 `CONVERTED_LOSS` 并生成报损单号；
   - 盘点系统 20 / 实盘 18 → 差异 -2 处置为 `LOSS` 并关单；
   - 投诉证据包含时间线 5 条、识别 2 条、库存变化 6 条、视频片段 URL；全额退款后订单 `REFUNDED / 6.50`；
   - 批次追踪 `B-YOG-NEAR`：`TEMP_ABUSE 2(SUPPLIER)` + `EXPIRED 3(SUPPLIER)`，流水完整；
   - 运营点位汇总：社区 `lossRatePct=75.0 / nearExpiryRatePct=37.5`；供应商越权访问运营接口返回 `403`；
   - Swagger UI 返回 200。

> 说明：本环境未安装 Docker 守护进程，`Dockerfile`/`docker-compose.yml` 已按标准多阶段构建与 KRaft Kafka 编写，但**未在本机实际执行 `docker build/compose up`**；应用本体已用真实 PostgreSQL + 嵌入式 Kafka 完成等价验证。
