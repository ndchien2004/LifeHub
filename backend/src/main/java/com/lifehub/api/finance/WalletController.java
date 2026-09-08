package com.lifehub.api.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifehub.api.common.ApiResponse;
import com.lifehub.api.common.JsonPatchReader;
import com.lifehub.api.finance.FinanceDtos.CreateWalletRequest;
import com.lifehub.api.finance.FinanceDtos.WalletBalanceResponse;
import com.lifehub.api.finance.FinanceDtos.WalletListResponse;
import com.lifehub.api.finance.FinanceDtos.WalletResponse;
import com.lifehub.application.finance.FinanceCommands.CreateWallet;
import com.lifehub.application.finance.FinanceCommands.UpdateWallet;
import com.lifehub.application.finance.WalletBalanceCalculator;
import com.lifehub.application.finance.WalletService;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletBalance;
import com.lifehub.domain.finance.WalletType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Wallet endpoints (06-API-SPEC.md section 7). */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;
    private final WalletBalanceCalculator balanceCalculator;
    private final FinanceMapper mapper;
    private final JsonPatchReader patches;

    public WalletController(
            WalletService walletService,
            WalletBalanceCalculator balanceCalculator,
            FinanceMapper mapper,
            JsonPatchReader patches) {
        this.walletService = walletService;
        this.balanceCalculator = balanceCalculator;
        this.mapper = mapper;
        this.patches = patches;
    }

    /** Wallets with their derived balances, plus the total across all of them (FR-FIN-07). */
    @GetMapping
    public ApiResponse<WalletListResponse> list() {
        List<Wallet> wallets = walletService.findAll();
        Map<String, WalletBalance> balances = balanceCalculator.balancesOf(wallets);

        List<WalletResponse> responses = wallets.stream()
                .map(wallet -> mapper.toResponse(wallet, balances.get(wallet.getId())))
                .toList();
        long totalAssets = responses.stream().mapToLong(WalletResponse::balance).sum();

        return ApiResponse.ok(new WalletListResponse(responses, totalAssets));
    }

    @GetMapping("/{id}")
    public ApiResponse<WalletResponse> detail(@PathVariable String id) {
        Wallet wallet = walletService.findById(id);
        return ApiResponse.ok(mapper.toResponse(wallet, balanceCalculator.balanceOf(wallet)));
    }

    /**
     * Balance at a point in time.
     *
     * @param asOf exclusive upper bound; omitted means the balance right now
     */
    @GetMapping("/{id}/balance")
    public ApiResponse<WalletBalanceResponse> balance(
            @PathVariable String id, @RequestParam(required = false) OffsetDateTime asOf) {
        Wallet wallet = walletService.findById(id);
        return ApiResponse.ok(
                mapper.toResponse(balanceCalculator.balanceOf(wallet, mapper.toInstant(asOf))));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.create(new CreateWallet(
                request.name(),
                request.type(),
                request.initialBalance(),
                request.currency(),
                request.icon(),
                request.isDefault(),
                request.sortOrder()));
        return ApiResponse.ok(mapper.toResponse(wallet, balanceCalculator.balanceOf(wallet)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<WalletResponse> update(@PathVariable String id, @RequestBody JsonNode body) {
        UpdateWallet command = new UpdateWallet(
                patches.read(body, "name", String.class),
                patches.read(body, "type", WalletType.class),
                patches.read(body, "initialBalance", Long.class),
                patches.read(body, "currency", String.class),
                patches.read(body, "icon", String.class),
                patches.read(body, "isDefault", Boolean.class),
                patches.read(body, "sortOrder", Integer.class));

        Wallet wallet = walletService.update(id, command);
        return ApiResponse.ok(mapper.toResponse(wallet, balanceCalculator.balanceOf(wallet)));
    }

    /** Soft delete, refused with 409 while transactions still reference the wallet (item C-6). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        walletService.delete(id);
        return ApiResponse.ok(null);
    }
}
