# BobaChop Roadmap

启动 2026-11-19。排期基准：1 人 + AI agent 辅助，日均 3–4h 有效投入（投入变化时工期近似线性缩放）。
**里程碑完成以 DESIGN.md 验收标准为准，日期仅为管理参考。** 每任务合入即更新本表状态。

| 里程碑 | 内容 | 任务 | 参考工期 | 时段（参考） | 状态 |
|---|---|---|---|---|---|
| M0 | 工程骨架 + AST 契约冻结 + 对拍框架空跑 | T1–T3 | 1–1.5 周 | 11 月末 | ✅ 完成（T1–T3 冻结，2026-11-19） |
| M1 | MySQL lexer + TokenBuffer + Pratt + SELECT 主干 | T4–T7 | 2.5–3.5 周 | 12 月 | ✅ 完成（T4–T7 冻结，2026-11-19；ANTLR4 参考对拍适配为跟进项） |
| M2 | MySQL INSERT 全家 + UPDATE/DELETE | T8–T9 | 1.5–2.5 周 | 12 月末–1 月初 | ✅ 完成（T8–T9 冻结，2026-11-19；bench 首轮 KPI 达标，同机对拍随参考执行器跟进） |
| M3 | PostgreSQL profile + 白名单构造 | T10 | 1.5–2 周 | 1 月中 | ✅ 完成（T10 冻结，2026-11-19，DESIGN v2.2） |
| M4 | Oracle profile + 白名单构造 | T11 | 1.5–2 周 | 1 月末 | ✅ 完成（T11 冻结，2026-11-19，DESIGN v2.3） |

对外依赖：无。下游约定：boba-pour 的 M0 依赖本仓 T2（AST 冻结）、M2 依赖本仓 M1。

跟进项（对拍体系 v2，DESIGN v2.4 定稿——真值分工见 D7：外部引擎不当验收器）：

- [x] WHITELIST.md 三件套显式化（2026-11-19，v1.0 对齐 DESIGN v2.3：支持面/开关矩阵/前置归类总表/超集窄口校准点 + WhitelistDocTest 版本对齐门禁）
- [x] F1 偏移不变量校验器（2026-11-20，DESIGN v2.4：SpanWalker/SpanInvariants 六条 + 三门禁；真值=输入串+独立词法标尺；抓出并修复 SelectSource span 倒挂 D5 缺陷；三方言全语料 138 条全绿）
- [ ] F2 规格生成器 + 渲染器 + 往返对拍（AST 先行生成→渲染→重解析判等；生成器以 DialectProfile 参数化、标识符从方言非保留字表采样专打别名位；canonical printer 为 boba-pour 改写付首付）
- [ ] F3 变异模糊 + 元性质（token 变异：三支异常封闭性 / parse∘render 幂等 / 结果确定性；预声明：深度护栏大概率浮出为新规格条目）
- [ ] F4 接受面矩阵 + 裁决台账（关键字×槽位枚举三分类矩阵 + sql_yacc.yy/gram.y/Oracle 手册人工裁决；§8 校准点升级为活台账）
- [ ] F5 规格语法 + 同机 KPI（WHITELIST 写成 ANTLR4 规格语法仅进 bench 当陪练；同语言 ≥5x 冷热双态实测）

任务状态：

- [x] T1 工程骨架（Maven 多模块 + CI + checkstyle + JDK8 基线，mvn install 全绿，2026-11-19）
- [x] T2 AST 契约 v1（42 类，冻结 2026-11-19，DESIGN v1.4）
- [x] T3 对拍框架（冻结 2026-11-19，DESIGN v1.5；6 类 + 15 用例）
- [x] T4 TokenType/Kw/KeywordTable + Lexer（冻结 2026-11-19，DESIGN v1.6；14 黄金用例）
- [x] T5 TokenBuffer（冻结 2026-11-19，DESIGN v1.7；7 用例）
- [x] T6 ExprParser（冻结 2026-11-19，DESIGN v1.8；Pratt 13 级优先级 + BETWEEN/NOT/IS/LIKE/IN/CASE/限定名/函数调用特判，19 黄金用例；子查询槽位占位由 T7 接活）
- [x] T7 SelectParser 主干（冻结 2026-11-19，DESIGN v1.9；api.BobaChop 入口 + BobachopExecutor + corpus select-v1 41 条 + 21 黄金用例；ANTLR4 参考执行器适配为独立跟进项）
- [x] T8 InsertParser/UpdateParser/DeleteParser（冻结 2026-11-19，DESIGN v2.0；语句级 Parser 分派器 + INSERT/UPDATE/DELETE 白名单 + corpus dml-v1 26 条 + 19 黄金用例）
- [x] T9 bench 套件（冻结 2026-11-19，DESIGN v2.1；四场景 + 收敛口径 KPI 可测化，首轮实测达标）
- [x] T10 PostgreSQL Profile + 构造（冻结 2026-11-19，DESIGN v2.2；方言数据化三件套 LexerConfig/KeywordTable/DialectProfile + PG 词法与分页族 + 白名单前置归类 + corpus pg/select-v1 33 条 + 21 黄金用例）
- [x] T11 Oracle Profile + 构造（冻结 2026-11-19，DESIGN v2.3；OracleDialect + Kw Oracle 段 47 词 + 标准分页族复用（ast.Limit 槽位零改动）+ tableAliasAs 开关 + 伪列/层级/MERGE/多表 INSERT 前置归类 + joinStartAhead 别名消歧（修复 T10 连接词吞别名静默错树缺陷）+ MySQL DUAL 修正 + corpus oracle/select-v1 38 条 + 20 黄金用例，146 测试全绿）
