# BobaChop 白名单规格（WHITELIST）

| 项 | 值 |
|---|---|
| 版本 | v1.2（对齐 DESIGN v2.7 / 对拍体系 v2 F4 接受面矩阵 + 裁决台账，2026-11-20 冻结） |
| 身份 | 白名单三件套之规格条目；解析器函数 = 可执行形态；corpus + 黄金用例 = CI 门禁 |
| 调用面 | `BobaChop.parse(sql)` 默认 MySQL；`parse(sql, profile)` 显式方言（MYSQL / POSTGRESQL / ORACLE） |

**演进规则**：新增/演进构造 = 规格条目 + 解析器函数 + 对拍用例三处同一 PR；白名单单调增长，破坏性收缩禁止。本文版本随所对齐的 DESIGN 冻结版本同步递增。

**错误分类学**（三支，绝不产出看似正确的错误树）：

| 异常 | 语义 | 典型来源 |
|---|---|---|
| `UnsupportedSyntaxException` | 合法方言语法、契约无锚或 v1 白名单外（消息含构造名 + 字符偏移） | 前置只读检查 |
| `ParseException` | 语法错误（该方言下本就不合法） | 语法器期望失配 |
| `LexException` | 非法字符 / 未闭合引号或注释 | 词法器 |

---

## 1. 词法层（方言旋钮矩阵）

| 旋钮 | MySQL | PostgreSQL | Oracle |
|---|---|---|---|
| 字符串反斜杠转义 | 开 | 关（标准 conforming） | 关 |
| 双引号 `"` | 字符串 | 包裹标识符（双写转义） | 包裹标识符（双写转义） |
| 反引号 `` ` `` | 包裹标识符 | 非法字符 | 非法字符 |
| `#` 行注释 | 有 | 无（非法字符） | 无（非法字符） |
| `--` 行注释 | 须后随空白/EOL | 恒为注释 | 恒为注释 |
| 块注释 `/* */` | 不嵌套 | 嵌套（深度计数） | 嵌套（深度计数） |
| 引导字符串前缀 | `N''` `X''` `B''` | `E''` `B''` `X''`（E 恒反斜杠转义） | 仅 `N''` |

- 标识符：`[A-Za-z_][A-Za-z0-9_$]*`；包裹标识符恒非关键字（`"select"` 可作标识符）；方言非保留字可进未包裹标识符位（D9），保留字必须包裹
- 数字：十进制 / 小数 / 科学计数 / `0x` 十六进制 / `0b` 二进制，一律保留原文（字面量不解释值，T2 契约）
- 参数标记 `?`，下标按访问序分配（白名单构造内访问序 = 文本序）
- 运算符最长匹配：`<=>` `<=` `>=` `<>` `!=` `->` `->>` `<<` `>>` `||` `&&` 及单字符 `= > < | & + - * / % ^ ! ~`
- 注释跳过但偏移记账不断链（全节点 start/stop 闭区间偏移）

## 2. 表达式层（Pratt 单循环，14 级优先级；F4 起 `||` 按方言裁决）

`OR=1 < XOR=2 < AND=3 < NOT=4 < BETWEEN=5 < 比较/IS/LIKE/IN/REGEXP=6 < 拼接 \|\|=7（PG/Oracle；MySQL 归 OR=1） < |=8 < &=9 < 移位=10 < 加减=11 < 乘除模/DIV/MOD=12 < ^=13 < 一元=14`

**`||` 方言二义**（F4 裁决 L1，`DialectProfile.pipesAsConcat()`）：PG 语法书归"其他运算符"层、Oracle 手册与二元 `+/−` 同层（均高于比较级）→ 两方言 `a || b = c` 为 `(a || b) = c`；MySQL 默认 `\|\| = OR`（`PIPES_AS_CONCAT` 模式白名单外）→ `a || (b = c)`。已知残余偏差见 LEDGER L1（Oracle 混拼接与算术的无括号树、PG 位运算同层问题）。

