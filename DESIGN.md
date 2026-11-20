# BobaChop 项目书（高性能 SQL 解析内核）

| 项 | 值 |
|---|---|
| 状态 | 设计定稿；实现进行中（任务进度见 ROADMAP.md，已冻结决策见版本记录） |
| 项目性质 | 独立的 SQL 解析内核库（非中间件、非任何现有项目的补丁） |
| 项目名 | BobaChop（GitHub 仓库 `bobachop`；groupId `io.github.susongyan`，包根 `io.github.susongyan.bobachop`；构件前缀 `boba-chop`） |
| 技术基线 | Java 8+，零第三方运行时依赖 |
| 文档目的 | 单一事实源：任何工程师或 AI Agent 依据本文档即可继续设计与实现，无需对话上下文 |

---

## 0. 给后续 AI Agent 的使用说明

1. **本文档自包含**：动机、已定决策（ADR）、规格、验收齐全，不要重新推导已有结论。
2. **第 6 节 ADR 已拍板**。除非发现事实性错误，不要重新讨论；确需变更先追加"变更记录"。
3. **从第 10 节任务清单认领**，每任务有 DoD，注意依赖顺序。
4. **硬约束**：Java 8 语法、零第三方依赖、不复制任何外部项目代码（思路可借鉴，代码必须原创）。
5. 修改设计/规格必须同步更新本文档。仓库创建后，本文档随迁为 github.com/susongyan/bobachop 根目录 DESIGN.md。

---

## 1. 缘起：ANTLR4 的性能问题

### 1.1 问题现象（实测）

实测环境：ANTLR 4.10 运行时 + 大型 MySQL 方言语法（生成代码数十万行级），JDK 21，2026-10；JDK 8 上更差，比例关系不变。

| 成本层 | 实测 | 支付频率 |
|---|---|---|
| JVM 冷启动（生成类加载 + ATN 反序列化 + 静态初始化） | MySQL 方言 621ms / 更大方言 ~2s | 每方言每进程一次 |
| 新语法路径首走（`adaptivePredict` 现场构建 DFA + JIT 未热） | 6~80ms，第二次降至 150~660µs，稳态 31~54µs | 每条语法决策路径一次 |
| 稳态单价 | 简单查询 ~6µs；IN(1000) ~1.2ms；批量 INSERT 1000 行 ~7.8ms（≈8µs/行） | 每次全量解析 |
| 两阶段解析回退（SLL 失败换 LL 整体重解析） | 代价 ×2 | 部分 SQL 每次解析都付 |

### 1.2 根因：这是 ALL(*) 算法的设计属性，不是实现缺陷

解析器流派的本质差异是**消歧成本的支付时机**：

```
yacc/LALR   → 编译期机器造表     （文法必须迁就机器，人力成本前置）
ANTLR ALL(*)→ 运行期首走时模拟   （文法随便写，惰性 DFA 缓存，预热曲线是价格标签）
手写递归下降 → 编码期程序员预付   （冷热一致最快，维护人力成本最高）
```

ANTLR4 把"决策在编译期算死"改为"运行期每个决策点第一次走到时现场模拟并缓存"——换取"文法怎么舒服怎么写"。该选择对"解析一次、复用多次"的语言工具（编译器、查询引擎）是正确交换；对**每笔请求都要过解析器的高频场景**（中间件、审计网关、可观测产品），账单被 QPS 放大，且性能方差（6µs 与 80ms 并存）不可 SLA 化。

### 1.3 项目动机

对"SQL 来自受控发版、模板可枚举"的企业场景，**预付型（手写）内核 + 显式范围治理**是解析层的最优组合，而现有开源产品没有一个同时满足：性能确定性 + 范围可控 + 主流生态可集成。本项目的目标就是填这个空。

---

## 2. 定位与形态

```
输入：SQL 字符串（文本协议，含字面量）
输出：自有精简 AST（表/列/表达式/参数/字面量/全节点源文本位置）
不做：路由/改写/归并/连接治理（那是中间件的事，本库是它们的解析底座）
```

- 本体是**内核库**：可嵌入任何需要"快而确定"解析 DML 的 Java 程序
- 适配层（AST→某中间件的数据结构）属集成方工作流，不在本体范围；本库保证 AST 契约稳定以降低适配成本
- 配套资产与内核同仓：对拍框架、语料库、基准套件

## 3. 目标与保证

四性质框架是一切设计决策的衡量标准。每条目标配**可验证的保证条款**：

### 3.1 性能 → 保证"冷热一致、无预热曲线"
- 目标：稳态 µs 级；长 SQL 成本随规模线性；首走新形态收敛方差 <2x（**收敛口径**＝第 10000 次/稳态——首跑的类加载+解释执行+JIT 编译是 JVM 通用一次性成本、任何 Java 代码皆有，不计入；K 按分层编译 Tier4 阈值物理选定。v2.1 修订，见版本记录）
- 保证机制：手写递归下降（无惰性 DFA）、无回溯政策（§6 D2）、每次解析零缓存零全局状态
- 验证：基准套件四类场景（冷启动/首走/稳态/长 SQL 线性度）持续回归

