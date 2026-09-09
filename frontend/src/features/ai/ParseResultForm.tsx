import { EventFormDialog } from '@/features/calendar/components/EventFormDialog'
import { TransactionFormDialog } from '@/features/finance/components/TransactionFormDialog'
import { TaskFormDialog } from '@/features/tasks/components/TaskFormDialog'
import { withoutUncertainValues } from './components/AiFieldHint'
import type { EventDraft, ParseResult, TaskDraft, TransactionDraft } from './types'

/**
 * Shows a parse result as the ordinary create form, prefilled (FR-AI-06, UC-09 step 10).
 *
 * <p>Deliberately not a form of its own. Reusing the real dialogs means the AI path gets the same
 * Zod validation, the same wallet and category pickers and the same save mutation as typing the
 * record by hand - so there is exactly one way a transaction can be created, and the AI simply
 * fills in the boxes.
 *
 * <p>That reuse is also what makes the strongest guarantee here free: nothing is written until the
 * user presses Save. Pressing Escape closes a dialog that never issued a request, which is the
 * acceptance criterion checked directly against the database (AGENTS.md 3.4 rule 1).
 */
export function ParseResultForm({
  result,
  open,
  onOpenChange,
}: {
  result: ParseResult
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  if (result.intent === 'TRANSACTION' && result.transaction) {
    return (
      <TransactionFormDialog
        open={open}
        onOpenChange={onOpenChange}
        defaults={transactionDefaults(result.transaction)}
        aiConfidence={result.transaction.fieldConfidence}
      />
    )
  }

  if (result.intent === 'TASK' && result.task) {
    return (
      <TaskFormDialog
        open={open}
        onOpenChange={onOpenChange}
        defaults={taskDefaults(result.task)}
        aiConfidence={result.task.fieldConfidence}
      />
    )
  }

  if (result.intent === 'EVENT' && result.event) {
    return (
      <EventFormDialog
        open={open}
        onOpenChange={onOpenChange}
        defaults={eventDefaults(result.event)}
        defaultReminderOffsets={result.event.reminderOffsetMinutes}
        aiConfidence={result.event.fieldConfidence}
        // A brand new event is never part of a series, so the scope question cannot arise.
        requestScope={async () => null}
      />
    )
  }

  return null
}

function transactionDefaults(draft: TransactionDraft) {
  return withoutUncertainValues(
    {
      type: draft.type,
      amount: draft.amount ?? 0,
      walletId: draft.walletId ?? '',
      categoryId: draft.categoryId ?? undefined,
      note: draft.note ?? '',
      occurredAt: toLocalInput(draft.occurredAt),
      tagIds: [] as string[],
    },
    // The confidence map is keyed by the model's field names; `categoryName` is what it reports
    // about the value that became `categoryId` here, so it is renamed before the blanking rule
    // is applied or an uncertain category would be prefilled anyway.
    renameKeys(draft.fieldConfidence, { categoryName: 'categoryId' }),
  )
}

function taskDefaults(draft: TaskDraft) {
  return withoutUncertainValues(
    {
      title: draft.title,
      priority: draft.priority,
      dueAt: toLocalInput(draft.dueAt),
      projectId: draft.projectId ?? '',
      description: '',
      estimateMinutes: '',
      tagIds: [] as string[],
    },
    draft.fieldConfidence,
  )
}

function eventDefaults(draft: EventDraft) {
  return withoutUncertainValues(
    {
      title: draft.title,
      startAt: toLocalInput(draft.startAt),
      endAt: toLocalInput(draft.endAt),
      location: draft.location ?? '',
      description: '',
      allDay: false,
    },
    draft.fieldConfidence,
  )
}

/** Copies a confidence map, moving the scores the model reports onto the form's own field names. */
function renameKeys(
  confidence: Record<string, number>,
  mapping: Record<string, string>,
): Record<string, number> {
  const renamed: Record<string, number> = {}
  for (const [key, value] of Object.entries(confidence)) {
    renamed[mapping[key] ?? key] = value
  }
  return renamed
}

/**
 * ISO instant with offset to the {@code yyyy-MM-ddTHH:mm} a datetime-local input expects.
 *
 * <p>The backend already rendered the value in the display timezone, so the local part is taken as
 * written rather than re-converted through {@code Date} - which would shift it by the machine
 * offset a second time.
 */
function toLocalInput(iso?: string): string {
  return iso ? iso.slice(0, 16) : ''
}