**支持**：
- 字面量：NUMBER / HEX / BIT / STRING / NULL / TRUE / FALSE / TEMPORAL（`DATE|TIME|TIMESTAMP '...'` 前缀形态，保原文）
- 一元：`-` `+` `!` `~` `NOT`；二元：上表全部中缀
- `BETWEEN x AND y`（AND 归属由优先级算术解决）、`NOT IN/LIKE/BETWEEN`、`IS [NOT] NULL`
- `IN (清单)` 与 `IN (子查询)` 分派；`LIKE ... [ESCAPE 'c']`
- `CASE [operand] WHEN ... THEN ... [ELSE ...] END`
- 限定名链不限级（`a.b.c` 作列引用，qualifier=前缀、name=末段）；裸星 `*` / 限定星 `t.*`
- 函数调用：单名 + 括号（含 `DISTINCT` 形参修饰）
- 子查询三槽位：标量 `(SELECT ...)` / `EXISTS (SELECT ...)` / `IN (SELECT ...)`

**UnsupportedSyntaxException**：行构造器 `(a, b)`；`IS TRUE / IS FALSE`；受限函数名单 11 个——`CAST CONVERT EXTRACT POSITION SUBSTRING TRIM WEIGHT_STRING DEFAULT LEFT RIGHT CHAR`（单名+括号形态与保留字起点形态均拒）；包裹标识符作函数名

**ParseException**：限定函数调用 `db.f(x)`（v1 不收，落语法错误路径）

**深度护栏**（F3 规格条目，DESIGN 版本记录见冻结条）：**同时**嵌套深度 ≤ 256——括号/一元/NOT/CASE/函数实参/IN 清单/BETWEEN 实参（表达式递归）与子查询/派生表/insert-select 源（查询递归）合计计量（两类递归同栈；上限按最坏每级帧成本 × 最小可部署栈预算推导）；超出以 ParseException 拒绝。只限栈递归：迭代路径（长 AND/OR 链、长 IN 清单）不受限，合法产出深树（递归消费者的深树预算为台账观察项）。

## 3. SELECT 白名单

- 投影：`expr [AS] alias`，列位 `AS` 三方言恒允许；别名 = 未包裹标识符 / 包裹标识符 / 方言非保留字
- `DISTINCT` / `ALL` 修饰
- FROM：具名表（限定上限 `db.t`，多级 → Unsupported）；派生表 `(SELECT ...) 别名`（别名必填）；表位 `AS` 见 §6
- JOIN：`INNER JOIN` / `JOIN` / `STRAIGHT_JOIN`（MySQL，归入 INNER）/ `LEFT [OUTER] JOIN` / `RIGHT [OUTER] JOIN` / `CROSS JOIN` / 逗号连接；条件 `ON expr` 或 `USING (列清单)`；CROSS/COMMA 带条件 → Unsupported；连接词与裸别名的歧义以只读前瞻消歧（连接词后随连接延续才让位，否则可作裸别名）
- WHERE / GROUP BY（表达式清单）/ HAVING；`GROUP BY ... WITH ROLLUP` → Unsupported
- ORDER BY `expr [ASC|DESC]`；`NULLS FIRST|LAST`（Oracle 语法）→ Unsupported
- 分页（方言形态，统一收敛 `ast.Limit` 槽位；实参窄口 = 数字字面量或 `?`）：

| 方言 | 形态 |
|---|---|
| MySQL | `LIMIT n` / `LIMIT o, n` / `LIMIT n OFFSET o` |
| PostgreSQL | `LIMIT n [OFFSET m [ROW\|ROWS]]`；`[OFFSET m [ROW\|ROWS]] FETCH {FIRST\|NEXT} n ROW[S] ONLY` |
| Oracle | `OFFSET m ROWS FETCH {FIRST\|NEXT} n ROW[S] ONLY`（复用标准族） |

  白名单外：裸 OFFSET（无 LIMIT/FETCH）、`LIMIT ALL`、FETCH 省略计数（隐含 1）、LIMIT 与 FETCH 并用；`LIMIT o, n` 在 PG/Oracle = ParseException（方言自身语法错误）；LIMIT 在 Oracle 非方言词 → ParseException

  **形态入契约**（F3 契约演进）：offset/rowCount 的文本序随书写形态翻转（逗号形态/标准族 offset 在前，LIMIT 形态 rowCount 在前），参数标记访问序随之翻转——`ast.Limit` 追加可空字段 `form ∈ {LIMIT, COMMA, FETCH}` 记录原书写形态，渲染器按原形态还原（未声明 → 方言默认形态）；规范化改写会静默对调绑定值（BobaPour 数据损坏级），F3 变异模糊暴露。

