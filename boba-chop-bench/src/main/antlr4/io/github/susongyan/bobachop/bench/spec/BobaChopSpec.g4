/*
 * BobaChop 规格语法（对拍体系 v2 F5）——WHITELIST v1.2 的 ANTLR4 可执行化。
 *
 * 定位（D7）：本语法是"树形真值 = WHITELIST 规格可执行化"的语法半壁（F2 生成器是另一半），
 * 与手写内核是同一规格的两种独立机制；性能上只当同机陪练，不当验收器。
 *
 * 编写纪律（与 AstRenderer 优先级表同一"独立复述"纪律）：
 *  - 按 WHITELIST §1–§5 逐节翻译，不参考任何外部语法文件；
 *  - 接受面 ⊇ 三方言白名单并集的语句面；关键字大小写不敏感（内核 lookup 归一小写，对齐）；
 *  - 表达式 14 级分层递归（§2 逐级对应，级名即规则名），|| 取 PG/Oracle 拼接位
 *    （接受面与 MySQL 无差，树形差异由 F4 裁决 L1 台账管辖，语法不比树）；
 *  - 语句级白名单外形态（CTE/FOR/WINDOW/多表 DML/WITH ROLLUP/裸 OFFSET/LIMIT ALL/
 *    FETCH 省略计数/LIMIT+FETCH 并用/PERCENT/WITH TIES/UNION 操作数自带分页排序/
 *    复合级尾部锚/RETURNING INTO）一律不进产生式——与内核 Unsupported 双向拒。
 *
 * 已知容差（并集接受面在方言窄口处宽于内核的方向性缺口；对拍报告单列，改判须记 LEDGER）：
 *  T1 方言词表/形态并集：REGEXP/RLIKE/XOR/DIV/MOD/&&、STRAIGHT_JOIN、E/B/X 前缀串、
 *     反引号与 # 注释、LIMIT o,n 形态——在 PG/Oracle 语句中被本语法接受（内核按词表/
 *     形态开关自限拒绝）；
 *  T2 词级粒度：受限函数 11 名（无 AS 形态）/包裹标识符作函数名/VALUES(col) 于 ODKU
 *     语境外/Oracle 伪列族（ROWNUM 等以普通标识符身份被收）/表位 AS（Oracle 语句）/
 *     CROSS·COMMA 带连接条件/别名位 41 词三方言通用——本语法按并集接受，内核按方言
 *     与位置键控分类；
 *  T3 词法旋钮并集：`--` 恒注释（MySQL 无空白后随时内核按双负号算术，两者接受面同、
 *     树不同）、块注释嵌套（MySQL 不嵌套）、字符串转义两义（转义串/标准串双规则按
 *     最长匹配裁定——病理残余：标准串以 \' 收尾且同语句后随引号的形态误拒，语料无此组合）。
 *     并集接受面 ⊇ 各方言白名单，白名单内语句不受影响。
 *
 * 别名位可用关键字 = 接受面矩阵（acceptance-matrix.tsv）三标识符槽位 ACCEPT 词集
 * （41 词）——矩阵翻转时本清单须同步（快照门禁 → 语法 → LEDGER 三处一致）。
 */
grammar BobaChopSpec;

options { caseInsensitive = true; }   // 内核 KeywordTable.lookup 归一小写，对齐

// ---------- 语句 ----------

root
    : statement EOF
    ;

statement
    : selectStatement
    | insertStatement
    | updateStatement
    | deleteStatement
    ;

// SELECT（§3）：单查询可带排序/分页；UNION 链的操作数（未括号化）不带（MySQL 1221 域），
// 复合级尾部无排序/分页/分页族锚——两处白名单外形态在本结构下不可达（与内核双向拒）
selectStatement
    : selectCore orderByClause? limitClause?
    | queryOperand (UNION ALL? queryOperand)+
    ;

