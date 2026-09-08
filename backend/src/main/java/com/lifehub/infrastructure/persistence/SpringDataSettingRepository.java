package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.system.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data plumbing. Application code depends on the domain port, never on this interface. */
public interface SpringDataSettingRepository extends JpaRepository<Setting, String> {
}
