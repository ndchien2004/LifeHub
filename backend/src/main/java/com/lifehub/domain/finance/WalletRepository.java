package com.lifehub.domain.finance;

import java.util.List;
import java.util.Optional;

/** Persistence port for wallets. */
public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findById(String id);

    List<Wallet> findAll();

    List<Wallet> findAllById(List<String> ids);

    boolean existsByName(String name, String excludingId);

    /** Clears the default flag on every other wallet, so at most one can hold it (FR-FIN-01). */
    int clearDefaultExcept(String walletId);
}
