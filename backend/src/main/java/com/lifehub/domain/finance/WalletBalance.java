package com.lifehub.domain.finance;

/**
 * A wallet's derived balance (03-DATA-MODEL.md 2.6).
 *
 * <p>{@code balance} is a signed long, not a {@link Money}: a credit wallet legitimately holds a
 * negative balance, and so does a cash wallet the user has over-drawn by mistyping an amount.
 * Refusing to represent that would hide the error rather than surface it.
 *
 * @param asOfSpent total leaving the wallet, as a positive magnitude
 * @param asOfReceived total arriving in the wallet, as a positive magnitude
 */
public record WalletBalance(String walletId, long balance, long asOfReceived, long asOfSpent) {

    public static WalletBalance of(String walletId, long initialBalance, long received, long spent) {
        return new WalletBalance(walletId, initialBalance + received - spent, received, spent);
    }
}