### 3.2 准确性 → 保证"白名单内语义与参考实现逐字段等价"
- 目标：白名单内构造的 AST 与成熟 ANTLR4 参考内核（同方言语法）语义等价，**含全部位置偏移**
- 保证机制：对拍三关（§8）；AST 深度比较逐字段断言；差异即失败、即禁止发布
- 验证：语料库全量对拍差异率 ≡ 0

### 3.3 范围可控 → 保证"支持面显式、不支持的不装懂"
- 目标：支持面 = 显式白名单规格 = 代码结构 = CI 门禁，四者一一对应
- 保证机制：白名单外构造返回 `UnsupportedSyntaxException`（携带构造名与位置），**绝不产出看似正确的错误树**；调用方以此做降级决策
- 验证：注入白名单外样例 100% 被显式拒绝并命中可捕获异常类型

### 3.4 可扩展 → 保证"加东西不破坏既有"
- 目标：加构造/加方言为增量三件套（规格条目+解析器函数+对拍用例）
- 保证机制：构造间零共享状态（各构造独立函数）；AST 契约发布即冻结、只增不改
- 验证：新增构造的 PR 不触碰既有构造代码路径（评审清单项）

## 4. 非目标（v1 明确不做）

- 不支持白名单外语法（DDL/DCL/DAL、CTE、窗口函数、存储过程、命名参数、q-字符串）
- 不做 SQL 生成/格式化/美化
- 不承诺与任何数据库官方语法 100% 兼容——只承诺白名单内与参考实现对拍一致
- 不做调用方的解析结果缓存（仅提供 `fingerprint()` 辅助接口供集成方自建模板缓存）
- 非 JVM 环境

## 5. 使用场景

**适用**
- 数据库中间件（分库分表/透明加密/读写分离）的解析底座：每笔请求过解析、要求确定性延迟
- SQL 审计/防火墙/网关：高频流量下的结构化提取与规则匹配
- 可观测产品（APM/慢日志分析）的 SQL 指纹与结构化：`fingerprint()` 直接产出模板串+参数序列
- 金融/政企受控发版环境：SQL 形态可盘点，白名单治理可行

**不适用**
- 需要全语法覆盖的通用解析需求（应使用 ANTLR4 生成内核——它正是本项目的差分参照）
- 需要 SQL 优化器语义的场景（应使用优化器自带的解析层）
- 语法形态高度动态不可枚举的平台（先做模板缓存，再评估本库覆盖）

---

## 6. 核心技术选择（ADR，已拍板）

> 总衡量标准：四性质（§3）+ 总成本公式 `感知成本 = 单次解析单价 × 付费频率`。本库治理单价并保证确定性；频率由集成方缓存策略治理，二者正交。

| # | 决策 | 衡量记录 |
|---|---|---|
| D1 | **手写递归下降 + Pratt 表达式** | 候选衡量：① ANTLR4 小文法——惰性 DFA 病变小未除根，排除；② LALR——决策表不可手写、Java 生成器生态已死、改文法导致表全局重排违反"代码即规格"，排除；③ JavaCC（LL(k) 固定前瞻）——SQL 的无界歧义（如 `(a,b) VALUES` vs `(SELECT`）逼出 LOOKAHEAD hack 地狱，排除；④ 引入现成手写库——AST 契约与维护责任都在外部，违反 D4/D6，排除。⑤ 手写递归下降+Pratt 有 Clang/rustc/Go/V8/Lua/Vitess/ClickHouse 全行业背书：**预付型、冷热一致、代码即规格** |
| D2 | **无回溯政策** | `peek(k)` 任意远只读前瞻合法；`next()` 单向、不撤销、不重解析。任何"要试退才能定"的构造不进白名单。保证最坏成本由代码长度而非输入指数决定——性能确定性是产品属性 |
| D3 | **显式白名单 + UnsupportedSyntaxException** | 范围可控的实体化：白名单是规格文档、函数目录、CI 门禁规则三重身份。"显式不支持"是给调用方的降级决策依据，是能力不是缺陷 |
| D4 | **自有精简 AST，发布即冻结（只增不改）** | AST 是解析层与一切下游的唯一接口，必须自主可控（反面教材：被生成器类层级焊死的解析库无法换引擎）。冻结保证多方长期协作与适配层稳定 |
| D5 | **全节点位置偏移（start/stop 字符偏移、闭区间），对齐主流生成器约定** | 下游改写/切片/审计定位依赖偏移；与通行约定对齐使差分测试可直接断言 |
| D6 | **零第三方依赖、Java 8+** | 可嵌入任何存量工程；降低接力与审计门槛 |
| D7 | **对拍体系 v2（v2.4 修订）：差分测试真值分工**——偏移真值=输入串本身、树形真值=WHITELIST 规格可执行化（生成器/规格语法）、方言真值=服务器语法书裁决台账、性能真值=同语言 ANTLR4 陪练；外部引擎（SS/grammars-v4）不当验收器 | 原"以成熟 ANTLR4 方言内核为参考实现"废弃：外部语法与真实方言双向偏差，当裁判即拿另一家 fork 的癖好当标准；五件器 F1–F5 见版本记录 v2.4 |
| D8 | **单内核 + DialectProfile** | 各方言 DML 骨架重合 ~80%，差异集中在词法层与小构造；不按方言写多套 parser |
| D9 | **关键字词法策略：标识符与关键字统一扫描，附查表所得 Kw 种类** | 软关键字（非保留关键字当标识符）问题在词法层消解，不产生互斥分类冲突 |

