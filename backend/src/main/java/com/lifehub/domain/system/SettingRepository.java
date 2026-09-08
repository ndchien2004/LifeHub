package com.lifehub.domain.system;

import java.util.List;
import java.util.Optional;

/**
 * Port for reading and writing application settings.
 *
 * <p>Declared in the domain layer and implemented in {@code infrastructure.persistence}, per the
 * dependency inversion rule in 04-ARCHITECTURE.md 3.
 */
public interface SettingRepository {

    List<Setting> findAll();

    Optional<Setting> findByKey(String key);

    Setting save(Setting setting);
}
