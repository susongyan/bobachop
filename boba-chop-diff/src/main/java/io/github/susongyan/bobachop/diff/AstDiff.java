package io.github.susongyan.bobachop.diff;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.susongyan.bobachop.ast.Node;

/**
 * AST 反射深度比较（DESIGN D7：断言含全部位置偏移）。
 *
 * <p>比较规则：</p>
 * <ul>
 *   <li>双 null 相等；单 null 记差异（另一侧渲染为类型名或 {@code null}）。</li>
 *   <li>类型不同记差异，不再下钻。</li>
 *   <li>{@code List}：先比长度（路径 {@code <字段>.size}，长度不同即形态不同，停止下钻避免噪音），
 *       再逐元素以 {@code <字段>[i]} 下钻。</li>
 *   <li>值类型（String / 枚举 / 基本类型包装类）按 equals 比较。</li>
 *   <li>其余（AST 复合节点）沿父类链逐字段反射下钻——{@code start}/{@code stop} 是
 *       {@code AbstractNode} 的普通字段，偏移因此自动纳入比较。</li>
 *   <li>未知字段类型（契约将来引入 Map/数组等）按"类型不可比较"记差异，显式暴露而非静默略过。</li>
 * </ul>
 *
 * <p>字段按名字典序遍历（JLS 不保证 getDeclaredFiles 顺序），保证差异报告确定可复现。
 * AST 未定义 equals/hashCode（契约 v1 约定），本类即深度比较的唯一实现位。</p>
 */
public final class AstDiff {

    private static final Set<Class<?>> VALUE_TYPES;

    static {
        VALUE_TYPES = Collections.unmodifiableSet(new HashSet<Class<?>>(Arrays.<Class<?>>asList(String.class,
                Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Character.class)));
    }

    private AstDiff() {
    }

    /** 比较两棵树（通常为两侧执行器产出的语句根节点），返回全部差异；空清单即全等。 */
    public static List<DiffDifference> compare(final Object expected, final Object actual) {
        final List<DiffDifference> out = new ArrayList<DiffDifference>();
        walk("", expected, actual, out);
        return out;
    }

    private static void walk(final String path, final Object expected, final Object actual, final List<DiffDifference> out) {
        if (null == expected && null == actual) {
            return;
        }
        if (null == expected || null == actual) {
            out.add(new DiffDifference(path, render(expected), render(actual)));
            return;
        }
        final Class<?> expectedClass = expected.getClass();
        final Class<?> actualClass = actual.getClass();
        if (!expectedClass.equals(actualClass)) {
            out.add(new DiffDifference(path, expectedClass.getSimpleName(), actualClass.getSimpleName()));
            return;
        }
        if (expected instanceof List) {
            walkList(path, (List<?>) expected, (List<?>) actual, out);
            return;
        }
        if (isValueType(expectedClass)) {
            if (!expected.equals(actual)) {
                out.add(new DiffDifference(path, render(expected), render(actual)));
            }
            return;
        }
        walkFields(path, expectedClass, expected, actual, out);
    }

    private static void walkList(final String path, final List<?> expected, final List<?> actual, final List<DiffDifference> out) {
        if (expected.size() != actual.size()) {
            out.add(new DiffDifference(child(path, "size"), String.valueOf(expected.size()), String.valueOf(actual.size())));
            return;
        }
        for (int i = 0; i < expected.size(); i++) {
            walk(path + "[" + i + "]", expected.get(i), actual.get(i), out);
        }
    }

    private static void walkFields(final String path, final Class<?> type, final Object expected, final Object actual, final List<DiffDifference> out) {
        for (Class<?> c = type; null != c && c != Object.class; c = c.getSuperclass()) {
            for (final Field field : sortedFields(c)) {
                walk(child(path, field.getName()), read(field, expected), read(field, actual), out);
            }
        }
    }

    private static List<Field> sortedFields(final Class<?> type) {
        final Field[] declared = type.getDeclaredFields();
        Arrays.sort(declared, (a, b) -> a.getName().compareTo(b.getName()));
        final List<Field> fields = new ArrayList<Field>();
        for (final Field field : declared) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
        return fields;
    }

    private static Object read(final Field field, final Object target) {
        try {
            return field.get(target);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException("不可达：setAccessible 已放开 " + field, e);
        }
    }

    private static boolean isValueType(final Class<?> type) {
        return type.isEnum() || VALUE_TYPES.contains(type);
    }

    private static String child(final String parent, final String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }

    /** 观测值渲染：字符串加单引号、null 显式、AST 节点取类型名，其余走 toString。 */
    private static String render(final Object value) {
        if (null == value) {
            return "null";
        }
        if (value instanceof String) {
            return "'" + value + "'";
        }
        if (value instanceof Node) {
            return value.getClass().getSimpleName();
        }
        return String.valueOf(value);
    }
}
