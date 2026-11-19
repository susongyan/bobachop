# BobaChop Roadmap

启动 2026-11-19。排期基准：1 人 + AI agent 辅助，日均 3–4h 有效投入（投入变化时工期近似线性缩放）。
**里程碑完成以 DESIGN.md 验收标准为准，日期仅为管理参考。** 每任务合入即更新本表状态。

| 里程碑 | 内容 | 任务 | 参考工期 | 时段（参考） | 状态 |
|---|---|---|---|---|---|
| M0 | 工程骨架 + AST 契约冻结 + 对拍框架空跑 | T1–T3 | 1–1.5 周 | 11 月末 | ✅ 完成（T1–T3 冻结，2026-11-19） |
| M1 | MySQL lexer + TokenBuffer + Pratt + SELECT 主干 | T4–T7 | 2.5–3.5 周 | 12 月 | 🔄 T4 已提交待评审 |
| M2 | MySQL INSERT 全家 + UPDATE/DELETE | T8–T9 | 1.5–2.5 周 | 12 月末–1 月初 | 未开始 |
| M3 | PostgreSQL profile + 白名单构造 | T10 | 1.5–2 周 | 1 月中 | 未开始 |
| M4 | Oracle profile + 白名单构造 | T11 | 1.5–2 周 | 1 月末 | 未开始 |

对外依赖：无。下游约定：boba-pour 的 M0 依赖本仓 T2（AST 冻结）、M2 依赖本仓 M1。

任务状态：

- [x] T1 工程骨架（Maven 多模块 + CI + checkstyle + JDK8 基线，mvn install 全绿，2026-11-19）
- [x] T2 AST 契约 v1（42 类，冻结 2026-11-19，DESIGN v1.4）
- [x] T3 对拍框架（冻结 2026-11-19，DESIGN v1.5；6 类 + 15 用例）
- [ ] T4 TokenType/Kw/KeywordTable + Lexer（已提交待评审：lexer 5 类 + exception 2 类，14 黄金用例含全部 DoD 陷阱）
- [ ] T5 TokenBuffer
- [ ] T6 ExprParser（Pratt + 特判）
- [ ] T7 SelectParser 主干
- [ ] T8 InsertParser/UpdateParser/DeleteParser
- [ ] T9 bench 套件
- [ ] T10 PostgreSQL Profile + 构造
- [ ] T11 Oracle Profile + 构造