---

## 7. 实现蓝图

### 7.1 AST 契约（心脏，M0 冻结）

面向中间件决策的**最小充分信息**，全节点携带源文本位置：

```
Statement(Select/Insert/Update/Delete)
 ├── 表与别名（JOIN 结构、派生表）          ← 路由决策需要
 ├── 列引用（owner.name 限定形态）           ← 加密/脱敏/审计改写需要
 ├── 表达式树（Pratt 产物：操作符/函数/子查询）
 │    └── 叶子：字面量(保留原文) / 参数标记(访问序 index) / 列引用
 ├── 投影/分组/排序/分页结构
 └── 全节点 start/stop 偏移                  ← 改写切片需要
辅助接口：fingerprint(SQL) → 模板串 + 参数值序列（字面量归一化，供集成方模板缓存）
```

### 7.2 词法器

显式状态机（switch 字符类别）+ maximal munch，无正则引擎。Token = `type + text(保大小写原文) + start/stop + Kw(可空)`。

规则要点：标识符 `[A-Za-z_][A-Za-z0-9_$]*` 统一扫描后查静态哈希关键字表（D9）；字符串 `''` 转义与方言性反斜杠转义；数字含 `0x`/`0b`/科学计数并保留原文；运算符最长匹配（`<=>`、`<=`、`<>`、`:=`、`||`）；注释跳过但偏移记账不断链。非法字符 → `LexException`（转 UnsupportedSyntax 上抛）。

### 7.3 语法器

- 顶层按首 token 分派 SELECT/INSERT/UPDATE/DELETE；每构造一函数，函数即白名单条目的可执行形态
- 表达式：Pratt 单循环 + 优先级表 `OR(1)<XOR(2)<AND(3)<比较/IS/LIKE/IN/BETWEEN(4)<|(5)<&(6)<移位(7)<加减(8)<乘除模(9)<一元(10)`
- 必须特判（各成独立小函数进规格）：`BETWEEN x AND y` 的 AND 归属；`NOT IN/NOT LIKE/IS NOT NULL`；一元符号；`IN(list)` vs `IN(subquery)` 分派；`*` 投影位/表达式位双身份；`a.b.c` 限定名 vs 小数点
- 无界歧义点（如 `INSERT t (a,b) VALUES` vs `INSERT t (SELECT`）：显式"扫到配对右括号看后继 token"helper（只读前瞻，~15 行）
- 参数下标按访问序分配（白名单构造内访问序=文本序，对拍锁死）

### 7.4 方言 Profile（T10/T11 落地）

方言 = 数据 + 少量锚点开关，三件载体：`lexer.LexerConfig`（字符串转义/双引号身份/反引号/`#` 注释/`--` 空白规则/块注释嵌套/引导字符串前缀集与强制转义前缀集）、`lexer.KeywordTable`（词集+保留字集实例，由方言装配；`mySql()` 工厂=Kw 主段全集−方言追加段）、`dialect.DialectProfile`（MySQL/PostgreSQL/Oracle；开关 `limitCommaOffset`/`fetchFirstPagination`/`tableAliasAs`）。依赖方向 dialect→lexer、parser→dialect，无环。分页形态差异收敛进 `ast.Limit` 语义槽位（MySQL `LIMIT [o,]n`/`LIMIT n OFFSET o`；PG `LIMIT n [OFFSET m [ROW|ROWS]]` 与 `[OFFSET m] FETCH {FIRST|NEXT} n ROW[S] ONLY`；Oracle 12c+ `OFFSET m ROWS FETCH FIRST n ROWS ONLY` 复用同一标准族路径，ROWNUM 分页白名单外）。表别名 AS 引导按方言开关裁决（Oracle 拒绝表位/派生表位 AS，列位各方言恒允许）。**连接词别名消歧（T11）**：PG/Oracle 下 JOIN/LEFT 等为非保留字，表别名位以 `joinStartAhead` 只读前瞻让位给子句——连接词后随连接延续才算连接，否则仍可作裸别名（修复 T10 静默错树缺陷）。合法方言语法但契约无锚的构造以前置只读检查归类 `UnsupportedSyntaxException`（PG：RETURNING/ON CONFLICT/FULL JOIN/LATERAL/EXCEPT/INTERSECT/裸 OFFSET/LIMIT ALL；Oracle：MERGE/多表 INSERT ALL|FIRST/MINUS/START WITH/CONNECT BY/NULLS FIRST|LAST/ROWNUM/LEVEL/PRIOR/SYSDATE/UID/FULL JOIN）。方言次序：**MySQL → PostgreSQL → Oracle**（M3=PostgreSQL，M4=Oracle）。语句骨架预留方言锚点（SELECT/INSERT/UPDATE 尾锚），方言子句挂锚实现；AST 存语法事实（运算符原文），不做跨方言语义归一化。

