package com.lifehub.domain.task;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Persistence port for tags. */
public interface TagRepository {

    Tag save(Tag tag);

    Optional<Tag> findById(String id);

    List<Tag> findAll();

    Set<Tag> findAllById(Set<String> ids);

    boolean existsByName(String name, String excludingId);

    /** Hard delete - the one approved exception to soft delete (item C-5b). */
    void delete(Tag tag);
}
