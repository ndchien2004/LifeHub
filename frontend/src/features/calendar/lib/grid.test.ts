import { describe, expect, it } from 'vitest'
import { blockPosition, itemsOnDay, monthGrid, rangeFor, shiftAnchor, weekGrid } from './grid'
import type { CalendarItem } from '../types'

/** Grid arithmetic behind the three calendar views (FR-CAL-02, FR-CAL-10). */

function item(overrides: Partial<CalendarItem>): CalendarItem {
  return {
    kind: 'EVENT',
    eventId: 'e1',
    occurrenceStart: '2026-09-15T09:00:00+07:00',
    title: 'Họp',
    startAt: '2026-09-15T09:00:00+07:00',
    endAt: '2026-09-15T10:00:00+07:00',
    allDay: false,
    isRecurring: false,
    isException: false,
    hasConflict: false,
    reminders: [],
    ...overrides,
  }
}

/** A local wall-clock date, so the assertions do not depend on the machine's offset. */
function local(year: number, month: number, day: number, hour = 0, minute = 0): Date {
  return new Date(year, month - 1, day, hour, minute)
}

function localIso(year: number, month: number, day: number, hour = 0, minute = 0): string {
  return local(year, month, day, hour, minute).toISOString()
}

describe('monthGrid', () => {
  it('luôn trả về các tuần trọn vẹn bắt đầu từ thứ 2', () => {
    const days = monthGrid(local(2026, 9, 15))

    expect(days.length % 7).toBe(0)
    expect(days[0]!.getDay()).toBe(1)
    expect(days[days.length - 1]!.getDay()).toBe(0)
  })

  it('phủ cả những ngày đầu và cuối thuộc tháng bên cạnh', () => {
    const days = monthGrid(local(2026, 9, 15))

    // 01/09/2026 là thứ 3, nên ô đầu lưới phải là 31/08 của tháng trước.
    expect(days[0]!.getMonth()).toBe(7)
    expect(days[0]!.getDate()).toBe(31)
  })
})

describe('rangeFor', () => {
  it('cửa sổ tháng bao trọn lưới đang vẽ, không chỉ tháng lịch', () => {
    const { from, to } = rangeFor('month', local(2026, 9, 15))

    expect(from.getTime()).toBeLessThanOrEqual(local(2026, 9, 1).getTime())
    expect(to.getTime()).toBeGreaterThanOrEqual(local(2026, 9, 30).getTime())
  })

  it('cửa sổ ngày gói gọn trong đúng ngày đó', () => {
    const { from, to } = rangeFor('day', local(2026, 9, 15, 14))

    expect(from.getDate()).toBe(15)
    expect(to.getDate()).toBe(15)
    expect(from.getHours()).toBe(0)
  })
})

describe('weekGrid', () => {
  it('trả về đúng 7 ngày, bắt đầu từ thứ 2', () => {
    const days = weekGrid(local(2026, 9, 17))

    expect(days).toHaveLength(7)
    expect(days[0]!.getDay()).toBe(1)
  })
})

describe('shiftAnchor', () => {
  it('nhảy theo đúng đơn vị của chế độ xem', () => {
    const anchor = local(2026, 9, 15)

    expect(shiftAnchor('day', anchor, 1).getDate()).toBe(16)
    expect(shiftAnchor('week', anchor, 1).getDate()).toBe(22)
    expect(shiftAnchor('month', anchor, -1).getMonth()).toBe(7)
  })
})

describe('itemsOnDay', () => {
  it('sự kiện kéo dài nhiều ngày hiện trên mọi ngày nó phủ qua', () => {
    const holiday = item({
      startAt: localIso(2026, 9, 14, 8),
      endAt: localIso(2026, 9, 16, 18),
    })

    expect(itemsOnDay([holiday], local(2026, 9, 14))).toHaveLength(1)
    expect(itemsOnDay([holiday], local(2026, 9, 15))).toHaveLength(1)
    expect(itemsOnDay([holiday], local(2026, 9, 16))).toHaveLength(1)
    expect(itemsOnDay([holiday], local(2026, 9, 17))).toHaveLength(0)
  })

  it('hạn chót của task là một thời điểm nên chỉ hiện đúng ngày của nó', () => {
    const deadline = item({
      kind: 'TASK',
      eventId: undefined,
      startAt: localIso(2026, 9, 15, 17),
      endAt: localIso(2026, 9, 15, 17),
    })

    expect(itemsOnDay([deadline], local(2026, 9, 15))).toHaveLength(1)
    expect(itemsOnDay([deadline], local(2026, 9, 16))).toHaveLength(0)
  })

  it('sự kiện kết thúc đúng nửa đêm không tràn sang ngày hôm sau', () => {
    const allDay = item({
      allDay: true,
      startAt: localIso(2026, 9, 15),
      endAt: localIso(2026, 9, 16),
    })

    expect(itemsOnDay([allDay], local(2026, 9, 15))).toHaveLength(1)
    expect(itemsOnDay([allDay], local(2026, 9, 16))).toHaveLength(0)
  })
})

describe('blockPosition', () => {
  it('đặt khối đúng vị trí theo giờ trong ngày', () => {
    const meeting = item({
      startAt: localIso(2026, 9, 15, 6),
      endAt: localIso(2026, 9, 15, 12),
    })

    const { top, height } = blockPosition(meeting, local(2026, 9, 15))

    expect(top).toBeCloseTo(0.25, 5)
    expect(height).toBeCloseTo(0.25, 5)
  })

  it('cắt khối tại rìa cột khi sự kiện kéo qua nửa đêm', () => {
    const overnight = item({
      startAt: localIso(2026, 9, 15, 22),
      endAt: localIso(2026, 9, 16, 6),
    })

    const { top, height } = blockPosition(overnight, local(2026, 9, 15))

    expect(top + height).toBeLessThanOrEqual(1)
  })

  it('cho sự kiện rất ngắn một chiều cao tối thiểu để vẫn bấm được', () => {
    const quick = item({
      startAt: localIso(2026, 9, 15, 9),
      endAt: localIso(2026, 9, 15, 9),
    })

    expect(blockPosition(quick, local(2026, 9, 15)).height).toBeGreaterThan(0)
  })
})
