package com.enterprise.kb.util;

/**
 * 请求体字段读取辅助，语义对齐原 Node 代码对 query/body 的处理方式。
 */
public final class Str {

    private Str() {
    }

    /** 等价 (v === undefined || v === null || v === '' ? null : v)，用于「可清空」的文本字段 */
    public static String normText(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }

    /** 等价 v || ''，用于创建时默认空串 */
    public static String orEmpty(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 等价 JS truthy 语义的长整数：null / 0 / 空串 / 非数字都返回 null（用于可选外键 id） */
    public static Long jsLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            long l = n.longValue();
            return l == 0 ? null : l;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            long l = Long.parseLong(s);
            return l == 0 ? null : l;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
