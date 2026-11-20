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
- [x] F2 规格生成器 + 渲染器 + 往返对拍（2026-11-20，DESIGN v2.5：AstGenerator/AstRenderer/RoundTripTest 三断言；生成序=渲染序=参数访问序；抓出并修复内核缺陷 ×3——PG 词表 EXISTS 错归非保留、INSERT INTO first 误判多表、INSERT…SELECT 的 ODKU 尾缀被 CROSS 抢占；3 种子 5000×3 + 50000×3 ≈19.5 万条全绿）
- [x] F3 变异模糊 + 元性质（2026-11-20，DESIGN v2.6：Mutator token 级五操作 + 封闭性/确定性/往返/偏移不变量两门禁 + 深度护栏新规格条目（同时 ≤256 共享预算，预声明兑现）；抓出并修复缺陷 ×4——分页形态参数序静默对调（数据损坏级，Limit.form 契约演进）/REGEXP 渲染漏收/R6 限定名公式校准/HAVING 独立渲染；同窗口冻结 PG RETURNING 契约演进（三 DML 语句追加 returning 字段，投影项同形零新节点，dmlReturning 开关 + 词表自限门禁 + corpus pg/dml-v1 22 条）；177 测试全绿，模糊多种子扫量全绿）
- [x] F4 接受面矩阵 + 裁决台账（2026-11-20，DESIGN v2.7：AcceptanceMatrixTest——方言词表全词 × 四标识符槽位 2000 针快照门禁（快照即门禁）+ 契约卫生断言，26 格 UNSUPPORTED 与前置归类总表逐格核销；LEDGER.md v1.0 裁决台账（L1–L8，真值=服务器语法书）承接 §8 校准点；挂账六项全清——修复 || 方言优先级（pipesAsConcat 开关 + PREC_CONCAT=7，梯度 13→14 级，解析/渲染两侧独立同步）/Oracle RETURNING INTO 与 FETCH PERCENT/WITH TIES 分类（词表自限 + DML 尾前置归类 + 别名位只读前瞻，生成器采样撞出纯位置键控盲点），维持 Oracle CASE 族/裸 HAVING/REGEXP 族（补回归锚 DialectAdjudicationTest 17 用例）；196 测试全绿（core 150/diff 40/bench 6），模糊 3 种子 ×8000 变异体全绿）
- [x] F5 规格语法 + 同机 KPI（2026-11-20，DESIGN v2.8：BobaChopSpec.g4 并集规格语法（bench 陪练位，按 WHITELIST 独立翻译；容差 T1/T2/T3 单向声明+字符串转义两义双规则；UNION 尾锚结构化双向拒）+ SpecGrammarDifferentialTest 方向性对拍（160 条语料 + 2000 针矩阵重放差异 ≡ 0 + 28 负例双向拒 + 10 容差单向锁定）+ 同机 KPI 实测（稳态 p99 最差形态 UPDATE 5.5x ≥5x 达标、全形态 5.5–18.1x；冷启动 21.25ms vs 82.83ms = 3.9x；收敛 1.17x；R²=1.0000）；开发期对拍抓出语法侧缺陷 ×6；201 测试全绿——**对拍体系 v2 F1–F5 齐装**）

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
