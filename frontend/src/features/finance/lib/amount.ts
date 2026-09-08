/**
 * Money input formatting (FR-FIN-06, UC-06 step 3).
 *
 * Typing 1500000 has to read back as "1.500.000" while the value sent to the API stays the
 * integer 1500000. The two directions live here rather than inside the input component so the
 * rules are testable on their own (T3-16).
 */

/** Strips every character that is not a digit, so a pasted "1.500.000 ₫" still parses. */
export function parseAmount(raw: string): number {
  const digits = raw.replace(/\D/g, '')
  return digits === '' ? 0 : Number(digits)
}

/**
 * Formats keystrokes into grouped digits, e.g. "1500000" -> "1.500.000".
 *
 * An empty input stays empty rather than becoming "0": showing a zero the user did not type
 * makes the field impossible to clear.
 */
export function formatAmountInput(raw: string): string {
  const digits = raw.replace(/\D/g, '').replace(/^0+(?=\d)/, '')
  if (digits === '') {
    return ''
  }
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

/** Signed display of a transaction amount: expense negative, income positive, transfer neutral. */
export function signOf(type: 'INCOME' | 'EXPENSE' | 'TRANSFER'): string {
  if (type === 'INCOME') {
    return '+'
  }
  return type === 'EXPENSE' ? '−' : ''
}