- UNION 链：`UNION` / `UNION ALL`；未括号化操作数禁带 ORDER BY/LIMIT（MySQL 1221 域，违约 → Unsupported）；复合级尾部 ORDER BY/LIMIT/OFFSET/FETCH → Unsupported
- 语句尾前置归类（Unsupported）：`FOR`（FOR UPDATE 等）/ `LOCK`（LOCK IN SHARE MODE）/ `WINDOW` / `NATURAL` / `INTO` / `PROCEDURE` / `EXCEPT` / `INTERSECT`（MySQL 8.0.31+ 与 PG 同拒）/ `MINUS` / `START` / `CONNECT`（后三者仅 Oracle 词）

## 4. INSERT 白名单

`INSERT [INTO] t [(列清单)] VALUES (行)[,(行)]... | SELECT ...`（目标表无别名，限定上限 `db.t`）

- 列数静态校验：列清单 vs 各行、行 vs 行（insert-select 的列数依赖 schema，不做静态校验）
- MySQL ODKU：`ON DUPLICATE KEY UPDATE 赋值清单`；`VALUES(col)` 在 ODKU 语境内白名单收窄接受（AST 为 FunctionCall，通用接受面不放开）

**UnsupportedSyntaxException**：`INSERT ... SET` / `VALUE` 同义形态 / 括号化查询数据源 `INSERT t (SELECT ...)` / 多表 INSERT `ALL|FIRST`（Oracle）/ `ON CONFLICT`（PG）

**ParseException**：MySQL 修饰符（LOW_PRIORITY / DELAYED / HIGH_PRIORITY / IGNORE / REPLACE）/ `VALUES ROW()` 构造

**RETURNING**（PG 方言锚点，`dmlReturning` 门禁；契约演进追加可空字段 `returning`）：INSERT/UPDATE/DELETE 尾部 `RETURNING 投影清单`——清单与 SELECT 投影项同形（`expr [AS] alias`、`*`、`t.*`）；渲染按清单序还原（别名规范为 AS 引导形态）。RETURNING 词入 PG 保留集与 Oracle 非保留集（F4 裁决 L5）：MySQL 无此词，语句尾"多余 token" ParseException（方言自限）；Oracle DML 尾由子语法器前置归类 Unsupported（合法方言语法 RETURNING INTO、契约待演进），且目标表别名位以只读前瞻消歧（后随 WHERE/SET/EOF/`;` 为别名用法仍收，后随投影/INTO 为子句锚点）；SELECT 语句尾三方言一致 ParseException（DML 专属子句）。

## 5. UPDATE / DELETE 白名单

- `UPDATE t [AS] a SET 列 = expr, ... [WHERE ...] [RETURNING 清单(PG)]`（单表；多表形态与尾部 ORDER BY/LIMIT → Unsupported；`:=` 赋值形态 → ParseException）
- `DELETE FROM t [AS] a [WHERE ...] [RETURNING 清单(PG)]`（单表；多表/`USING` 形态与尾部 ORDER BY/LIMIT → Unsupported）
- PG 语境 `SET` 位置键控：非保留字但恒为子句分隔词，不作裸别名

## 6. 方言开关矩阵（DialectProfile）

| 开关 | MySQL | PostgreSQL | Oracle |
|---|---|---|---|
| `limitCommaOffset`（LIMIT o, n） | ✓ | ✗ ParseException | ✗ ParseException |
| `fetchFirstPagination`（OFFSET/FETCH 标准族） | ✗ | ✓ | ✓ |
| `tableAliasAs`（表位/派生表位 AS 引导） | ✓ | ✓ | ✗ ParseException（列位 AS 恒允许） |
| `multiTableInsert`（INSERT ALL/FIRST 前置归类） | ✗ | ✗ | ✓ |
| `dmlReturning`（DML 尾部 RETURNING 清单） | ✗ ParseException | ✓ | ✗ Unsupported（RETURNING INTO 前置归类，词入非保留表） |
| `pipesAsConcat`（`\|\|` 拼接高于比较级） | ✗（\|\| = OR） | ✓ | ✓ |

关键字表装配：MySQL = Kw 主段全集 − 方言追加段（PG/Oracle 专有词在 MySQL 为普通标识符）；PG / Oracle = 保留字集 ∪ 语法键控非保留字集。子句锚点词（PG 的 OFFSET/FETCH、Oracle 的 OFFSET/FETCH/USING）入保留集——表别名位必须让位给子句。