### 7.5 工程结构

```
bobachop/                             # GitHub 仓库根 = Maven 聚合工程（groupId io.github.susongyan，artifactId boba-chop）
├── boba-chop-core/                   # AST + lexer + parser + dialect + 异常体系
├── boba-chop-diff/                   # 对拍框架：双执行器接口 + AST 反射深度 diff + 报告
├── boba-chop-bench/                  # 基准套件：冷启动/首走/稳态/长SQL线性度
└── corpus/                           # 语料库（开源引入标注来源 + 自建黄金用例；数据目录，非 Maven 模块）
```

包根 `io.github.susongyan.bobachop`，子包 `.ast / .lexer / .parser / .dialect / .api / .exception`；类名不带项目前缀（`Lexer`、`TokenBuffer`、`Parser`、`DialectProfile`…，命名空间由包承担）。对外入口 `api.BobaChop`：`parse(sql, profile) → Statement`、`fingerprint(sql) → 模板串+参数序列`。

---

## 8. 质量门禁与验收

**对拍三关**（v2.4 修订：真值分工见 D7）：
1. 开发期（每 PR）：黄金用例（每构造 SQL→期望 AST）+ F1 偏移不变量（输入串真值）+ F2 规格往返对拍（生成器构造 AST → 渲染 → 重解析判等），差异即失败
2. 语料回归：corpus 全量过 F1 六条不变量 + F2 往返对拍全绿方可合入
3. 集成期影子（由集成方执行）：生产按比例双跑，与 boba-pour 影子 oracle（生产 fork）比对，差异告警 + 秒级回退开关

**KPI**：白名单内对拍差异率 ≡ 0；稳态 p99 较 ANTLR4 同语言规格语法（F5 同机陪练）≥5x；首次与万次解析耗时方差 <2x；长 SQL 成本线性（R² ≥ 0.99）。

---

## 9. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 工程骨架 + **AST 契约定稿评审并冻结** + 异常体系 + 对拍框架跑通空内核 | 对拍框架能对任意两棵 AST 给出字段级差异路径 |
| M1 | MySQL：lexer + TokenBuffer + Pratt 表达式 + SELECT 主干（投影/JOIN/WHERE/GROUP/ORDER/LIMIT/子查询/派生表/UNION/CASE） | 黄金用例 + 语料(SELECT 子集)对拍全绿 |
| M2 | MySQL：INSERT 全家（多行/insert-select/ON DUPLICATE KEY）、UPDATE、DELETE | 全 v1 白名单对拍全绿；bench 达 KPI |
| M3 | PostgreSQL profile + 白名单构造 | PostgreSQL 语料对拍全绿 |
| M4 | Oracle profile + 白名单构造 | Oracle 语料对拍全绿 |

## 10. 任务分解（AI Agent 可认领，含 DoD）

| # | 任务 | 依赖 | DoD |
|---|---|---|---|
| T1 | 工程骨架（Maven 多模块 + CI + checkstyle + JDK8 编译基线） | — | `mvn install` 全绿，模块边界如 §7.5 |
| T2 | AST 契约 v1（§7.1 全部节点 + 位置 + 参数序 + fingerprint 接口） | T1 | 评审通过后冻结；javadoc 完整（契约即文档） |
| T3 | 对拍框架（AST 反射深度 diff + 双执行器接口 + 报告） | T2 | 注入式差异能被逐字段定位；ANTLR4 参考执行器可插拔 |
| T4 | TokenType/Kw/KeywordTable + Lexer（MySQL） | T2 | §7.2 规则全实现；黄金 token 序列单测含全部陷阱样例（转义/0x/0b/包裹标识符/注释偏移/运算符最长匹配） |
| T5 | TokenBuffer | T4 | peek 任意远只读、next 单向；无 undo API（评审确认） |
| T6 | ExprParser（Pratt + 全部特判） | T5 | 表达式黄金用例 + 对拍（经 SELECT 投影位驱动） |
| T7 | SelectParser 主干 | T6 | corpus MySQL SELECT 对拍全绿 |
| T8 | InsertParser（含括号前瞻 helper）/UpdateParser/DeleteParser | T6 | 对应语料对拍全绿 |
| T9 | bench 套件（四类场景） | T7 | 输出 KPI 报告与对比图表 |
| T10 | PostgreSQL Profile + 白名单构造 | T8 | PostgreSQL 语料对拍全绿 |
| T11 | Oracle Profile + 白名单构造 | T10 | Oracle 语料对拍全绿 |

