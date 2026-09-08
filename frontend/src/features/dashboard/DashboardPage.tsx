import { AlertCircle, CheckCircle2, Database, Loader2 } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/components/ui/card'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { useBootstrap } from './useBootstrap'

/**
 * Phase 0 landing page.
 *
 * Its job is to prove the whole chain end to end: renderer → HTTP with the shared token →
 * Spring → SQLite. It therefore renders the settings that came back from the database
 * verbatim, rather than a static "Hello".
 */
export function DashboardPage() {
  const { data, isPending, error, refetch, isFetching } = useBootstrap()

  if (isPending) {
    return (
      <CenteredState>
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" aria-hidden />
        <p className="text-sm text-muted-foreground">Đang tải dữ liệu khởi động…</p>
      </CenteredState>
    )
  }

  if (error) {
    const apiError = error instanceof ApiRequestError ? error : null
    return (
      <CenteredState>
        <AlertCircle className="h-8 w-8 text-destructive" aria-hidden />
        <div className="space-y-1 text-center">
          <p className="font-medium">Không tải được dữ liệu khởi động</p>
          <p className="text-sm text-muted-foreground">
            {apiError?.message ?? 'Dịch vụ nền chưa sẵn sàng.'}
          </p>
          {apiError?.traceId && (
            <p className="font-mono text-xs text-muted-foreground">Mã lỗi: {apiError.traceId}</p>
          )}
        </div>
        <Button onClick={() => void refetch()} disabled={isFetching}>
          {isFetching ? 'Đang thử lại…' : 'Thử lại'}
        </Button>
      </CenteredState>
    )
  }

  const settingEntries = Object.entries(data.settings)

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-8">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Tổng quan</h1>
        <p className="text-sm text-muted-foreground">
          Phase 0 — khung sườn đã chạy. Các module nghiệp vụ sẽ xuất hiện ở những phase sau.
        </p>
      </header>

      <Card>
        <CardHeader className="flex-row items-center gap-3 space-y-0">
          <CheckCircle2 className="h-5 w-5 text-primary" aria-hidden />
          <div>
            <CardTitle>Kết nối thành công</CardTitle>
            <CardDescription>
              Giao diện đã gọi được <code className="font-mono text-xs">GET /api/v1/bootstrap</code> kèm
              header token và nhận dữ liệu từ SQLite.
            </CardDescription>
          </div>
        </CardHeader>
      </Card>

      <Card>
        <CardHeader className="flex-row items-center gap-3 space-y-0">
          <Database className="h-5 w-5 text-muted-foreground" aria-hidden />
          <div>
            <CardTitle>Cấu hình đọc từ database</CardTitle>
            <CardDescription>
              {settingEntries.length} bản ghi trong bảng <code className="font-mono text-xs">setting</code>,
              do migration <code className="font-mono text-xs">V1__init_core.sql</code> tạo.
            </CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          <dl className="divide-y divide-border rounded-md border border-border">
            {settingEntries.map(([key, value]) => (
              <div key={key} className="flex items-center justify-between gap-4 px-4 py-2.5 text-sm">
                <dt className="font-mono text-xs text-muted-foreground">{key}</dt>
                <dd className="font-medium">{value}</dd>
              </div>
            ))}
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Chưa có ở phase này</CardTitle>
          <CardDescription>Các phần dưới đây trả về giá trị rỗng cho tới đúng phase của chúng.</CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="space-y-2 text-sm text-muted-foreground">
            <li>
              Nhắc hẹn bị lỡ: <strong>{data.missedReminders.length}</strong> — bắt đầu từ Phase 2
            </li>
            <li>
              Số liệu tổng quan: <strong>{data.dashboard ? 'có' : 'chưa có'}</strong> — bắt đầu từ Phase 3
            </li>
            <li>
              AI đã cấu hình: <strong>{data.aiConfigured ? 'rồi' : 'chưa'}</strong> — bắt đầu từ Phase 4
            </li>
          </ul>
        </CardContent>
      </Card>
    </div>
  )
}

function CenteredState({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8">{children}</div>
  )
}
