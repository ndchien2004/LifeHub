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

    @Override
    public boolean existsByName(String name, String excludingId) {
        return delegate.existsByName(name, excludingId);
    }
}
