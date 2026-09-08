package com.lifehub.domain.common;

/**
 * Paging and sorting request, expressed without any framework type.
 *
 * <p>Spring Data's {@code Pageable} would drag the framework into the domain layer, which
 * 04-ARCHITECTURE.md §3 forbids. The infrastructure adapter translates this record instead.
 *
 * @param page zero based page index (06-API-SPEC.md §1)
 * @param size rows per page, capped at {@link #MAX_SIZE}
 * @param sortField entity property to order by, or {@code null} for the repository default
 * @param ascending sort direction
 */
public record PageRequest(int page, int size, String sortField, boolean ascending) {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    public PageRequest {
        if (page < 0) {
            throw new ValidationException("Số trang không được âm", "page");
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, null, true);
    }
}
