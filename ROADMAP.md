# BobaChop Roadmap

启动 2026-11-19。排期基准：1 人 + AI agent 辅助，日均 3–4h 有效投入（投入变化时工期近似线性缩放）。
**里程碑完成以 DESIGN.md 验收标准为准，日期仅为管理参考。** 每任务合入即更新本表状态。

| 里程碑 | 内容 | 任务 | 参考工期 | 时段（参考） | 状态 |
|---|---|---|---|---|---|
| M0 | 工程骨架 + AST 契约冻结 + 对拍框架空跑 | T1–T3 | 1–1.5 周 | 11 月末 | ✅ 完成（T1–T3 冻结，2026-11-19） |
| M1 | MySQL lexer + TokenBuffer + Pratt + SELECT 主干 | T4–T7 | 2.5–3.5 周 | 12 月 | ✅ 完成（T4–T7 冻结，2026-11-19；ANTLR4 参考对拍适配为跟进项） |
| M2 | MySQL INSERT 全家 + UPDATE/DELETE | T8–T9 | 1.5–2.5 周 | 12 月末–1 月初 | 🔄 T8 冻结，T9 进行中 |
| M3 | PostgreSQL profile + 白名单构造 | T10 | 1.5–2 周 | 1 月中 | 未开始 |
| M4 | Oracle profile + 白名单构造 | T11 | 1.5–2 周 | 1 月末 | 未开始 |

对外依赖：无。下游约定：boba-pour 的 M0 依赖本仓 T2（AST 冻结）、M2 依赖本仓 M1。

任务状态：

- [x] T1 工程骨架（Maven 多模块 + CI + checkstyle + JDK8 基线，mvn install 全绿，2026-11-19）
- [x] T2 AST 契约 v1（42 类，冻结 2026-11-19，DESIGN v1.4）
- [x] T3 对拍框架（冻结 2026-11-19，DESIGN v1.5；6 类 + 15 用例）
- [x] T4 TokenType/Kw/KeywordTable + Lexer（冻结 2026-11-19，DESIGN v1.6；14 黄金用例）
- [x] T5 TokenBuffer（冻结 2026-11-19，DESIGN v1.7；7 用例）
- [x] T6 ExprParser（冻结 2026-11-19，DESIGN v1.8；Pratt 13 级优先级 + BETWEEN/NOT/IS/LIKE/IN/CASE/限定名/函数调用特判，19 黄金用例；子查询槽位占位由 T7 接活）
- [x] T7 SelectParser 主干（冻结 2026-11-19，DESIGN v1.9；api.BobaChop 入口 + BobachopExecutor + corpus select-v1 41 条 + 21 黄金用例；ANTLR4 参考执行器适配为独立跟进项）
- [x] T8 InsertParser/UpdateParser/DeleteParser（冻结 2026-11-19，DESIGN v2.0；语句级 Parser 分派器 + INSERT/UPDATE/DELETE 白名单 + corpus dml-v1 26 条 + 19 黄金用例）
- [ ] T9 bench 套件（已提交待评审：四场景手写基准（无 JMH，D6）——冷启动（子 JVM 进程首调）/首走（收敛剖面 + 收敛口径 KPI）/稳态（8 形态 p50/p99/吞吐）/线性度（最小二乘 R²）；报告 markdown+CSV；§3.1 方差 KPI 可测化为"第10000次/稳态 <2x"（K 按分层编译 Tier4 阈值物理选定）。首轮实测：冷启动 26ms（ANTLR4 基线 621ms）、简单查询 p50 939ns（~6µs）、IN(1000) 69.5µs（1.2ms）、批量 INSERT1000 356µs=0.35µs/行（7.8ms≈8µs/行）、R²=0.99999994、收敛比 0.96x）
- [ ] T10 PostgreSQL Profile + 构造
- [ ] T11 Oracle Profile + 构造
