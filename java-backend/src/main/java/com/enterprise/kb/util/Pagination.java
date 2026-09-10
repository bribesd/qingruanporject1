package com.enterprise.kb.util;

/**
 * 分页解析，等价 utils/pagination.js：读取 limit/offset，约束上限，返回安全值。
 */
public final class Pagination {

    private static final int MAX_LIMIT = 500;

    private Pagination() {
    }

    public record Page(int limit, int offset) {
    }

    public static Page parse(String limitParam, String offsetParam, int defaultLimit) {
        int limit = toInt(limitParam);
        int offset = toInt(offsetParam);

        if (limit < 1) {
            limit = defaultLimit;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (offset < 0) {
            offset = 0;
        }
        return new Page(limit, offset);
    }

    private static int toInt(String s) {
        if (s == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }
}
