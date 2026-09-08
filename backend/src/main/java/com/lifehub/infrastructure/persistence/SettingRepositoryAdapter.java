package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.system.Setting;
import com.lifehub.domain.system.SettingRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link SettingRepository} port declared by the domain layer. */
@Repository
public class SettingRepositoryAdapter implements SettingRepository {

    private final SpringDataSettingRepository delegate;

    public SettingRepositoryAdapter(SpringDataSettingRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Setting> findAll() {
        return delegate.findAll(Sort.by("key"));
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        return delegate.findById(key);
    }

    @Override
    public Setting save(Setting setting) {
        return delegate.save(setting);
    }
}
