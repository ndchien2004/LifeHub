package com.lifehub.domain.common;

import java.util.List;

/** One page of results plus the totals the client needs to render a pager (06-API-SPEC.md §1). */
public record Page<T>(List<T> items, int page, int size, long totalItems) {

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalItems / size);
    }

    public <R> Page<R> map(java.util.function.Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, size, totalItems);
    }

    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(List.of(), request.page(), request.size(), 0);
    }
}
