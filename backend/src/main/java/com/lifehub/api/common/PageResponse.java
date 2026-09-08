package com.lifehub.api.common;

import com.lifehub.domain.common.Page;
import java.util.List;

/**
 * Paginated list envelope from 06-API-SPEC.md section 1.
 *
 * <p>Separate from the domain {@code Page} so the wire format stays fixed even if the internal
 * paging type changes, and so field names match the spec exactly.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    /** Wraps already mapped items alongside the paging numbers of the source page. */
    public static <S, T> PageResponse<T> of(Page<S> source, List<T> items) {
        return new PageResponse<>(items, source.page(), source.size(), source.totalItems(), source.totalPages());
    }
}
