package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link TagRepository} port. */
@Repository
public class TagRepositoryAdapter implements TagRepository {

    private final SpringDataTagRepository delegate;

    public TagRepositoryAdapter(SpringDataTagRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Tag save(Tag tag) {
        return delegate.save(tag);
    }

    @Override
    public Optional<Tag> findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public List<Tag> findAll() {
        return delegate.findAllByOrderByNameAsc();
    }

    @Override
    public Set<Tag> findAllById(Set<String> ids) {
        return ids == null || ids.isEmpty() ? Set.of() : delegate.findAllByIdIn(ids);
    }

    @Override
    public boolean existsByName(String name, String excludingId) {
        return delegate.existsByName(name, excludingId);
    }

    @Override
    public void delete(Tag tag) {
        delegate.delete(tag);
    }
}