## 7. 前置归类 Unsupported 总表（合法方言语法、契约无锚）

| 构造 | 触发位置 | 方言 |
|---|---|---|
| CTE（WITH 子句） | 语句分派位 | 三方言 |
| ON CONFLICT upsert | INSERT 的 ON 后 | PG |
| FULL JOIN / FULL OUTER JOIN | 连接位 | PG / Oracle |
| LATERAL 派生表 | 表因子位 | PG |
| EXCEPT / INTERSECT | SELECT 尾部 | 三方言（词在表内即触发） |
| MINUS / START WITH / CONNECT BY（层级查询族） | SELECT 尾部 | Oracle |
| NULLS FIRST / LAST | ORDER BY 项后 | Oracle |
| ROWNUM / LEVEL / PRIOR / SYSDATE / UID（伪列与系统值族） | 表达式起点 | Oracle |
| MERGE 语句 | 语句分派位 | Oracle |
| 多表 INSERT（ALL / FIRST） | INSERT 后首 token | Oracle（MySQL 误归类，见 §8） |
| 多表 UPDATE / DELETE | 目标表后 | 三方言 |
| UPDATE/DELETE 尾部 ORDER BY/LIMIT | 语句尾 | 三方言 |
| UNION 复合级尾部与操作数自带分页排序 | 复合层 | 三方言 |
| 括号化表引用（嵌套 JOIN） | 表因子位 | 三方言 |
| 多级限定表名（上限 db.t） | 表名解析 | 三方言 |
| 分页族窄口（裸 OFFSET / LIMIT ALL / 并用 / 省略计数） | 分页解析 | 按方言形态 |
| RETURNING INTO 子句 | DML 语句尾（SELECT 尾恒 ParseException） | Oracle（F4 裁决 L5） |
| `FETCH FIRST n PERCENT`（按百分比取数） | FETCH 计数后 | Oracle（PERCENT 词表自限，仅 Oracle 表收录） |
| `FETCH ... WITH TIES`（并列行取回） | FETCH 的 ROW/ROWS 后 | PG / Oracle（合法方言语法，契约无锚） |

## 8. 已知超集与窄口（对拍校准点；F4 起由 LEDGER.md 裁决台账逐条承接，本节为规格侧摘要）

接受面**超集**（本内核接受、个别方言自身拒绝；AST 存原文不做跨方言语义归一化，D8）：
- 运算符表为三方言并集：`<=>` `&&` `!` `^` `%` 等在部分方言本无（如 Oracle 无 `%` 取模算子、PG 无 `<=>`）
- OFFSET 计数后 ROW/ROWS 后缀省略：PG 合法形态在 Oracle 语境内同为超集
- MySQL DUAL 为语法键控非保留：`SELECT dual FROM t`（列名）亦解析，MySQL 本拒
- 列限定名链不限级（`a.b.c.d`），schema 语义由集成方校验

窄口（理想分类与现状的差异，对拍接入后校准；裁决记录见 LEDGER.md）：
- 多表 INSERT 在 MySQL 方言误报 UnsupportedSyntaxException（本应 ParseException；拒绝事实不变，仅异常类型）
- `USER` 作裸表达式（Oracle）→ ParseException（同词为 PG 保留字，T10 已冻结该分类，未并入伪列 Unsupported 族）
- q-字符串 `q'[...]'` 与绑定变量 `:1` / `:name` → 词法/语法错误路径拒绝
- `(+)` 外连接算符（Oracle）→ ParseException
- ILIKE 与正则运算符 `~` / `!~`（PG）未收录 → ParseException

## 9. CI 门禁对应（三件套第三腿）

| 层 | 语料门禁 | 黄金用例 |
|---|---|---|
| 词法 | — | LexerTest / PgLexerTest / OracleLexerTest |
| 表达式 | — | ExprParserTest |
| SELECT | CorpusSelectV1Test / CorpusPgSelectV1Test / CorpusOracleSelectV1Test | SelectParserTest / PgParserTest / OracleParserTest |
| DML | CorpusDmlV1Test | InsertParserTest / UpdateDeleteParserTest |
| 接受面 | acceptance-matrix.tsv 快照（F4） | AcceptanceMatrixTest / DialectAdjudicationTest |
| 规格本身 | WhitelistDocTest（本文存在且版本与 DESIGN 版本记录对齐） | — |
