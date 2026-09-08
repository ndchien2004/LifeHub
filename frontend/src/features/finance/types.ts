/** Wire types for the finance module (06-API-SPEC.md section 7). */

export const WALLET_TYPES = ['CASH', 'BANK', 'E_WALLET', 'CREDIT'] as const
export type WalletType = (typeof WALLET_TYPES)[number]

export const TRANSACTION_TYPES = ['EXPENSE', 'INCOME', 'TRANSFER'] as const
export type TransactionType = (typeof TRANSACTION_TYPES)[number]

export type CategoryType = 'INCOME' | 'EXPENSE'
export type TransactionSource = 'MANUAL' | 'AI_PARSE' | 'CSV_IMPORT' | 'RECURRING'

export const BUDGET_PERIODS = ['WEEKLY', 'MONTHLY', 'YEARLY'] as const
export type BudgetPeriod = (typeof BUDGET_PERIODS)[number]

export type BudgetAlertLevel = 'WARNING' | 'EXCEEDED'
export type SummaryGroupBy = 'CATEGORY' | 'DAY' | 'WEEK' | 'MONTH' | 'WALLET'

export interface Wallet {
  id: string
  name: string
  type: WalletType
  initialBalance: number
  balance: number
  currency: string
  icon?: string
  isDefault: boolean
  sortOrder: number
  createdAt: string
}

export interface WalletList {
  wallets: Wallet[]
  totalAssets: number
}

export interface Category {
  id: string
  parentId?: string
  name: string
  type: CategoryType
  icon?: string
  color: string
  isSystem: boolean
  sortOrder: number
  children?: Category[]
}

export interface WalletRef {
  id: string
  name: string
  icon?: string
}

export interface CategoryRef {
  id: string
  name: string
  icon?: string
  color: string
  parentName?: string
}

export interface TagRef {
  id: string
  name: string
  color: string
}

export interface Transaction {
  id: string
  type: TransactionType
  /** Whole đồng. Never a decimal — see FR-FIN-06. */
  amount: number
  wallet: WalletRef
  toWallet?: WalletRef
  category?: CategoryRef
  note?: string
  occurredAt: string
  source: TransactionSource
  aiConfidence?: number
  recurringRuleId?: string
  tags: TagRef[]
  createdAt: string
  updatedAt: string
  deletedAt?: string
}

export interface BudgetAlert {
  budgetId: string
  categoryId: string
  categoryName: string
  usage: number
  level: BudgetAlertLevel
  limitAmount: number
  spentAmount: number
}

/** What POST/PATCH/DELETE on a transaction returns (SD-01). */
export interface TransactionWriteResult {
  transaction: Transaction
  walletBalance: number
  toWalletBalance?: number
  budgetAlert?: BudgetAlert
}

export interface PagedTransactions {
  items: Transaction[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface SummaryGroup {
  key: string
  label: string
  color?: string
  amount: number
  percentage: number
  transactionCount: number
}

export interface Summary {
  totalIncome: number
  totalExpense: number
  net: number
  groups: SummaryGroup[]
}

export interface Budget {
  id: string
  category: CategoryRef
  limitAmount: number
  spentAmount: number
  remainingAmount: number
  usage: number
  level?: BudgetAlertLevel
  period: BudgetPeriod
  startDate: string
  periodStart: string
  periodEnd: string
  isActive: boolean
}

export interface RecurringRule {
  id: string
  rrule: string
  nextRunDate?: string
  lastRunDate?: string
  isActive: boolean
  template?: TransactionInput
}

export interface TransactionInput {
  type: TransactionType
  amount: number
  walletId: string
  toWalletId?: string | null
  categoryId?: string | null
  note?: string | null
  occurredAt?: string
  tagIds?: string[]
  source?: TransactionSource
  aiConfidence?: number | null
}

/** Everything the filter bar can constrain on (FR-FIN-12). */
export interface TransactionQuery {
  from?: string
  to?: string
  walletIds?: string[]
  categoryIds?: string[]
  type?: TransactionType[]
  source?: TransactionSource[]
  tagIds?: string[]
  minAmount?: number
  maxAmount?: number
  q?: string
  page?: number
  size?: number
  sort?: string
}

export const WALLET_TYPE_LABELS: Record<WalletType, string> = {
  CASH: 'Tiền mặt',
  BANK: 'Ngân hàng',
  E_WALLET: 'Ví điện tử',
  CREDIT: 'Thẻ tín dụng',
}

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  EXPENSE: 'Chi',
  INCOME: 'Thu',
  TRANSFER: 'Chuyển khoản',
}

export const BUDGET_PERIOD_LABELS: Record<BudgetPeriod, string> = {
  WEEKLY: 'Hàng tuần',
  MONTHLY: 'Hàng tháng',
  YEARLY: 'Hàng năm',
}

export const SOURCE_LABELS: Record<TransactionSource, string> = {
  MANUAL: 'Nhập tay',
  AI_PARSE: 'AI',
  CSV_IMPORT: 'Nhập từ CSV',
  RECURRING: 'Định kỳ',
}

/** Colour per transaction type, used by both the list and the form. */
export const TYPE_CLASSES: Record<TransactionType, string> = {
  EXPENSE: 'text-red-600 dark:text-red-400',
  INCOME: 'text-emerald-600 dark:text-emerald-400',
  TRANSFER: 'text-slate-600 dark:text-slate-300',
}
