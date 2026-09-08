package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link ProjectRepository} port. */
@Repository
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final SpringDataProjectRepository delegate;

    public ProjectRepositoryAdapter(SpringDataProjectRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Project save(Project project) {
        return delegate.save(project);
    }

    @Override
    public Optional<Project> findById(String id) {
        return delegate.findById(id).filter(project -> !project.isDeleted());
    }

    @Override
    public List<Project> findAll(boolean includeArchived) {
        return includeArchived ? delegate.findAllLive() : delegate.findAllActive();
    }

    /**
     * Case insensitive name check.
     *
     * <p>Compared in Java for the same reason as tags: SQLite {@code LOWER()} is ASCII only and
     * leaves accented Vietnamese characters unchanged, so two names differing only in case would
     * both be accepted.
     */
    @Override
    public boolean existsByName(String name, String excludingId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String candidate = name.trim();
        return delegate.findAllLive().stream()
                .anyMatch(project ->
                        !project.getId().equals(excludingId) && project.getName().equalsIgnoreCase(candidate));
    }
}