通用约束：Java 8；零依赖；不复制外部代码；每任务含黄金用例与对拍断言；公共类 javadoc 必备。

---

## 11. 风险与缓解

| 风险 | 缓解 |
|---|---|
| AST 设计不当导致后期大改 | M0 冻结前三方案评审；冻结后只增不改，破坏性变更走 v2 并行 |
| 与参考实现语义对不齐（位置/边角） | 对拍逐 token 断言偏移；差分框架先于解析器开工（T3 依赖序） |
| 白名单不够用导致拒绝率高 | 语料先行（真实流量形态盘点）；白名单增长走三件套流程 |
| 单人/Agent 接力断层 | 本文档 + 冻结 AST javadoc + 黄金用例 = 完整知识载体 |
| Oracle 边角远多于 MySQL | M3 独立里程碑；Oracle 白名单从严，宁窄勿错 |

## 12. 附录

**术语**：预付型=消歧成本编码期支付；白名单三件套=规格条目+解析器函数+对拍用例；对拍=同一规格的两种独立机制互证（v2.4 起真值分工见 D7）；fingerprint=字面量归一化模板串；陪练=同语言 ANTLR4 生成 parser（仅性能对照，不当正确性锚点）。

**基准复现方法**：以 ANTLR4 运行时 + 方言生成内核构造微基准。冷启动=进程首调；首走=预热 3 万次某形态后首走新形态取第 1/2/2000 次；稳态=2s 窗口均值；线性度=批量 INSERT 100/300/1000 行拟合。基线数据见 §1.1。

