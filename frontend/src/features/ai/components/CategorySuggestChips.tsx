import { Sparkles } from 'lucide-react'
import type { CategoryType } from '@/features/finance/types'
import { useCategorySuggestions } from '../hooks'

/**
 * Category chips under the picker in the transaction form (FR-AI-07, UC-10).
 *
 * <p>Renders nothing at all when there is nothing to offer - no suggestions, a failed call, AI
 * switched off. That is UC-10 exception E1: this is a convenience beneath a field the user can
 * perfectly well fill in themselves, so its failure mode is silence, not an error.
 *
 * <p>The debounce lives in {@code useCategorySuggestions}, so this component simply passes the note
 * through on every keystroke and the hook decides when a request is worth making.
 */
export function CategorySuggestChips({
  note,
  type,
  enabled,
  onSelect,
}: {
  note: string
  type: CategoryType
  /** False while the field already has a value, or the dialog is closed. */
  enabled: boolean
  onSelect: (categoryId: string) => void
}) {
  const { data: suggestions = [] } = useCategorySuggestions(note, type, enabled)

  if (!enabled || suggestions.length === 0) {
    return null
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
      <Sparkles className="h-3 w-3 shrink-0 text-violet-500" aria-hidden />
      <span className="text-xs text-muted-foreground">Gợi ý:</span>
      {suggestions.map((suggestion) => (
        <button
          key={suggestion.categoryId}
          type="button"
          onClick={() => onSelect(suggestion.categoryId)}
          title={`Độ tin cậy: ${Math.round(suggestion.confidence * 100)}%`}
          className="rounded-full border border-violet-300 px-2.5 py-0.5 text-xs text-violet-700 transition-colors hover:bg-violet-50 dark:border-violet-700 dark:text-violet-300 dark:hover:bg-violet-900/40"
        >
          {suggestion.categoryName}
        </button>
      ))}
    </div>
  )
}
