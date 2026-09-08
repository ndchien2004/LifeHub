package com.lifehub.application.finance;

import com.lifehub.application.finance.FinanceCommands.CreateCategory;
import com.lifehub.application.finance.FinanceCommands.UpdateCategory;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category management for the two level income/expense tree (FR-FIN-02, FR-FIN-03). */
@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public CategoryService(
            CategoryRepository categoryRepository,
            TransactionRepository transactionRepository,
            Clock clock) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    /** Flat list of live categories; the api layer assembles the tree for the response. */
    @Transactional(readOnly = true)
    public List<Category> findAll(CategoryType type) {
        return categoryRepository.findAll(type);
    }

    @Transactional(readOnly = true)
    public Category findById(String id) {
        return categoryRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy danh mục"));
    }

    public Category create(CreateCategory command) {
        Category parent = command.parentId() == null || command.parentId().isBlank()
                ? null
                : findById(command.parentId());

        CategoryType type = parent != null ? parent.getType() : command.type();
        requireNameAvailable(command.name(), parent == null ? null : parent.getId(), type, null);

        Category category = new Category(command.name(), type, parent, command.icon(), command.color());
        category.changeSortOrder(command.sortOrder());
        return categoryRepository.save(category);
    }

    public Category update(String id, UpdateCategory command) {
        Category category = findById(id);

        command.parentId().ifPresent(parentId -> {
            if (parentId == null || parentId.isBlank()) {
                category.attachTo(null);
            } else {
                requireNoChildren(category, "Danh mục đang có danh mục con nên không thể trở thành danh mục con");
                category.attachTo(findById(parentId));
            }
        });
        command.name().ifPresent(name -> {
            requireNameAvailable(name, category.getParentId(), category.getType(), id);
            category.rename(name);
        });
        command.icon().ifPresent(category::changeIcon);
        command.color().ifPresent(category::recolor);
        command.sortOrder().ifPresent(category::changeSortOrder);

        return categoryRepository.save(category);
    }

    /**
     * Soft deletes a category.
     *
     * <p>Blocked in three cases. A system category is part of the seeded set and charts from
     * previous months are labelled with it. A category with transactions would leave those rows
     * pointing at something invisible. A parent with children would orphan a whole branch of the
     * tree, and the FK is {@code ON DELETE RESTRICT} precisely so this cannot happen by accident.
     */
    public void delete(String id) {
        Category category = findById(id);

        if (category.isSystem()) {
            throw new ConflictException("Không xóa được danh mục mặc định của hệ thống");
        }
        requireNoChildren(category, "Không xóa được danh mục đang có danh mục con. Hãy xóa các danh mục con trước.");

        long transactionCount = transactionRepository.countByCategory(id);
        if (transactionCount > 0) {
            throw new ConflictException(
                    "Không xóa được danh mục vì còn " + transactionCount + " giao dịch đang dùng nó.");
        }

        category.softDelete(clock.instant());
        categoryRepository.save(category);
    }

    private void requireNoChildren(Category category, String message) {
        if (!categoryRepository.findChildren(category.getId()).isEmpty()) {
            throw new ConflictException(message);
        }
    }

    private void requireNameAvailable(String name, String parentId, CategoryType type, String excludingId) {
        if (name != null && categoryRepository.existsByName(name.trim(), parentId, type, excludingId)) {
            throw new ConflictException("Tên danh mục đã tồn tại trong cùng nhóm", "name");
        }
    }
}
