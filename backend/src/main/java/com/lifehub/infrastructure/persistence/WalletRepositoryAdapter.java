package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adapts Spring Data JPA to the {@link WalletRepository} port. */
@Repository
public class WalletRepositoryAdapter implements WalletRepository {

    private final SpringDataWalletRepository delegate;

    public WalletRepositoryAdapter(SpringDataWalletRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Wallet save(Wallet wallet) {
        return delegate.save(wallet);
    }

    @Override
    public Optional<Wallet> findById(String id) {
        return delegate.findById(id).filter(wallet -> !wallet.isDeleted());
    }

    @Override
    public List<Wallet> findAll() {
        return delegate.findAllLive();
    }

    @Override
    public List<Wallet> findAllById(List<String> ids) {
        return ids == null || ids.isEmpty() ? List.of() : delegate.findAllLiveById(ids);
    }

    /**
     * Case insensitive name check, compared in Java.
     *
     * <p>Same reason as projects and tags: SQLite {@code LOWER()} only folds ASCII, so "Tiền mặt"
     * and "TIỀN MẶT" would both be accepted as distinct wallet names.
     */
    @Override
    public boolean existsByName(String name, String excludingId) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String candidate = name.trim().toLowerCase(Locale.ROOT);
        return delegate.findAllLive().stream()
                .anyMatch(wallet -> !wallet.getId().equals(excludingId)
                        && wallet.getName().toLowerCase(Locale.ROOT).equals(candidate));
    }

    @Override
    public int clearDefaultExcept(String walletId) {
        return delegate.clearDefaultExcept(walletId);
    }
}
