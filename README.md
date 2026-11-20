# BobaChop

手写高性能 SQL 解析内核：递归下降 + Pratt 表达式，自有冻结 AST，零第三方运行时依赖（Java 8+）。

面向 ANTLR4（ALL(\*) 惰性 DFA）在高频解析场景的性能问题（冷启动、预热曲线、稳态单价）的预付型替代方案：冷热一致、无预热曲线、白名单外一律显式拒绝，绝不产出错误树。

- 设计规格（单一事实源）：[DESIGN.md](DESIGN.md)
- 支持面规格（白名单三件套）：[WHITELIST.md](WHITELIST.md)
- 模块规划：`boba-chop-core` / `boba-chop-diff`（对拍框架）/ `boba-chop-bench` / `corpus/`
- 状态：M0–M4 完成 + 对拍体系 v2 F1–F3（DESIGN v2.6，2026-11-20）——三方言 DML 白名单（含 PG RETURNING）+ 偏移不变量门禁 + 生成往返对拍 + 变异模糊（三支异常封闭性 / 深度护栏 / 分页形态参数序保真）

配套项目：[BobaPour](https://github.com/susongyan/bobapour) — 基于 BobaChop 的分库分表 + 透明加密中间件。
