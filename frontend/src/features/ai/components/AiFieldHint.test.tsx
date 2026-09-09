import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AiFieldHint, aiFieldClass, withoutUncertainValues } from './AiFieldHint'

/** UC-09 alternate flow 10a — the 0.6 threshold, in the one place it is applied. */
describe('AiFieldHint', () => {
  it('không hiện gì cho trường không phải do AI điền', () => {
    const { container } = render(<AiFieldHint />)
    expect(container).toBeEmptyDOMElement()
  })

  it('hiện badge kèm phần trăm khi AI đủ tự tin', () => {
    render(<AiFieldHint confidence={0.87} />)
    expect(screen.getByText('AI 87%')).toBeInTheDocument()
  })

  it('hiện cảnh báo khi độ tin cậy dưới ngưỡng 0.6', () => {
    render(<AiFieldHint confidence={0.42} />)
    expect(screen.getByText('Cần kiểm tra')).toBeInTheDocument()
  })

  it('đúng ngưỡng 0.6 vẫn được coi là đủ tin cậy', () => {
    render(<AiFieldHint confidence={0.6} />)
    expect(screen.getByText('AI 60%')).toBeInTheDocument()
  })
})

describe('aiFieldClass', () => {
  it('chỉ highlight ô có độ tin cậy thấp', () => {
    expect(aiFieldClass(undefined)).toBeUndefined()
    expect(aiFieldClass(0.9)).toBeUndefined()
    expect(aiFieldClass(0.3)).toContain('ring-amber')
  })
})

describe('withoutUncertainValues', () => {
  it('bỏ giá trị AI không chắc, giữ nguyên phần còn lại', () => {
    const values = { amount: 45000, categoryId: 'c1', note: 'cơm gà' }

    expect(withoutUncertainValues(values, { amount: 0.99, categoryId: 0.3 })).toEqual({
      amount: 45000,
      note: 'cơm gà',
    })
  })

  it('trường không có điểm tin cậy được giữ lại', () => {
    const values = { amount: 45000, walletId: 'w1' }

    expect(withoutUncertainValues(values, { amount: 0.99 })).toEqual({
      amount: 45000,
      walletId: 'w1',
    })
  })

  it('không sửa object gốc', () => {
    const values = { amount: 45000, categoryId: 'c1' }
    withoutUncertainValues(values, { categoryId: 0.1 })

    expect(values.categoryId).toBe('c1')
  })
})
