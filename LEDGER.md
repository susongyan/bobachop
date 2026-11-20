# BobaChop 裁决台账（LEDGER）

| 项 | 值 |
|---|---|
| 版本 | v1.0（对拍体系 v2 F4 建立，2026-11-20） |
| 身份 | 接受面争议点的活台账：每条含真值来源（服务器语法书/保留字表）、裁决、落地锚 |
| 变更规则 | 接受面矩阵（`boba-chop-diff/src/test/resources/acceptance-matrix.tsv`）任何一格翻转，或新增/改判争议点，须在本台账增改条目——裁决改一次，`DialectAdjudicationTest`/`AcceptanceMatrixTest` 红一次 |
| 真值分工 | D7：权威来自独立性机制——MySQL `sql_yacc.yy` / PG `gram.y` / Oracle SQL Language Reference（含保留字表与运算符优先级表）；外部开源语法不当验收器 |

---

## L1 · `||` 的方言二义 → 修复（PREC_CONCAT 插层，13 级 → 14 级）

- **争议**：同一段文本 `a || b = c`，三方言两棵树。原实现 `||` 恒为 OR 级（1），PG/Oracle 树错。
- **真值**：PG 语法书优先级表——`||` 归"任何其他运算符"层，**高于比较、低于加减**；Oracle 运算符优先级表——`||` 与二元 `+`/`-` 同层（高于比较）；MySQL——默认 `|| = OR`（逻辑或，最低层；`PIPES_AS_CONCAT` sql_mode 不在白名单）。
- **裁决**：新增 `DialectProfile.pipesAsConcat()`（MySQL 默认 false，PG/Oracle true）；`ExprParser` 插 `PREC_CONCAT = 7`（原 ≥7 各级 +1 重编号，一元升至 14）；`AstRenderer` 优先级表独立复述同步（`binaryPrecedence` 转实例方法，随 profile 裁决 `||`）。
- **已知残余偏差**（观察项，D8 运算符表为三方言并集的既定代价）：
  - Oracle 手册 `||` 与 `+` **同层**（左结合 `(a || b) + c`）；本内核取 PG 层（`a || (b + c)`）——混拼接与算术的无括号语句树有差，实践应括号化；
  - PG 的 `|` `&` `<<` 与 `||` 同属"其他运算符"一层；本内核沿用 MySQL 梯度分层（8/9/10 级）。
- **锚**：`DialectAdjudicationTest` 五用例（PG/Oracle/MySQL 三树 + PG 算术次序 + 左结合）；`RoundTripTest` INFIX 池含 `||` 自动压新层。

## L2 · Oracle CASE/WHEN/END/TRUE/FALSE 词表对照 → 维持

- **争议**：五词在 MySQL 词表内为保留字（MySQL 8.0 保留），Oracle 对照后分类是否要改。
- **真值**：Oracle 保留字表（SQL Language Reference）——CASE/WHEN/END 非保留；TRUE/FALSE 自 11g 起非保留（PL/SQL 内保留不涉 SQL 解析面）。
- **裁决**：维持 `ORACLE_NON_RESERVED` 收录（CASE/CROSS/END/ESCAPE/FALSE/FIRST/…/TRUE/WHEN），语法位置键控（CASE 表达式起点仍按 CASE 产式走，作标识符时让位）。
- **锚**：接受面矩阵快照记录五词四槽分类；矩阵卫生规则（非保留不作 Unsupported）覆盖。

## L3 · 裸 HAVING（无 GROUP BY）→ 维持接受（超集）

- **争议**：HAVING 无 GROUP BY 是否前置拒绝。
- **真值**：MySQL `sql_yacc.yy`——`opt_having` 为独立可选产式，不依附 GROUP BY；PG `gram.y`——`having_clause` 独立可选；Oracle——无 GROUP BY 时 HAVING 视作全局单组（语义合法）。
- **裁决**：维持接受（三方言服务器均收；白名单单调不收窄）。
- **锚**：`corpus/select-v1` 等语料 + F3 HAVING 独立渲染回归。

## L4 · REGEXP/RLIKE/XOR/DIV/MOD 方言门禁 → 维持（词表自限）

- **争议**：五词 PG/Oracle 语境是否需要显式开关。
- **真值**：五词仅 MySQL 8.0 关键字表收录；PG/Oracle 关键字表无。
- **裁决**：维持零分支词表自限——PG/Oracle 语句中五词退化为普通标识符，落"期望失配" ParseException（该方言下本就不合法），与服务器同拒。
- **锚**：`DialectAdjudicationTest.mySqlRegexpFamilyAccepted` / `regexpFamilyGatedByWordTableInPostgres` / `regexpFamilyGatedByWordTableInOracle`（回归锚；此前无测试钉住）。