queryOperand
    : selectCore
    | '(' selectStatement ')'                 // 括号化操作数内部为完整查询（可带尾锚）
    ;

selectCore
    : SELECT (DISTINCT | ALL)?
      selectItem (',' selectItem)*
      fromClause?
      whereClause?
      groupByClause?
      havingClause?
    ;

selectItem
    : STAR
    | qualifiedName '.' STAR                 // 裸星 / 限定星（§2）
    | expr (AS? alias)?
    ;

fromClause
    : FROM tableRef (',' tableRef | joinClause)*
    ;

// 条件连接：JOIN/INNER/LEFT [OUTER]/RIGHT [OUTER]/CROSS/STRAIGHT_JOIN（MySQL 独立连接词，
// 归 INNER）；条件 ON expr 或 USING (列清单)（CROSS 带条件 → 内核 Unsupported，本语法
// 并集收 = T2）；NATURAL 不收（白名单外）；FULL 不收（前置归类，白名单外）
joinClause
    : (STRAIGHT_JOIN
       | (INNER | LEFT OUTER? | RIGHT OUTER? | CROSS)? JOIN)
      tableRef (ON expr | USING '(' idList ')')?
    ;

tableRef
    : tableName alias?
    | '(' selectStatement ')' alias           // 派生表别名必填（§3）
    ;

// 表名上限 db.t（多级 → 内核 Unsupported，双向拒）
tableName
    : name ('.' name)?
    ;

whereClause
    : WHERE expr
    ;

groupByClause
    : GROUP BY exprList                       // WITH ROLLUP 不进产生式（白名单外）
    ;

havingClause
    : HAVING expr
    ;

orderByClause
    : ORDER BY orderByItem (',' orderByItem)*
    ;

orderByItem
    : expr (ASC | DESC)?                      // NULLS FIRST/LAST 不进产生式（前置归类）
    ;

// 分页三形态并集（§3）：LIMIT n [OFFSET m [ROW|ROWS]] / LIMIT o, n（MySQL 逗号形态，T1）/
// OFFSET m [ROW|ROWS] FETCH {FIRST|NEXT} n [ROW|ROWS] ONLY；实参窄口 = 数字字面量或 ?；
// 裸 OFFSET / LIMIT ALL / FETCH 省略计数 / LIMIT+FETCH 并用 / PERCENT / WITH TIES
// 均不进产生式（与内核双向拒）
limitClause
    : LIMIT limitArg (',' limitArg
                     | OFFSET limitArg rowSuffix?)?
    | OFFSET limitArg rowSuffix? FETCH (FIRST | NEXT) limitArg rowSuffix? ONLY
    | FETCH (FIRST | NEXT) limitArg rowSuffix? ONLY          // OFFSET 段可选（PG/Oracle 标准族）
    ;

rowSuffix
    : ROW | ROWS
    ;

limitArg
    : NUMBER
    | PARAM
    ;

// ---------- INSERT（§4） ----------

insertStatement
    : INSERT INTO? tableName ('(' idList ')')?
      (VALUES rowValue (',' rowValue)*
       | selectStatement)
      (ON DUPLICATE KEY UPDATE assignment (',' assignment)*)?   // ODKU 仅 MySQL 白名单（T1）
      returningClause?
    ;

rowValue
    : '(' expr (',' expr)* ')'
    ;

// ---------- UPDATE / DELETE（§5） ----------

updateStatement
    : UPDATE tableName alias? SET assignment (',' assignment)*
      whereClause?
      returningClause?
    ;

deleteStatement
    : DELETE FROM tableName alias?
      whereClause?
      returningClause?
    ;

// RETURNING 清单与 SELECT 投影项同形（§4）；仅 DML 尾（SELECT 语句无此产生式——
// SELECT+RETURNING 三方言皆拒，双向一致）；Oracle RETURNING INTO 的 INTO 不被收（双向拒）
returningClause
    : RETURNING selectItem (',' selectItem)*
    ;

