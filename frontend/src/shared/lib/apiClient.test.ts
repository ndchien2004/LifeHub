import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError, apiFetch, resetBackendInfo } from './apiClient'
import { stubLifeHubBridge } from '@/test/lifehubBridge'

describe('apiClient', () => {
  beforeEach(() => {
    resetBackendInfo()
    stubLifeHubBridge()
  })

  afterEach(() => {
    delete window.lifehub
    vi.unstubAllGlobals()
  })

  function mockResponse(status: number, body: unknown) {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: async () => body,
    })
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }

  it('gọi đúng cổng do Electron cấp và gắn header X-App-Token (NFR-SEC-04)', async () => {
    const fetchMock = mockResponse(200, { success: true, data: { settings: {} } })

    await apiFetch('/bootstrap')

    const call = fetchMock.mock.calls[0]
    expect(call).toBeDefined()
    const [url, init] = call!
    expect(url).toBe('http://127.0.0.1:51234/api/v1/bootstrap')
    expect((init.headers as Record<string, string>)['X-App-Token']).toBe('secret-token')
  })

  it('trả về phần data, không phải cả phong bì response', async () => {
    mockResponse(200, { success: true, data: { settings: { 'app.theme': 'SYSTEM' } } })

    await expect(apiFetch('/bootstrap')).resolves.toEqual({ settings: { 'app.theme': 'SYSTEM' } })
  })

  it('ném ApiRequestError giữ nguyên code và traceId khi backend trả lỗi', async () => {
    mockResponse(401, {
      success: false,
      error: { code: 'UNAUTHORIZED', message: 'Phiên làm việc không hợp lệ.', traceId: '01J-abc' },
    })

    await expect(apiFetch('/bootstrap')).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 401,
      code: 'UNAUTHORIZED',
      traceId: '01J-abc',
    })
  })

  it('body không parse được vẫn thành lỗi có thông báo tiếng Việt, không phải crash', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: async () => {
          throw new Error('not json')
        },
      }),
    )

    await expect(apiFetch('/bootstrap')).rejects.toBeInstanceOf(ApiRequestError)
    await expect(apiFetch('/bootstrap')).rejects.toThrow(/khởi động lại/)
  })

  it('chỉ hỏi Electron một lần rồi dùng lại thông tin đã lấy', async () => {
    mockResponse(200, { success: true, data: {} })

    await apiFetch('/bootstrap')
    await apiFetch('/bootstrap')

    expect(window.lifehub!.getBackendInfo).toHaveBeenCalledTimes(1)
  })
})