## L5 · Oracle RETURNING INTO 分类 → 修复（ParseException → UnsupportedSyntaxException）

- **争议**：`RETURNING` 原仅 PG 表收录，Oracle 语句尾落"多余 token" ParseException；但 RETURNING INTO 是 Oracle 合法方言语法，按三支分类学应归"契约无锚"的 Unsupported。
- **真值**：Oracle SQL Language Reference——`returning_clause` 为 DML 合法子句；Oracle 保留字表——RETURNING **非保留**（可作标识符）。
- **裁决**：`Kw.RETURNING` 入 `ORACLE_NON_RESERVED`；三 DML 子语法器尾部前置归类 Unsupported（消息"RETURNING INTO 子句（Oracle，契约待演进）"）；**别名位只读前瞻**（`returningClauseAhead`：后随 WHERE/SET/EOF/`;` 判别名用法与服务器同收，后随投影/INTO 判子句锚点让位）——生成器把非保留词采样为 DML 裸别名，暴露了纯位置键控会误杀 `DELETE FROM t returning WHERE ...` 的合法别名用法。
- **边界**：MySQL 无此词不变（标识符 → ParseException）；PG SELECT 语句尾不变（ParseException，三方言一致）。
- **锚**：`DialectAdjudicationTest.oracleReturningIntoPreclassified` 等四用例；`RoundTripTest` 生成器采样压前瞻。

## L6 · FETCH PERCENT / WITH TIES 分类 → 修复（ParseException → UnsupportedSyntaxException）

- **争议**：两形态原落"期望 ROW/ROWS"/"多余 token" ParseException，但均为合法方言语法。
- **真值**：Oracle 12c+ `row_limiting_clause`（`FETCH FIRST n PERCENT ROWS`、`WITH TIES` 并列行取回）；PG 13+ `FETCH ... WITH TIES`。
- **裁决**：`Kw.PERCENT`/`Kw.TIES` 追加 Oracle 段（非保留，入 `ORACLE_NON_RESERVED`）；`parsePgOffsetFetch` 计数后 PERCENT、ROW/ROWS 后 WITH 两处前置归类 Unsupported。PERCENT 词表自限（仅 Oracle 表收录——PG 同文本落 ParseException，与 PG 服务器一致）；WITH 三方言表内，检查不设方言门（PG/Oracle 均合法语法、契约无锚，均 Unsupported；MySQL FETCH 族不可达）。
- **锚**：`DialectAdjudicationTest` 四用例（含 `SELECT percent/ties FROM t` 标识符用法保留）。

## L7 · OFFSET 计数后 ROW/ROWS 后缀省略 → 维持（超集）

- **真值**：PG `gram.y`——offset 后缀可省；Oracle 手册——`OFFSET n {ROW|ROWS}` 后缀必带。
- **裁决**：维持接受（PG 合法形态在 Oracle 语境内为超集，AST 存语法事实不归一，D8）。
- **锚**：WHITELIST §8 校准点条目（本台账成立后由本条承接）。

## L8 · ILIKE 与 `~` / `!~`（PG）→ 白名单外维持

- **真值**：PG 语法书——ILIKE 与正则运算符为 PG 专有表达式形态。
- **裁决**：维持 ParseException（`~` 词法上落入运算符并集但语法器无结合力条目；ILIKE 词表外为标识符），契约演进前不收。
- **锚**：WHITELIST §8 窄口条目（本条承接）。

---

## 附 · 矩阵卫生规则（AcceptanceMatrixTest 契约断言）

- 保留字在裸别名 / AS 别名 / 表名三个纯标识符槽位**不得 ACCEPT**（D9）；
- 保留字在表达式首 ACCEPT 仅当消费为**字面量**（NULL/TRUE/FALSE 与服务器同构）——被当列引用即违约；
- 非保留字**不得 UNSUPPORTED**（位置歧义词允许 PARSE——与服务器同拒，如 Oracle `SELECT case FROM t`）；
- 任何探针**不得 LEX**（探针文本恒为合法词法）。

首跑产出（2026-11-20，快照 v1）：2000 探针 = ACCEPT 228 / UNSUPPORTED 26 / PARSE 1746；26 格 UNSUPPORTED 与 WHITELIST §3/§7 前置归类总表逐格核销——语句尾前置归类词 bare-alias 位 19 格（MySQL 7 / PG 5 / Oracle 7）、LATERAL 表位 2 格（MySQL/PG）、Oracle 伪列族表达式位 5 格（LEVEL/PRIOR/ROWNUM/SYSDATE/UID）；卫生断言零违约。