assignment
    : columnRef '=' expr                      // := 形态白名单外不收
    ;

// ---------- 表达式（§2，14 级分层递归，级名对应 WHITELIST 优先级链） ----------

exprList
    : expr (',' expr)*
    ;

// 1 级 OR
expr
    : orExpr
    ;

orExpr
    : xorExpr (OR xorExpr)*
    ;

// 2 级 XOR（PG/Oracle 语句中为 T1 容差）
xorExpr
    : andExpr (XOR andExpr)*
    ;

// 3 级 AND（&& 归同级 = T1）
andExpr
    : notExpr ((AND | AND_AND) notExpr)*
    ;

// 4 级 NOT（独立分级：NOT a = b 即 NOT(a=b)）
notExpr
    : NOT notExpr
    | betweenExpr
    ;

// 5 级 BETWEEN（AND 归属由分层解决：low/high 取比较级之下）
betweenExpr
    : comparisonExpr (NOT? BETWEEN comparisonExpr AND comparisonExpr)*
    ;

// 6 级比较/IS/LIKE/IN/REGEXP（REGEXP/RLIKE 在 PG/Oracle 语句中为 T1）
comparisonExpr
    : concatExpr (comparisonOperator concatExpr
                  | IS NOT? NULL                              // IS TRUE/FALSE 白名单外不收
                  | NOT? LIKE concatExpr (ESCAPE concatExpr)?
                  | NOT? IN '(' (exprList | selectStatement) ')'
                  | (REGEXP | RLIKE) concatExpr)*
    ;

comparisonOperator
    : '=' | '<=>' | '>=' | '>' | '<=' | '<' | '<>' | '!=' | '->' | '->>'
    ;

// 7 级拼接 ||（PG/Oracle 位；接受面与 MySQL 无差——树形差异见 F4 裁决 L1）
concatExpr
    : bitOrExpr (PIPE_PIPE bitOrExpr)*
    ;

// 8 级 |
bitOrExpr
    : bitAndExpr ('|' bitAndExpr)*
    ;

// 9 级 &
bitAndExpr
    : shiftExpr ('&' shiftExpr)*
    ;

// 10 级移位
shiftExpr
    : addExpr (('<<' | '>>') addExpr)*
    ;

// 11 级加减
addExpr
    : mulExpr (('+' | '-') mulExpr)*
    ;

// 12 级乘除模/DIV/MOD（DIV/MOD 在 PG/Oracle 语句中为 T1）
mulExpr
    : bitXorExpr (('*' | '/' | '%' | DIV | MOD) bitXorExpr)*
    ;

// 13 级 ^
bitXorExpr
    : unaryExpr ('^' unaryExpr)*
    ;

// 14 级一元（前缀符号；~ 于 PG 语句为 T1）
unaryExpr
    : ('-' | '+' | '!' | '~') unaryExpr
    | primaryExpr
    ;

primaryExpr
    : literal
    | STAR                                    // 裸星表达式（COUNT(*) 等函数实参，内核 Star）
    | qualifiedName '.' STAR                  // 限定星表达式（a.b.*）
    | columnRef
    | functionCall
    | '(' (selectStatement | expr) ')'        // 标量子查询 / 括号分组
    | EXISTS '(' selectStatement ')'
    | caseExpr
    ;

// CASE [operand] WHEN ... THEN ... [ELSE ...] END
caseExpr
    : CASE expr? (WHEN expr THEN expr)+ (ELSE expr)? END
    ;

columnRef
    : qualifiedName
    ;

// 限定名链不限级（§2）；name 段含非保留关键字（内核 isNamePart 同口径）
qualifiedName
    : name ('.' name)*
    ;

functionCall
    : name '(' (DISTINCT? exprList)? ')'      // 受限函数 11 名为 T2 容差
    ;

