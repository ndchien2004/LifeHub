import { describe, expect, it } from 'vitest'
import { formatAmountInput, parseAmount, signOf } from './amount'

/** T3-16 — the money field formats as the user types and reports a plain integer. */
describe('amount formatting', () => {
  it('T3-16 — gõ 1500000 hiển thị 1.500.000', () => {
    expect(formatAmountInput('1500000')).toBe('1.500.000')
  })

  it('T3-16 — giá trị gửi lên API là số nguyên 1500000, không có dấu chấm', () => {
    expect(parseAmount('1.500.000')).toBe(1500000)
  })

  it('Định dạng từng phím một cho ra kết quả đúng ở mọi độ dài', () => {
    const steps = ['1', '15', '150', '1500', '15000', '150000', '1500000']
    const expected = ['1', '15', '150', '1.500', '15.000', '150.000', '1.500.000']

    expect(steps.map(formatAmountInput)).toEqual(expected)
  })

  it('Ô rỗng vẫn rỗng chứ không tự thành 0 — nếu không sẽ không xóa được', () => {
    expect(formatAmountInput('')).toBe('')
    expect(parseAmount('')).toBe(0)
  })

  it('Bỏ qua mọi ký tự không phải số, kể cả khi dán "1.500.000 ₫"', () => {
    expect(parseAmount('1.500.000 ₫')).toBe(1500000)
    expect(formatAmountInput('1.500.000 ₫')).toBe('1.500.000')
    expect(parseAmount('abc')).toBe(0)
  })

  it('Không cho số thực lọt vào: dấu phẩy và dấu chấm thập phân đều bị bỏ', () => {
    expect(parseAmount('45,5')).toBe(455)
    expect(parseAmount('45.5')).toBe(455)
  })

  it('Số 0 đứng đầu bị cắt để không gõ ra 0001.000', () => {
    expect(formatAmountInput('0001000')).toBe('1.000')
    expect(formatAmountInput('0')).toBe('0')
  })

  it('Dấu hiển thị theo loại giao dịch', () => {
    expect(signOf('INCOME')).toBe('+')
    expect(signOf('EXPENSE')).toBe('−')
    expect(signOf('TRANSFER')).toBe('')
  })
})
