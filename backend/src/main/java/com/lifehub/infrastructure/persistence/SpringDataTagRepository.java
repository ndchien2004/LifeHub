package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Tag;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the tag table. */
public interface SpringDataTagRepository extends JpaRepository<Tag, String> {

    List<Tag> findAllByOrderByNameAsc();

    Set<Tag> findAllByIdIn(Set<String> ids);

    @Query("SELECT COUNT(t) > 0 FROM Tag t WHERE LOWER(t.name) = LOWER(:name) "
            + "AND (:excludingId IS NULL OR t.id <> :excludingId)")
    boolean existsByName(@Param("name") String name, @Param("excludingId") String excludingId);
}
