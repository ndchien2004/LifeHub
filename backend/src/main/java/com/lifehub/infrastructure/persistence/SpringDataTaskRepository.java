package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Task;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the task table. */
public interface SpringDataTaskRepository
        extends JpaRepository<Task, String>, JpaSpecificationExecutor<Task> {

    @Query("SELECT t FROM Task t WHERE t.parent.id = :parentId AND t.deletedAt IS NULL "
            + "ORDER BY t.sortOrder ASC, t.createdAt ASC")
    List<Task> findSubtasks(@Param("parentId") String parentId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId AND t.deletedAt IS NULL")
    long countByProject(@Param("projectId") String projectId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.project.id = :projectId AND t.deletedAt IS NULL "
            + "AND t.status = com.lifehub.domain.task.TaskStatus.DONE")
    long countCompletedByProject(@Param("projectId") String projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Task t SET t.project = NULL WHERE t.project.id = :projectId")
    int clearProject(@Param("projectId") String projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Task t WHERE t.deletedAt IS NOT NULL AND t.deletedAt < :cutoff")
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);

    @Query("SELECT t.parent.id, COUNT(t), "
            + "SUM(CASE WHEN t.status = com.lifehub.domain.task.TaskStatus.DONE THEN 1 ELSE 0 END) "
            + "FROM Task t WHERE t.parent.id IN :parentIds AND t.deletedAt IS NULL GROUP BY t.parent.id")
    List<Object[]> countSubtasks(@Param("parentIds") List<String> parentIds);

    @Query("SELECT tag.id, COUNT(t) FROM Task t JOIN t.tags tag WHERE t.deletedAt IS NULL "
            + "GROUP BY tag.id")
    List<Object[]> countUsageByTag();
}