literal
    : NUMBER
    | HEX
    | BIT
    | STRING_ESC                              // 反斜杠转义串（MySQL/PG E 前缀语义，T3 两义）
    | STRING_DQ                               // 标准串（双写转义，PG/Oracle 语义，T3 两义）
    | PREFIX_STRING_ESC                       // N''/E''/B''/X''（§1 并集，前缀属方言旋钮 T1）
    | PREFIX_STRING_DQ
    | temporalLiteral
    | NULL
    | TRUE
    | FALSE
    | PARAM
    ;

// TEMPORAL：DATE|TIME|TIMESTAMP '...' 前缀形态（保原文，§2）
temporalLiteral
    : (DATE | TIME | TIMESTAMP) (STRING_ESC | STRING_DQ)
    ;

// ---------- 名字（未包裹标识符 / 包裹标识符 / 矩阵 41 词） ----------

idList
    : name (',' name)*
    ;

alias
    : AS? name
    ;

name
    : IDENTIFIER
    | quotedIdentifier
    | aliasUsableKeyword
    ;

quotedIdentifier
    : BACKTICK_ID
    | DQUOTE_ID
    ;

// 别名位可用关键字 = 接受面矩阵三标识符槽位 ACCEPT 词集（41）
aliasUsableKeyword
    : BETWEEN | BY | CASE | CONFLICT | CROSS | DATE | DELETE | DUAL | DUPLICATE
    | END | ESCAPE | FALSE | FIRST | FULL | INNER | INSERT | JOIN | LEFT | LIKE
    | LOCK | MERGE | NATURAL | NEXT | NULLS | OFFSET | ONLY | OUTER | PERCENT
    | PROCEDURE | RETURNING | RIGHT | ROW | ROWS | SET | TIES | TIME | TIMESTAMP
    | TRUE | UPDATE | VALUES | WHEN
    ;

// ---------- 词法（§1 三方言旋钮并集；T3 容差） ----------

WS          : [ \t\r\n]+ -> skip ;

LINE_COMMENT
            : '--' ~[\r\n]* -> skip ;           // 恒注释（MySQL 须空白后随——T3）
HASH_COMMENT
            : '#' ~[\r\n]* -> skip ;            // 仅 MySQL（PG/Oracle 语句中 = T1）
BLOCK_COMMENT
            : '/*' (BLOCK_COMMENT | .)*? '*/' -> skip ;   // 嵌套（PG/Oracle；MySQL 不嵌套 = T3）

