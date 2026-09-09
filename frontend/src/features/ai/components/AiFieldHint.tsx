import { Sparkles, TriangleAlert } from 'lucide-react'
import { cn } from '@/shared/lib/utils'
import { CONFIDENCE_THRESHOLD, type FieldConfidence } from '../types'

/**
 * Marks a form field that the AI filled in (FR-AI-06, UC-09 step 10).
 *
 * <p>Two states, and the difference matters. A confident field is prefilled and badged, so the user
 * knows to glance at it rather than trust it blindly. An unconfident one is left <em>blank</em> and
 * badged in amber - UC-09 alternate flow 10a - because a wrong value the user does not notice is
 * worse than an empty one that asks them a question.
 */
export function AiFieldHint({ confidence }: { confidence?: number }) {
  if (confidence === undefined) {
    return null
  }

  const uncertain = confidence < CONFIDENCE_THRESHOLD
  const percent = Math.round(confidence * 100)

  return (
    <span
      title={`Độ tin cậy của AI: ${percent}%`}
      className={cn(
        'inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] font-medium',
        uncertain
          ? 'bg-amber-100 text-amber-800 dark:bg-amber-900/50 dark:text-amber-300'
          : 'bg-violet-100 text-violet-800 dark:bg-violet-900/50 dark:text-violet-300',
      )}
    >
      {uncertain ? (
        <TriangleAlert className="h-2.5 w-2.5" aria-hidden />
      ) : (
        <Sparkles className="h-2.5 w-2.5" aria-hidden />
      )}
      {uncertain ? 'Cần kiểm tra' : `AI ${percent}%`}
    </span>
  )
}

/** Ring highlight for an input the AI left blank because it was not sure enough. */
export function aiFieldClass(confidence?: number): string | undefined {
  if (confidence === undefined || confidence >= CONFIDENCE_THRESHOLD) {
    return undefined
  }
  return 'ring-2 ring-amber-400/70 focus-visible:ring-amber-400'
}

/**
 * Drops values the parser was not sure enough about.
 *
 * <p>Applied once, where the draft becomes form defaults, so no screen has to remember the rule.
 * Fields with no confidence entry are kept: a parser that says nothing about a field has not
 * expressed doubt about it, it simply took the value from somewhere else - the user's default
 * wallet, for instance.
 */
export function withoutUncertainValues<T extends Record<string, unknown>>(
  values: T,
  confidence: FieldConfidence,
): T {
  const kept = { ...values }
  for (const [field, score] of Object.entries(confidence)) {
    if (score < CONFIDENCE_THRESHOLD && field in kept) {
      delete kept[field]
    }
  }
  return kept
}