**版本记录**：v2.5（2026-11-20）F2 规格生成器 + canonical 渲染器 + 往返对拍冻结（对拍体系 v2 第二器，树形真值=WHITELIST 规格可执行化的生成器半落地，D7）。diff.AstGenerator（种子可复现，加权递归采样白名单内树：逗号连接无条件、派生表别名必填、ODKU 仅 MySQL、UNION 操作数不带 ORDER BY/LIMIT、表别名 AS 随方言、分页形态随方言）与 core 解析器是**同一规格的两种独立机制**，往返断言因此构成 N 版本校验而非同义反复。三断言（diff.RoundTripTest，默认种子 5000×3，-Dgen.seed/-Dgen.count 扫量）：① AstDiff.compareStructure 跳 start/stop 结构全等但**保留 ParameterMarker.index**——"访问序=文本序"的逐标记批量验证；② 打印幂等 render(parse(render(ast)))==render(ast)；③ 重解析树过 F1 六条偏移不变量（F1 在生成语料上复用）。设计纪律：**生成序=渲染序**（Java 实参左结合求值是窝点——InExpr.target/CASE.operand 曾在构造器实参里后求值先出现，对拍以相邻参数序号对调精确抓出）；AstRenderer 的 13 级优先级表按 WHITELIST §2 **独立复述**（与内核 ExprParser 同源同值、分别书写），**NOT 独立分级为 4**（前缀符号才是 13，NOT a<>b 即 NOT(a<>b)，作左子须括号——渲染器首版误按 13，对拍立即抓出重组）；最小括号化=左子裸⟺子级≥父级、右子裸⟺子级>父级（左结合防重组）；标识符池混入方言非保留字专打 T10 类窝点，让位词三分类保守剔除（连接延续词/语句尾锚词/表达式头键控词 CASE/WHEN/END/TRUE/FALSE——别名位可作标识符、表达式头不可，生成器不做邻接感知）；递归深度预算穿透子查询体与派生表（expr→selectBody 曾重置深度致 StackOverflow）；空/null 不对称跟随解析器（缺席恒 null，空清单文本不可表达）。**抓出并修复内核缺陷 ×3**（皆附回归测试）：① PG 词表 EXISTS 误归非保留——本内核表达式头对 EXISTS 键控（谓词必跟 ( ）与"非保留可作标识符"自相矛盾，PG 附录 C 本列保留→移入 PG_RESERVED；② INSERT 的 ALL/FIRST 多表前置检查在消费 INTO 之后执行——表名位 first 撞上误判（INSERT INTO first 被拒）；检查移至 INTO 前 + 新开关 DialectProfile.multiTableInsert() 仅 Oracle 生效（MySQL 的 FIRST 为非保留字，INSERT first VALUES 合法，不能前置拒绝）；③ INSERT…SELECT 尾缀 ON DUPLICATE KEY UPDATE 的 ON 被 CROSS 连接条件抢占——CROSS JOIN b ON DUPLICATE… 整句被拒；邻接让位（ON DUPLICATE KEY UPDATE 四词邻接无歧义，DUPLICATE 仅 MySQL 词表收录→检查方言自限），条件连接仍消费 ON、INNER+ODKU 撞邻接的非法形态在条件表达式处自然报错不误吞。新增 API：KeywordTable.nonReservedWords()（core 暴露方言非保留集=生成器采样位）、AstDiff.compareStructure。验证：全量构建绿（checkstyle+160 测试 core 124/diff 30/bench 6）；往返扫量 3 种子×5000×3 + 50000×3 大扫≈19.5 万条全绿。台账观察项：PG 的 || 为字符串拼接且优先级高于比较运算（现按统一 13 级表取 OR 级=MySQL 语义）——方言差异化优先级表属 F4 服务器语法书裁决；Oracle 经典 SQL 保留字表不列 CASE/WHEN/END/TRUE/FALSE（PL/SQL 保留），本内核按位置裁决（别名位可用）——与真实 Oracle 行为的对照点记 F4。v2.4（2026-11-20）（2026-11-20）对拍体系 v2 重构定稿 + F1 偏移不变量校验器冻结：**D7 修订——参考的权威来自独立性机制，不来自来源名气**。原"以成熟 ANTLR4 方言内核为参考实现"废弃：SS/grammars-v4 等外部语法与真实方言双向偏差（多收/漏收皆有），当裁判即拿另一家 fork 的癖好当标准，循环论证；参考职责拆为四真值各司其职——偏移=输入串本身（一次独立词法为标尺）、树形=WHITELIST 规格（可执行化为仓内生成器与规格语法）、方言=服务器语法书（sql_yacc.yy/gram.y/Oracle 手册）+内网生产语料裁决台账、性能=同语言 ANTLR4 陪练（只当陪练不当裁判）。五件器：F1 偏移不变量校验器（本次落地）/F2 规格生成器+渲染器+往返对拍/F3 变异模糊+元性质/F4 接受面矩阵+裁决台账/F5 规格语法+同机 KPI；SS 构件彻底退场（至多 F4 裁决时当旁证引注）。F1 落地：diff.SpanWalker（冻结 AST 结构遍历函数，校验器唯一结构事实源，新增节点类型漏更分支只在此一处暴露）+ diff.SpanInvariants 六条——R1 界内/R2 token 边界对齐（span 切进 token 中间=子串手术致命伤）/R3 子含于父/R4 兄弟不重叠（**不查字段间源序**：Limit 的 offset/rowCount 源序随方言形态翻转——LIMIT n OFFSET o vs OFFSET o FETCH FIRST n——非普适不变量）/R5 根对齐（尾分号不入标尺，与解析器吞分号行为一致）/R6 叶子存文=切片（Literal/Star/ParameterMarker/ColumnRef 全等 + FunctionCall 终点必 ')'）；标尺=一次独立词法（Lexer 有独立黄金门禁，公平）。**抓出并修复 D5 缺陷**：InsertParser 的 SelectSource 构造实参左结合求值让 lastStop() 抢在 parseQueryBody() 前取值，INSERT...SELECT 数据源 span 倒挂（stop=start−2）；黄金用例从未断言该节点 span 故漏网，BobaPour 拿此偏移做子串手术直接越界——修复+回归用例。门禁三件：SpanInvariantsTest 正负向黄金用例（六规则各自必着火，防不变量静默失效）/SpanWalkerTest 反射守卫（枚举 ast 包全部 Node 具体类断言全遍历，首轮即抓出极限语句集漏标量子查询形态）/SpanInvariantsCorpusTest 三方言全语料 138 条六条全绿。观察项入台账：别名/列清单/USING 列等字符串字段无 span（D5 现状；BobaPour 改写若需别名偏移属 AST 契约演进议题，不在 F1 解决）。157 测试全绿（core 122/diff 29/bench 6）。v2.3（2026-11-19）T11 Oracle Profile 冻结（M4 完成）：Kw 追加 Oracle 段 47 词只增不改；dialect.OracleDialect——经典保留字表 + 12c 分页 OFFSET/FETCH 与连接条件词 USING 入保留集（子句锚点词在表别名位必须让位给子句，与 T10 PG 的 OFFSET/FETCH 保留同款取舍，数据修正非代码分支）；词法=ANSI 双引号标识符/无反斜杠转义/无反引号/无 `#`/嵌套块注释/`--` 恒注释/仅 `N''` 前缀（q-字符串与 `:name` 绑定变量以既有非法字符词法路径拒绝，零新代码）。DialectProfile 新开关 `tableAliasAs()`：Oracle 拒绝表位/派生表位 AS 引导（ParseException 定性），列位 AS 各方言恒允许，UPDATE/DELETE 目标表位同规。Oracle 12c+ 分页完整复用 T10 parsePgOffsetFetch（ast.Limit 槽位零改动）；LIMIT 不入 Oracle 关键字表→出现即 ParseException；OFFSET 后缀省略（`OFFSET 10 FETCH ...`）为本内核接受的 PG 超集形态（对拍校准点）。Oracle 前置归类 UnsupportedSyntaxException：MERGE（分派位）/多表 INSERT ALL|FIRST/MINUS/START WITH/CONNECT BY（尾部清单追加，仅 Oracle 词、MySQL/PG 恒不触发）/NULLS FIRST|LAST（ORDER BY 项位）/ROWNUM/LEVEL/PRIOR/SYSDATE/UID（表达式位）；USER 同为 PG 保留字、T10 已冻结其 ParseException 分类，不并入。**joinStartAhead() 位置键控消歧**（SelectParser/UpdateParser/DeleteParser 三处守卫，只读前瞻 D2 合规）：修复 T10 缺陷——PG/Oracle 非保留连接词（JOIN/LEFT/RIGHT/INNER/CROSS/OUTER/FULL/NATURAL）在表别名位被吞：裸 `t1 JOIN t2` 失败、`t1 LEFT JOIN t2` 静默错析为 t1(别名 left) INNER JOIN t2；规则=连接词后随连接延续才让位（JOIN/STRAIGHT_JOIN+表因子起点、LEFT/RIGHT/FULL+JOIN|OUTER、INNER/CROSS+JOIN、NATURAL+连接词、OUTER 恒连接语境），后随非延续仍可作裸别名（`t1 left`/`t1 join` 合法）。MySQL DUAL 修正为语法键控非保留（官方表标 R 但 FROM DUAL 专用产式恒允许，作保留字会误拒 `SELECT 1 FROM DUAL`）。Oracle DATE 保留字收紧：无字面量后继不可作裸列名（`SELECT date FROM t`→ParseException；PG/MySQL 非保留行为不变）。已知窄口对拍校准点：OFFSET 后缀省略超集、FETCH PERCENT/WITH TIES 落 ParseException、INSERT ALL 在 MySQL 误报 Unsupported（拒绝事实不变仅异常类型）。corpus oracle/select-v1 38 条 + OracleLexerTest 9 + OracleParserTest 10 + PgParserTest T10 缺陷回归 1 + CorpusOracleSelectV1Test，合计 146 测试全绿。v2.2（2026-11-19）T10 PostgreSQL Profile 冻结：方言数据化三件套——lexer.LexerConfig（词法旋钮）+ KeywordTable 实例化（词集+保留字集，方言装配）+ dialect.DialectProfile（MYSQL/POSTGRESQL 单例；依赖方向 dialect→lexer、parser→dialect 无环）；**Kw.reserved() 移除**——保留字裁决是方言数据非枚举行为（OFFSET 在 MySQL 非保留/PG 保留，T4 内嵌语义多方言下必然失真）；Kw 追加方言段 23 词只增不改主段（MySQL 表=全集−方言段）。PG 词法：ANSI 双引号=QUOTED_IDENTIFIER、E''/B''/X'' 并入 STRING（**E 前缀恒反斜杠转义、标准串不转义**——`'\''` 在两语义下闭合位置不同，前缀级开关落 LexerConfig.escapedStringPrefixes）、`--` 恒注释、无 `#`、嵌套块注释深度计数、无反引号、dollar-quoted 以 `$` 非法字符路径显式拒绝（零新代码）。PG 关键字收录三原则：语法键控词必收、PG 保留字必收、其余非保留词不收（退化标识符无害且 PG 本就允许其作标识符）；MySQL 专有词（STRAIGHT_JOIN/XOR/RLIKE/REGEXP/DIV/MOD/DUPLICATE）不进 PG 表。PG 分页族全入 ast.Limit 既有槽位（契约零改动）；**裸 OFFSET/LIMIT ALL/FETCH 省略计数/LIMIT+FETCH 并用（含经 OFFSET 中转）白名单外**（Limit.rowCount 必填为 v1.4 冻结事实，放宽属契约变更）；LIMIT o,n 在 PG=ParseException（PG 自身即语法错误）。合法 PG 语法前置只读归类 UnsupportedSyntaxException：RETURNING（Parser 尾查）/ON CONFLICT（ON 后 peek(1)）/FULL JOIN（连接位）/LATERAL（表因子位）/EXCEPT/INTERSECT（SELECT 尾清单追加，MySQL 8.0.31+ 同受益）；ILIKE 与正则运算符 `~` 未收录→ParseException（已知窄口，对拍校准）。**UPDATE 语境 SET 位置键控**（PG 非保留字不作裸别名——D9 位置键控哲学延伸）。api.BobaChop.parse(sql, profile) 重载 + diff.BobachopExecutor(profile)；corpus pg/select-v1 33 条 + PgLexerTest 9 + PgParserTest 12，合计 125 测试全绿。v2.1（2026-11-19）T9 bench 套件冻结：无 JMH 手写基准（D6）；冷启动=子 JVM 进程首调（stdout 协议）；首走=收敛剖面（1/2/10/100/1k/10k/20k 次完整曲线不截断）+ 收敛口径 KPI（第 10000 次/稳态 <2x，K 按分层编译 Tier4 阈值物理选定）——§3.1 方差 KPI 的可测化修订；KPI 判定不作构建门禁（性能数字机器相关）；报告 markdown+CSV，ANTLR4 对照取 §1.1 基线量级对照（不同硬件、明确 caveat），同机 live 对拍待参考执行器。首轮实测（JDK21/Linux/8c）：冷启动 26ms（基线 621ms）、简单查询 p50 939ns（~6µs）、IN(1000) 69.5µs（1.2ms）、批量 INSERT1000 356µs=0.35µs/行（7.8ms≈8µs/行）、R²=0.99999994、收敛比 0.96x、首走第 2 次 126µs（ANTLR4 同口径 150~660µs）。v2.0（2026-11-19）T8 Insert/Update/Delete 冻结：语句级 Parser 分派器（共享装配 buffer/exprs/子解析器，分号/EOF 统一）；互递归单一出口——子查询桥与 insert-SELECT 同指 SelectParser.parseQueryBody；括号消歧 peek(1)==SELECT 即判（保留字不可为列名），§7.3 预言的括号扫描 helper 于 MySQL v1 谱系不需要、留待真需要形态（如 PG ON CONFLICT）；VALUES 静态列数校验（列 vs 行、行 vs 行；insert-select 列数依赖 schema 不做静态校验）；ODKU 的 VALUES(col) 在语境内手工构造 FunctionCall、ExprParser 通用接受面不放开；多表与尾部 ORDER BY/LIMIT（MySQL 合法但契约无锚）→ UnsupportedSyntaxException（前置只读检查保证分类）；INSERT…SET/VALUE 同义形态白名单外；UPDATE/DELETE 别名 bare/AS 两形态、INSERT 目标表无别名。v1.9（2026-11-19）T7 SelectParser 冻结：子查询三槽位（EXISTS/标量/IN）经 SubqueryBridge 注入接活；stop 偏移经 TokenBuffer.consumed() 直读（共享游标下表达式结尾语句恒准）；UNION 归属裁定——未括号化操作数禁带 ORDER BY/LIMIT（MySQL 1221 域）、复合级尾锚因契约无字段而白名单外（演进出口=追加可空字段）；LIMIT 实参窄口=完整表达式解析后形态校验（仅数字字面量或 ?）；语句尾拒绝分类用显式清单（FOR/LOCK/WINDOW/NATURAL/INTO/PROCEDURE→Unsupported，含多语句的其余→ParseException）；CROSS/COMMA 无条件契约、CROSS…ON 拒绝；STRAIGHT_JOIN 归入 INNER；派生表别名必填；api.BobaChop 入口 + diff.BobachopExecutor + corpus select-v1 41 条全绿；ANTLR4 参考执行器适配（fork-AST→自有 AST）列为独立跟进项。v1.8（2026-11-19）T6 ExprParser 冻结：优先级表细化 13 级（相对序不变，NOT/^/BETWEEN 按 MySQL 官方序插入）、BETWEEN 的 AND 归属以优先级算术解决、子查询槽位占位拒绝（T7 接活）、受限函数名单 11 个、括号分组透明、ParseException 落地错误分类。v1.7（2026-11-19）T5 TokenBuffer 冻结：peek 越界恒返 EOF 哨兵（不抛异常）、next 在 EOF 幂等、无 undo/mark/reset API——D2 无回溯策略的物理落地。v1.6（2026-11-19）T4 MySQL 词法器冻结：保留字清单按"漏收保留字=错误接受"的不对称方向收录（ANTLR4 对拍为验收器）；`--` 注释须后随空白；注释跳过偏移绝对不断链；N/X/B 引导字符串并入 STRING、`_charset'...'` v1 排除；方言开关常量内嵌待 T10 抽为 DialectProfile。v1.5（2026-11-19）T3 对拍框架冻结：拒绝=观测值（非异常）、双拒=一致（接受面对齐）、偏移随反射字段比较自动纳入且字段序确定可复现、执行器 Throwable 兜底不中断批量。v1.4（2026-11-19）T2 AST 契约 v1 冻结：42 类/接口，五项设计决定经评审确认——语法事实存源文本（含偏移）、字面量不解释值、"恰一非空"以可空字段表达、不可变且不定义 equals/hashCode（深度比较职责在对拍框架）、方言锚点以追加可空字段扩展。v1.3（2026-11-19）方言次序定稿 MySQL→PostgreSQL→Oracle；排期见 ROADMAP.md。v1.2（2026-11-10）方言范围定稿：MySQL 先行，PostgreSQL 与 Oracle 随后（M3/M4）；§7.4 增方言锚点与"语法事实不归一化"约定。v1.1（2026-11-10）定名 BobaChop，确立仓库/Maven/包标识符体系；v1.0（2026-10-30）初版定稿。
