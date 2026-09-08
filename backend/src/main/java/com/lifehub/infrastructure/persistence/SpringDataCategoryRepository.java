package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the category table. */
public interface SpringDataCategoryRepository extends JpaRepository<Category, String> {

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.deletedAt IS NULL "
            + "ORDER BY c.sortOrder ASC, c.name ASC")
    List<Category> findAllLive();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.deletedAt IS NULL AND c.type = :type "
            + "ORDER BY c.sortOrder ASC, c.name ASC")
    List<Category> findAllLiveByType(@Param("type") CategoryType type);

    @Query("SELECT c FROM Category c WHERE c.id IN :ids AND c.deletedAt IS NULL")
    List<Category> findAllLiveById(@Param("ids") List<String> ids);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId AND c.deletedAt IS NULL "
            + "ORDER BY c.sortOrder ASC, c.name ASC")
    List<Category> findChildren(@Param("parentId") String parentId);
}
