import { describe, expect, it } from 'vitest'
import {
  buildRrule,
  describeRrule,
  NO_RECURRENCE,
  parseRrule,
  type Recurrence,
} from './rrule'

/**
 * The RRULE builder and its Vietnamese rendering (FR-CAL-03).
 *
 * The round trip matters most: whatever the form produces has to parse back into the same state, or
 * reopening an event would silently show a different rule from the one that was saved.
 */

function recurrence(overrides: Partial<Recurrence>): Recurrence {
  return { ...NO_RECURRENCE, ...overrides }
}

describe('buildRrule', () => {
  it('không lặp thì không sinh RRULE', () => {
    expect(buildRrule(NO_RECURRENCE)).toBeNull()
  })

  it('bỏ qua INTERVAL=1 vì đó đã là mặc định của RFC 5545', () => {
    expect(buildRrule(recurrence({ freq: 'DAILY', interval: 1 }))).toBe('FREQ=DAILY')
    expect(buildRrule(recurrence({ freq: 'DAILY', interval: 3 }))).toBe('FREQ=DAILY;INTERVAL=3')
  })

  it('xếp BYDAY theo thứ tự tuần, không theo thứ tự bấm', () => {
    const clickedOutOfOrder = recurrence({ freq: 'WEEKLY', byDay: ['WE', 'MO'] })

    expect(buildRrule(clickedOutOfOrder)).toBe('FREQ=WEEKLY;BYDAY=MO,WE')
  })

  it('chỉ gắn BYDAY cho quy luật hằng tuần', () => {
    expect(buildRrule(recurrence({ freq: 'MONTHLY', byDay: ['MO'] }))).toBe('FREQ=MONTHLY')
  })

  it('gắn COUNT hoặc UNTIL tùy kiểu kết thúc', () => {
    expect(buildRrule(recurrence({ freq: 'WEEKLY', end: { type: 'COUNT', count: 5 } }))).toBe(
      'FREQ=WEEKLY;COUNT=5',
    )
    expect(
      buildRrule(recurrence({ freq: 'WEEKLY', end: { type: 'UNTIL', until: '2026-09-15' } })),
    ).toMatch(/^FREQ=WEEKLY;UNTIL=\d{8}T\d{6}Z$/)
  })

  it('UNTIL neo vào cuối ngày để "đến 15/09" bao gồm cả ngày 15', () => {
    const built = buildRrule(
      recurrence({ freq: 'DAILY', end: { type: 'UNTIL', until: '2026-09-15' } }),
    )
    const stamp = built?.split('UNTIL=')[1] ?? ''
    const asDate = new Date(
      `${stamp.slice(0, 4)}-${stamp.slice(4, 6)}-${stamp.slice(6, 8)}T${stamp.slice(9, 11)}:${stamp.slice(11, 13)}:${stamp.slice(13, 15)}Z`,
    )

    expect(asDate.getTime()).toBeGreaterThan(new Date('2026-09-15T00:00:00').getTime())
  })
})

describe('parseRrule', () => {
  it('đọc lại đúng những gì buildRrule sinh ra', () => {
    const original = recurrence({
      freq: 'WEEKLY',
      interval: 2,
      byDay: ['MO', 'WE', 'FR'],
      end: { type: 'COUNT', count: 8 },
    })

    expect(parseRrule(buildRrule(original))).toEqual(original)
  })

  it('chấp nhận tiền tố RRULE: và chữ thường', () => {
    expect(parseRrule('rrule:freq=DAILY;interval=2')).toMatchObject({ freq: 'DAILY', interval: 2 })
  })

  it('quy luật rỗng hoặc không đọc được thì trở về không lặp', () => {
    expect(parseRrule(null)).toEqual(NO_RECURRENCE)
    expect(parseRrule('   ')).toEqual(NO_RECURRENCE)
    expect(parseRrule('KHONG-PHAI-RRULE')).toEqual(NO_RECURRENCE)
  })

  it('bỏ qua thứ lạ trong BYDAY thay vì hỏng cả form', () => {
    expect(parseRrule('FREQ=WEEKLY;BYDAY=MO,XX,FR').byDay).toEqual(['MO', 'FR'])
  })

  it('INTERVAL không hợp lệ lùi về 1', () => {
    expect(parseRrule('FREQ=DAILY;INTERVAL=0').interval).toBe(1)
    expect(parseRrule('FREQ=DAILY;INTERVAL=abc').interval).toBe(1)
  })

  it('COUNT được ưu tiên khi quy luật lỡ mang cả hai giới hạn', () => {
    // RFC 5545 cấm COUNT và UNTIL cùng xuất hiện; nếu gặp thì phải chọn một cách xác định.
    expect(parseRrule('FREQ=DAILY;COUNT=3;UNTIL=20260915T000000Z').end).toEqual({
      type: 'COUNT',
      count: 3,
    })
  })
})

describe('describeRrule', () => {
  it('mô tả bằng tiếng Việt đọc được, không phải chuỗi RRULE thô', () => {
    expect(describeRrule(null)).toBe('Không lặp lại')
    expect(describeRrule('FREQ=DAILY')).toBe('Hằng ngày')
    expect(describeRrule('FREQ=DAILY;INTERVAL=3')).toBe('Mỗi 3 ngày')
    expect(describeRrule('FREQ=MONTHLY')).toBe('Hằng tháng')
    expect(describeRrule('FREQ=YEARLY;INTERVAL=2')).toBe('Mỗi 2 năm')
  })

  it('liệt kê các thứ trong tuần theo đúng thứ tự lịch', () => {
    expect(describeRrule('FREQ=WEEKLY;BYDAY=WE,MO')).toBe('Hằng tuần vào thứ 2, thứ 4')
    expect(describeRrule('FREQ=WEEKLY;BYDAY=SU')).toBe('Hằng tuần vào chủ nhật')
  })

  it('nêu rõ điểm dừng của chuỗi', () => {
    expect(describeRrule('FREQ=WEEKLY;COUNT=10')).toBe('Hằng tuần, 10 lần')
    expect(describeRrule('FREQ=WEEKLY;UNTIL=20260915T120000Z')).toContain('đến 15/09/2026')
  })
})
