import { describe, expect, it } from 'vitest'
import { categoryOptions, flattenCategories } from './categoryTree'
import type { Category } from '../types'

function category(id: string, name: string, children: Category[] = []): Category {
  return {
    id,
    name,
    type: 'EXPENSE',
    color: '#f59e0b',
    isSystem: true,
    sortOrder: 0,
    children: children.length > 0 ? children : undefined,
  }
}

describe('categoryTree', () => {
  const tree = [
    category('food', 'Ăn uống', [category('coffee', 'Cà phê'), category('market', 'Đi chợ')]),
    category('other', 'Khác'),
  ]

  it('Danh mục con hiện thụt vào để giữ được cấu trúc cây trong thẻ option', () => {
    const options = categoryOptions(tree)

    expect(options.map((option) => option.id)).toEqual(['food', 'coffee', 'market', 'other'])
    expect(options[1]!.label).toContain('Cà phê')
    expect(options[1]!.label.startsWith('\u00A0')).toBe(true)
    expect(options[3]!.label).toBe('Khác')
  })

  it('Làm phẳng cây trả về đủ cả cha lẫn con', () => {
    expect(flattenCategories(tree).map((item) => item.id)).toEqual([
      'food',
      'coffee',
      'market',
      'other',
    ])
  })

  it('Cây rỗng không làm vỡ danh sách lựa chọn', () => {
    expect(categoryOptions([])).toEqual([])
  })
})
