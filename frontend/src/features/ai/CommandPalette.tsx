import { useEffect, useRef, useState } from 'react'
import { CalendarDays, ListTodo, Loader2, Sparkles, Wallet, WifiOff } from 'lucide-react'
import { Button } from '@/shared/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/components/ui/dialog'
import { Input } from '@/shared/components/ui/input'
import { useAiStatus, useParseText } from './hooks'
import { ParseResultForm } from './ParseResultForm'
import { WARNING_LABELS, type ParseIntent, type ParseResult } from './types'

/**
 * Global natural language input, opened with Ctrl+Space (FR-AI-01, UC-09).
 *
 * <p>Two dialogs, never both: the input, and then the prefilled form the parse produced. The input
 * closes as the form opens so the user is looking at one thing at a time, and closing the form
 * without saving writes nothing - there is no draft record to clean up because none was ever made.
 *
 * <p>When AI is switched off or unconfigured, the palette still works: the backend answers from the
 * rule based parser and says so, and the banner explains why the result is rougher than usual
 * (FR-AI-08, FR-AI-12).
 */
export function CommandPalette() {
  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [result, setResult] = useState<ParseResult | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const { data: status } = useAiStatus()
  const parse = useParseText()

  // Ctrl+Space from anywhere, including from inside another dialog's text field.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.code === 'Space' && (event.ctrlKey || event.metaKey) && !event.altKey) {
        event.preventDefault()
        setResult(null)
        setText('')
        setOpen(true)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])

  const submit = async () => {
    if (!text.trim() || parse.isPending) {
      return
    }
    try {
      const parsed = await parse.mutateAsync(text.trim())
      setResult(parsed)
      // Only step aside once there is something to show; a failure leaves the text to retry.
      if (parsed.intent !== 'UNKNOWN') {
        setOpen(false)
      }
    } catch {
      // Reported by the mutation's onError; the palette stays open with the text intact.
    }
  }

  /** UC-09 alternate flow 9a: the user says which kind of record they meant. */
  const chooseIntent = (intent: Exclude<ParseIntent, 'UNKNOWN'>) => {
    setResult(manualDraft(intent, text.trim()))
    setOpen(false)
  }

  const offline = result?.source === 'RULE'

  return (
    <>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className="max-w-2xl"
          onOpenAutoFocus={(event) => {
            event.preventDefault()
            inputRef.current?.focus()
          }}
        >
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-violet-500" aria-hidden />
              Nhập nhanh
            </DialogTitle>
            <DialogDescription>
              Gõ tự nhiên bằng tiếng Việt, ví dụ{' '}
              <em>ăn trưa cơm gà 45k với team</em> hoặc{' '}
              <em>họp review sprint thứ 5 tuần sau 2h chiều nhắc trước 15 phút</em>.
            </DialogDescription>
          </DialogHeader>

          {status && !status.available && (
            <p className="flex items-start gap-2 rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
              <WifiOff className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
              {status.enabled
                ? 'Chưa có API key nên đang dùng chế độ ngoại tuyến. Kết quả có thể kém chính xác hơn — thêm key trong Cài đặt.'
                : 'AI đang tắt nên câu của bạn được phân tích bằng bộ luật ngoại tuyến. Bật lại trong Cài đặt.'}
            </p>
          )}

          <Input
            ref={inputRef}
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                void submit()
              }
            }}
            placeholder="Bạn muốn ghi lại điều gì?"
            aria-label="Câu cần phân tích"
            maxLength={500}
          />

          {parse.isPending && (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
              Đang phân tích…
            </p>
          )}

          {result?.intent === 'UNKNOWN' && !parse.isPending && (
            <div className="space-y-2">
              <p className="text-sm">Mình chưa hiểu ý bạn. Bạn muốn tạo gì?</p>
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" onClick={() => chooseIntent('TRANSACTION')}>
                  <Wallet className="mr-2 h-4 w-4" aria-hidden />
                  Giao dịch
                </Button>
                <Button type="button" variant="outline" onClick={() => chooseIntent('TASK')}>
                  <ListTodo className="mr-2 h-4 w-4" aria-hidden />
                  Công việc
                </Button>
                <Button type="button" variant="outline" onClick={() => chooseIntent('EVENT')}>
                  <CalendarDays className="mr-2 h-4 w-4" aria-hidden />
                  Sự kiện
                </Button>
              </div>
            </div>
          )}

          <p className="text-xs text-muted-foreground">
            <kbd className="rounded border border-border px-1">Enter</kbd> để phân tích,{' '}
            <kbd className="rounded border border-border px-1">Esc</kbd> để đóng. Không có gì được
            lưu cho tới khi bạn bấm Lưu trên form.
          </p>
        </DialogContent>
      </Dialog>

      {result && result.intent !== 'UNKNOWN' && (
        <>
          {offline && <OfflineBanner warning={result.warning} />}
          <ParseResultForm
            result={result}
            open
            onOpenChange={(next) => {
              if (!next) {
                setResult(null)
              }
            }}
          />
        </>
      )}
    </>
  )
}

/** FR-AI-08: says plainly that this result came from the offline parser, and why. */
function OfflineBanner({ warning }: { warning: ParseResult['warning'] }) {
  return (
    <div
      role="status"
      className="fixed left-1/2 top-4 z-[60] flex -translate-x-1/2 items-center gap-2 rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900 shadow-sm dark:border-amber-700 dark:bg-amber-950 dark:text-amber-200"
    >
      <WifiOff className="h-3.5 w-3.5 shrink-0" aria-hidden />
      <span>
        Đang dùng chế độ ngoại tuyến, kết quả có thể kém chính xác hơn
        {warning ? ` — ${WARNING_LABELS[warning]}` : ''}.
      </span>
    </div>
  )
}

/**
 * An empty draft of the type the user picked, carrying their original sentence.
 *
 * <p>Source is {@code RULE} because nothing was inferred - the text goes into the note or title
 * untouched, and every other field is left for the user. The confidence map is empty so no field
 * gets an AI badge it did not earn.
 */
function manualDraft(intent: Exclude<ParseIntent, 'UNKNOWN'>, text: string): ParseResult {
  const base: ParseResult = {
    intent,
    confidence: 0,
    source: 'RULE',
    transaction: null,
    task: null,
    event: null,
    warning: null,
  }

  if (intent === 'TRANSACTION') {
    return { ...base, transaction: { type: 'EXPENSE', note: text, fieldConfidence: {} } }
  }
  if (intent === 'TASK') {
    return { ...base, task: { title: text, priority: 'MEDIUM', fieldConfidence: {} } }
  }

  const start = new Date()
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  return {
    ...base,
    event: {
      title: text,
      startAt: toLocalIso(start),
      endAt: toLocalIso(end),
      reminderOffsetMinutes: [15],
      fieldConfidence: {},
    },
  }
}

/** Local wall-clock ISO, matching what the backend sends for a draft. */
function toLocalIso(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
