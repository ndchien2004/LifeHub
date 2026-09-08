package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.finance.Wallet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the wallet table. */
public interface SpringDataWalletRepository extends JpaRepository<Wallet, String> {

    @Query("SELECT w FROM Wallet w WHERE w.deletedAt IS NULL ORDER BY w.sortOrder ASC, w.createdAt ASC")
    List<Wallet> findAllLive();

    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids AND w.deletedAt IS NULL")
    List<Wallet> findAllLiveById(@Param("ids") List<String> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.defaultWallet = false WHERE w.id <> :walletId AND w.defaultWallet = true")
    int clearDefaultExcept(@Param("walletId") String walletId);
}
