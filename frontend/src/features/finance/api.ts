import { apiFetch } from '@/shared/lib/apiClient'
import type {
  Budget,
  BudgetPeriod,
  Category,
  CategoryType,
  PagedTransactions,
  RecurringRule,
  Summary,
  SummaryGroupBy,
  Transaction,
  TransactionInput,
  TransactionQuery,
  TransactionType,
  TransactionWriteResult,
  Wallet,
  WalletList,
  WalletType,
} from './types'

/** REST calls for the finance module. Query keys live next to them so invalidation stays honest. */

export const walletKeys = { all: ['wallets'] as const }
export const categoryKeys = {
  all: ['categories'] as const,
  byType: (type?: CategoryType) => ['categories', type ?? 'ALL'] as const,
}
export const budgetKeys = { all: ['budgets'] as const }
export const recurringKeys = { all: ['recurring-rules'] as const }
export const transactionKeys = {
  all: ['transactions'] as const,
  list: (query: TransactionQuery) => ['transactions', 'list', query] as const,
  summary: (params: SummaryParams) => ['transactions', 'summary', params] as const,
}

export interface SummaryParams {
  from?: string
  to?: string
  groupBy?: SummaryGroupBy
  type?: TransactionType
}

/**
 * Builds the query string for GET /transactions.
 *
 * URLSearchParams percent-encodes values, which matters for the ISO timestamps: a raw
 * {@code +07:00} offset would decode to a space and the backend would reject it.
 */
function buildQuery(query: TransactionQuery): string {
  const params = new URLSearchParams()

  if (query.from) params.set('from', query.from)
  if (query.to) params.set('to', query.to)
  if (query.walletIds?.length) params.set('walletIds', query.walletIds.join(','))
  if (query.categoryIds?.length) params.set('categoryIds', query.categoryIds.join(','))
  if (query.type?.length) params.set('type', query.type.join(','))
  if (query.source?.length) params.set('source', query.source.join(','))
  if (query.tagIds?.length) params.set('tagIds', query.tagIds.join(','))
  if (query.minAmount != null) params.set('minAmount', String(query.minAmount))
  if (query.maxAmount != null) params.set('maxAmount', String(query.maxAmount))
  if (query.q?.trim()) params.set('q', query.q.trim())
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? 50))
  if (query.sort) params.set('sort', query.sort)

  return params.toString()
}

export function fetchTransactions(query: TransactionQuery): Promise<PagedTransactions> {
  return apiFetch<PagedTransactions>(`/transactions?${buildQuery(query)}`)
}

export function fetchTransaction(id: string): Promise<Transaction> {
  return apiFetch<Transaction>(`/transactions/${id}`)
}

export function createTransaction(input: TransactionInput): Promise<TransactionWriteResult> {
  return apiFetch<TransactionWriteResult>('/transactions', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateTransaction(
  id: string,
  input: Partial<TransactionInput>,
): Promise<TransactionWriteResult> {
  return apiFetch<TransactionWriteResult>(`/transactions/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteTransaction(id: string): Promise<TransactionWriteResult> {
  return apiFetch<TransactionWriteResult>(`/transactions/${id}`, { method: 'DELETE' })
}

export function restoreTransaction(id: string): Promise<TransactionWriteResult> {
  return apiFetch<TransactionWriteResult>(`/transactions/${id}/restore`, { method: 'POST' })
}

export function fetchSummary(params: SummaryParams): Promise<Summary> {
  const search = new URLSearchParams()
  if (params.from) search.set('from', params.from)
  if (params.to) search.set('to', params.to)
  if (params.groupBy) search.set('groupBy', params.groupBy)
  if (params.type) search.set('type', params.type)
  return apiFetch<Summary>(`/transactions/summary?${search.toString()}`)
}

export function fetchWallets(): Promise<WalletList> {
  return apiFetch<WalletList>('/wallets')
}

export interface WalletInput {
  name: string
  type?: WalletType
  initialBalance?: number
  currency?: string
  icon?: string | null
  isDefault?: boolean
}

export function createWallet(input: WalletInput): Promise<Wallet> {
  return apiFetch<Wallet>('/wallets', { method: 'POST', body: JSON.stringify(input) })
}

export function updateWallet(id: string, input: Partial<WalletInput>): Promise<Wallet> {
  return apiFetch<Wallet>(`/wallets/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteWallet(id: string): Promise<void> {
  return apiFetch<void>(`/wallets/${id}`, { method: 'DELETE' })
}

export function fetchCategories(type?: CategoryType): Promise<Category[]> {
  return apiFetch<Category[]>(`/categories${type ? `?type=${type}` : ''}`)
}

export interface CategoryInput {
  name: string
  type?: CategoryType
  parentId?: string | null
  icon?: string | null
  color?: string
}

export function createCategory(input: CategoryInput): Promise<Category> {
  return apiFetch<Category>('/categories', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCategory(id: string, input: Partial<CategoryInput>): Promise<Category> {
  return apiFetch<Category>(`/categories/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteCategory(id: string): Promise<void> {
  return apiFetch<void>(`/categories/${id}`, { method: 'DELETE' })
}

export function fetchBudgets(): Promise<Budget[]> {
  return apiFetch<Budget[]>('/budgets')
}

export interface BudgetInput {
  categoryId: string
  limitAmount: number
  period?: BudgetPeriod
  startDate?: string
  isActive?: boolean
}

export function createBudget(input: BudgetInput): Promise<Budget> {
  return apiFetch<Budget>('/budgets', { method: 'POST', body: JSON.stringify(input) })
}

export function updateBudget(id: string, input: Partial<BudgetInput>): Promise<Budget> {
  return apiFetch<Budget>(`/budgets/${id}`, { method: 'PATCH', body: JSON.stringify(input) })
}

export function deleteBudget(id: string): Promise<void> {
  return apiFetch<void>(`/budgets/${id}`, { method: 'DELETE' })
}

export function fetchRecurringRules(): Promise<RecurringRule[]> {
  return apiFetch<RecurringRule[]>('/recurring-rules')
}

export function createRecurringRule(input: {
  rrule: string
  startDate?: string
  template: TransactionInput
}): Promise<RecurringRule> {
  return apiFetch<RecurringRule>('/recurring-rules', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateRecurringRule(
  id: string,
  input: { rrule?: string; isActive?: boolean; nextRunDate?: string },
): Promise<RecurringRule> {
  return apiFetch<RecurringRule>(`/recurring-rules/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function deleteRecurringRule(id: string): Promise<void> {
  return apiFetch<void>(`/recurring-rules/${id}`, { method: 'DELETE' })
}

export function runRecurringRules(): Promise<Transaction[]> {
  return apiFetch<Transaction[]>('/recurring-rules/run', { method: 'POST' })
}
