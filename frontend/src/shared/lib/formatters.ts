/**
 * Vietnamese display formatting (NFR-USE-02).
 *
 * Thousands are separated with a dot and decimals with a comma, so 1500000 reads as
 * "1.500.000". Money is a whole number of đồng everywhere in the system — there is no
 * decimal part to render, and no floating point value ever reaches this module.
 */

const NUMBER_FORMAT = new Intl.NumberFormat('vi-VN')

/** Formats an integer amount in đồng, e.g. 1500000 -> "1.500.000". */
export function formatAmount(amountInDong: number): string {
  return NUMBER_FORMAT.format(amountInDong)
}

/** Formats an amount with its currency suffix, e.g. "1.500.000 ₫". */
export function formatCurrency(amountInDong: number): string {
  return `${formatAmount(amountInDong)} ₫`
}

/** Formats any integer for display, e.g. a task count. */
export function formatCount(value: number): string {
  return NUMBER_FORMAT.format(value)
}
