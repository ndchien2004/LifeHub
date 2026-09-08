import { describe, expect, it } from 'vitest'
import { formatAmount, formatCount, formatCurrency } from './formatters'

describe('formatters — định dạng số kiểu Việt Nam (NFR-USE-02)', () => {
  it('dùng dấu chấm phân cách hàng nghìn', () => {
    expect(formatAmount(1500000)).toBe('1.500.000')
    expect(formatAmount(45000)).toBe('45.000')
    expect(formatAmount(1200)).toBe('1.200')
  })

  it('giữ nguyên số nhỏ hơn 1000', () => {
    expect(formatAmount(0)).toBe('0')
    expect(formatAmount(999)).toBe('999')
  })

  it('xử lý số âm — dùng cho chênh lệch thu chi', () => {
    expect(formatAmount(-2500000)).toBe('-2.500.000')
  })

  it('thêm ký hiệu tiền tệ khi cần', () => {
    expect(formatCurrency(1500000)).toBe('1.500.000 ₫')
  })

  it('formatCount dùng chung quy tắc phân cách', () => {
    expect(formatCount(5000)).toBe('5.000')
  })
})
