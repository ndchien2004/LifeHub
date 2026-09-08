package com.lifehub.domain.finance;

import java.util.List;
import java.util.Optional;

/** Persistence port for categories. */
public interface CategoryRepository {

    Category save(Category category);

    Optional<Category> findById(String id);

    /** Live categories, ordered by sort order then name, optionally filtered by type. */
    List<Category> findAll(CategoryType type);

    List<Category> findAllById(List<String> ids);

    List<Category> findChildren(String parentId);

    boolean existsByName(String name, String parentId, CategoryType type, String excludingId);
}
