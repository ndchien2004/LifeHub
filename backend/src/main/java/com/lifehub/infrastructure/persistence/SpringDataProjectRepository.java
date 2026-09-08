package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the project table. */
public interface SpringDataProjectRepository extends JpaRepository<Project, String> {

    @Query("SELECT p FROM Project p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<Project> findAllLive();

    @Query("SELECT p FROM Project p WHERE p.deletedAt IS NULL "
            + "AND p.status = com.lifehub.domain.task.ProjectStatus.ACTIVE ORDER BY p.createdAt DESC")
    List<Project> findAllActive();

    @Query("SELECT COUNT(p) > 0 FROM Project p WHERE LOWER(p.name) = LOWER(:name) "
            + "AND p.deletedAt IS NULL AND (:excludingId IS NULL OR p.id <> :excludingId)")
    boolean existsByName(@Param("name") String name, @Param("excludingId") String excludingId);
}
