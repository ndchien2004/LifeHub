package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.CategoryType;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link CategoryRepository} port. */
@Repository
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository delegate;

    public CategoryRepositoryAdapter(SpringDataCategoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Category save(Category category) {
        return delegate.save(category);
    }

    @Override
    public Optional<Category> findById(String id) {
        return delegate.findById(id).filter(category -> !category.isDeleted());
    }

    @Override
    public List<Category> findAll(CategoryType type) {
        return type == null ? delegate.findAllLive() : delegate.findAllLiveByType(type);
    }

    @Override
    public List<Category> findAllById(List<String> ids) {
        return ids == null || ids.isEmpty() ? List.of() : delegate.findAllLiveById(ids);
    }

    @Override
    public List<Category> findChildren(String parentId) {
        return parentId == null ? List.of() : delegate.findChildren(parentId);
    }

    /**
     * Uniqueness of {@code (name, parent, type)} among live categories, compared in Java.
     *
     * <p>The database index enforces the same rule, but only byte for byte. Vietnamese names
     * differing only by case would slip past it, and the user would end up with two "Cà phê"
     * entries under the same parent.
     */
    @Override
    public boolean existsByName(String name, String parentId, CategoryType type, String excludingId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String candidate = name.trim().toLowerCase(Locale.ROOT);
        return delegate.findAllLive().stream()
                .filter(category -> !category.getId().equals(excludingId))
                .filter(category -> category.getType() == type)
                .filter(category -> Objects.equals(category.getParentId(), parentId))
                .anyMatch(category -> category.getName().toLowerCase(Locale.ROOT).equals(candidate));
    }
}
