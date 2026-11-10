# BobaChop

手写高性能 SQL 解析内核：递归下降 + Pratt 表达式，自有冻结 AST，零第三方运行时依赖（Java 8+）。

面向 ANTLR4（ALL(\*) 惰性 DFA）在高频解析场景的性能问题（冷启动、预热曲线、稳态单价）的预付型替代方案：冷热一致、无预热曲线、白名单外一律显式拒绝，绝不产出错误树。

- 设计规格（单一事实源）：[DESIGN.md](DESIGN.md)
- 模块规划：`boba-chop-core` / `boba-chop-diff`（对拍框架）/ `boba-chop-bench` / `corpus/`
- 状态：设计定稿（DESIGN v1.1），M0 未开工

配套项目：[BobaPour](https://github.com/susongyan/bobapour) — 基于 BobaChop 的分库分表 + 透明加密中间件。
