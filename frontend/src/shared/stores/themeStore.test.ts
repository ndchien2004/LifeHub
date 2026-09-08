import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resolveTheme, useThemeStore } from './themeStore'

function mockPrefersDark(prefersDark: boolean) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockReturnValue({
      matches: prefersDark,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }),
  )
}

describe('themeStore — FR-SYS-06', () => {
  beforeEach(() => {
    localStorage.clear()
    useThemeStore.setState({ theme: 'SYSTEM' })
  })

  it('mặc định theo hệ thống', () => {
    expect(useThemeStore.getState().theme).toBe('SYSTEM')
  })

  it('LIGHT và DARK bỏ qua thiết lập hệ điều hành', () => {
    mockPrefersDark(true)
    expect(resolveTheme('LIGHT')).toBe('light')

    mockPrefersDark(false)
    expect(resolveTheme('DARK')).toBe('dark')
  })

  it('SYSTEM đi theo thiết lập hệ điều hành', () => {
    mockPrefersDark(true)
    expect(resolveTheme('SYSTEM')).toBe('dark')

    mockPrefersDark(false)
    expect(resolveTheme('SYSTEM')).toBe('light')
  })

  it('lựa chọn được ghi vào localStorage để giữ qua lần mở app sau', () => {
    useThemeStore.getState().setTheme('DARK')

    expect(useThemeStore.getState().theme).toBe('DARK')
    expect(localStorage.getItem('lifehub.theme')).toContain('DARK')
  })
})
