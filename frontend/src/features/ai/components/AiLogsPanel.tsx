import { useState } from 'react'
import { Button } from '@/shared/components/ui/button'
import { formatDateTime } from '@/shared/lib/dateUtils'
import { cn } from '@/shared/lib/utils'
import { useAiLogs } from '../hooks'
import { WARNING_LABELS } from '../types'

/**
 * The AI call log, newest first (FR-AI-10).
 *
 * <p>Exists to answer "why did it give me that?" - so it shows the sentence that went in, the model
 * that answered, how long it took and what came back. The API key is not in the table and cannot
 * be: it never leaves the Electron credential store, and the backend never writes it anywhere
 * (NFR-SEC-01).
 */
export function AiLogsPanel() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAiLogs(page)

  const entries = data?.items ?? []

  return (
    <div className="space-y-3">
      {isLoading && <p className="text-sm text-muted-foreground">Đang tải nhật ký…</p>}

      {!isLoading && entries.length === 0 && (
        <p className="text-sm text-muted-foreground">
          Chưa có lần gọi nào. Thử nhập nhanh bằng <kbd className="rounded border border-border px-1">Ctrl</kbd>{' '}
          + <kbd className="rounded border border-border px-1">Space</kbd>.
        </p>
      )}

      {entries.length > 0 && (
        <div className="overflow-x-auto rounded-md border border-border">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-muted/60 text-xs text-muted-foreground">
              <tr>
                <th className="px-3 py-2 text-left font-medium">Thời điểm</th>
                <th className="px-3 py-2 text-left font-medium">Loại</th>
                <th className="px-3 py-2 text-left font-medium">Câu nhập</th>
                <th className="px-3 py-2 text-left font-medium">Kết quả</th>
                <th className="px-3 py-2 text-right font-medium">Độ trễ</th>
                <th className="px-3 py-2 text-right font-medium">Token</th>
                <th className="px-3 py-2 text-left font-medium">Model</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id} className="border-t border-border align-top">
                  <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                    {formatDateTime(entry.createdAt)}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                    {entry.requestType}
                  </td>
                  <td className="max-w-xs px-3 py-2">
                    <span className="line-clamp-2">{entry.inputText ?? '—'}</span>
                  </td>
                  <td className="px-3 py-2">
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs',
                        entry.success
                          ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/50 dark:text-emerald-300'
                          : 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-300',
                      )}
                    >
                      {entry.success
                        ? (entry.intent ?? 'OK')
                        : entry.errorCode
                          ? WARNING_LABELS[entry.errorCode]
                          : 'AI đang tắt'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums text-muted-foreground">
                    {entry.latencyMs != null ? `${entry.latencyMs} ms` : '—'}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums text-muted-foreground">
                    {entry.tokenInput != null || entry.tokenOutput != null
                      ? `${entry.tokenInput ?? 0} / ${entry.tokenOutput ?? 0}`
                      : '—'}
                  </td>
                  <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                    {entry.model ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between text-sm">
          <span className="text-muted-foreground">
            Trang {data.page + 1} / {data.totalPages} · {data.totalItems} bản ghi
          </span>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((current) => Math.max(0, current - 1))}
            >
              Trước
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Sau
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