// 字符串两义（T3）：反斜杠转义（MySQL/PG E 前缀）与标准串（双写转义，PG/Oracle）
// 各立一规则，靠最长匹配按内容裁定——`'a\'`（标准串）转义规则不可达整token故落标准规则；
// `'a\'b'`（转义串）转义规则跨更长故赢。病理残余：标准串以 \' 收尾且同语句后随引号的
// 形态会被转义规则吞并误拒（白名单语料无此组合，出现即入 LEDGER 校准）
STRING_ESC  : '\'' ('\\' . | ~['\\])* '\'' ;
STRING_DQ   : '\'' ('\'' '\'' | ~['])* '\'' ;

// 前缀字符串：N''（三方言）/E''/B''/X''（PG 旋钮，MySQL X/B——前缀属方言 = T1）；
// 同样两义两规则（E 恒转义、N/B/X 随方言旋钮）
PREFIX_STRING_ESC
            : [nNeEbBxX] '\'' ('\\' . | ~['\\])* '\'' ;
PREFIX_STRING_DQ
            : [nNeEbBxX] '\'' ('\'' '\'' | ~['])* '\'' ;

BACKTICK_ID : '`' (~'`')+ '`' ;                 // 仅 MySQL（PG/Oracle 语句中 = T1）
DQUOTE_ID   : '"' ('""' | ~'"')* '"' ;          // PG/Oracle 包裹标识符（双写转义）

NUMBER      : [0-9]+ '.' [0-9]* ([eE] [+-]? [0-9]+)?
            | '.' [0-9]+ ([eE] [+-]? [0-9]+)?
            | [0-9]+ ([eE] [+-]? [0-9]+)? ;
HEX         : '0' [xX] [0-9a-fA-F]+ ;
BIT         : '0' [bB] [01]+ ;

PARAM       : '?' ;

// 结构关键字（产生式直接引用；先于 IDENTIFIER 声明 = 最长匹配 + 先声明优先）
SELECT: 'SELECT' ; FROM: 'FROM' ; WHERE: 'WHERE' ; GROUP: 'GROUP' ; BY: 'BY' ;
HAVING: 'HAVING' ; ORDER: 'ORDER' ; ASC: 'ASC' ; DESC: 'DESC' ; LIMIT: 'LIMIT' ;
OFFSET: 'OFFSET' ; FETCH: 'FETCH' ; FIRST: 'FIRST' ; NEXT: 'NEXT' ; ROW: 'ROW' ;
ROWS: 'ROWS' ; ONLY: 'ONLY' ; UNION: 'UNION' ; ALL: 'ALL' ; DISTINCT: 'DISTINCT' ;
AS: 'AS' ; JOIN: 'JOIN' ; INNER: 'INNER' ; LEFT: 'LEFT' ; RIGHT: 'RIGHT' ;
OUTER: 'OUTER' ; CROSS: 'CROSS' ; STRAIGHT_JOIN: 'STRAIGHT_JOIN' ; ON: 'ON' ;
USING: 'USING' ; INSERT: 'INSERT' ; INTO: 'INTO' ; VALUES: 'VALUES' ;
UPDATE: 'UPDATE' ; SET: 'SET' ; DELETE: 'DELETE' ; DUPLICATE: 'DUPLICATE' ;
KEY: 'KEY' ; RETURNING: 'RETURNING' ; NULL: 'NULL' ; TRUE: 'TRUE' ; FALSE: 'FALSE' ;
NOT: 'NOT' ; AND: 'AND' ; OR: 'OR' ; XOR: 'XOR' ; IS: 'IS' ; LIKE: 'LIKE' ;
IN: 'IN' ; BETWEEN: 'BETWEEN' ; EXISTS: 'EXISTS' ; CASE: 'CASE' ; WHEN: 'WHEN' ;
THEN: 'THEN' ; ELSE: 'ELSE' ; END: 'END' ; REGEXP: 'REGEXP' ; RLIKE: 'RLIKE' ;
DIV: 'DIV' ; MOD: 'MOD' ; ESCAPE: 'ESCAPE' ; DATE: 'DATE' ; TIME: 'TIME' ;
TIMESTAMP: 'TIMESTAMP' ;
// 仅 name 位引用（矩阵 ACCEPT 词集中未 token 化的词）
CONFLICT: 'CONFLICT' ; DUAL: 'DUAL' ; FULL: 'FULL' ; LOCK: 'LOCK' ; MERGE: 'MERGE' ;
NATURAL: 'NATURAL' ; NULLS: 'NULLS' ; PERCENT: 'PERCENT' ; PROCEDURE: 'PROCEDURE' ;
TIES: 'TIES' ;

// 运算符（先长后短；单字符 = / > < | & + - * / % ^ ! ~ 由字面量直书于产生式或此处）
STAR        : '*' ;                                // 裸星/限定星投影与函数实参星（selectItem/primaryExpr）
PIPE_PIPE   : '||' ;
SE_Q        : '<=>' ;
ANGLE_EQ    : '<=' | '>=' ;
ANGLE_NE    : '<>' | '!=' ;
ARROW       : '->' ;
ARROW_JSON  : '->>' ;
SHIFT_L     : '<<' ;
SHIFT_R     : '>>' ;
AND_AND     : '&&' ;

IDENTIFIER  : [A-Za-z_][A-Za-z0-9_$]* ;
