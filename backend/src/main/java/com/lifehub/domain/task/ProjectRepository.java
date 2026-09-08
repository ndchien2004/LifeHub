package com.lifehub.domain.task;

import java.util.List;
import java.util.Optional;

/** Persistence port for projects. */
public interface ProjectRepository {

    Project save(Project project);

    Optional<Project> findById(String id);

    /** Live projects, newest first, optionally including archived ones. */
    List<Project> findAll(boolean includeArchived);

    boolean existsByName(String name, String excludingId);
}
