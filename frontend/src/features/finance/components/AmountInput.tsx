import { forwardRef, useEffect, useState } from 'react'
import { Input } from '@/shared/components/ui/input'
import { cn } from '@/shared/lib/utils'
import { formatAmountInput, parseAmount } from '../lib/amount'

interface AmountInputProps {
  value: number
  onChange: (value: number) => void
  id?: string
  placeholder?: string
  className?: string
  autoFocus?: boolean
  'aria-invalid'?: boolean
  'aria-label'?: string
}

/**
 * Money field that groups thousands as the user types (UC-06 step 3, T3-16).
 *
 * <p>The displayed text and the reported value are deliberately different things: the field shows
 * "1.500.000" while {@code onChange} reports the integer 1500000. Formatting on every keystroke
 * rather than on blur is what makes a mistyped extra zero visible immediately, which is the whole
 * point - a wrong amount is the most expensive mistake this screen can produce.
 *
 * <p>{@code inputMode="numeric"} rather than {@code type="number"}: a number input would strip the
 * separators and bring along spinner arrows and locale-dependent decimal handling.
 */
export const AmountInput = forwardRef<HTMLInputElement, AmountInputProps>(function AmountInput(
  { value, onChange, id, placeholder, className, autoFocus, ...aria },
  ref,
) {
  const [text, setText] = useState(() => formatAmountInput(String(value || '')))

  // Keeps the field in step when the form is reset or an existing transaction is loaded into it,
  // without fighting the user while they are typing.
  useEffect(() => {
    setText((current) => (parseAmount(current) === value ? current : formatAmountInput(String(value || ''))))
  }, [value])

  return (
    <div className={cn('relative', className)}>
      <Input
        ref={ref}
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        autoFocus={autoFocus}
        placeholder={placeholder ?? '0'}
        value={text}
        onChange={(event) => {
          const formatted = formatAmountInput(event.target.value)
          setText(formatted)
          onChange(parseAmount(formatted))
        }}
        className="pr-8 text-right font-medium tabular-nums"
        {...aria}
      />
      <span
        className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-sm text-muted-foreground"
        aria-hidden
      >
        ₫
      </span>
    </div>
  )
})
