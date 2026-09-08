import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query'
import { bootstrapQueryKey } from '@/features/dashboard/api'
import { toast } from '@/shared/components/ui/toast'
import { ApiRequestError } from '@/shared/lib/apiClient'
import { formatCurrency } from '@/shared/lib/formatters'
import * as api from './api'
import {
  budgetKeys,
  categoryKeys,
  recurringKeys,
  transactionKeys,
  walletKeys,
  type BudgetInput,
  type CategoryInput,
  type SummaryParams,
  type WalletInput,
} from './api'
import type {
  BudgetAlert,
  CategoryType,
  Transaction,
  TransactionInput,
  TransactionQuery,
} from './types'

/**
 * Data hooks for the finance module.
 *
 * <p>Every write invalidates the same four things, because they are all views of the same
 * ledger: the transaction list, the derived wallet balances, budget usage, and the dashboard
 * totals inside {@code /bootstrap}. Invalidating fewer would leave the user looking at a balance
 * that no longer matches the list right beside it (SD-01 step 73).
 */

function invalidateLedger(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: transactionKeys.all })
  void queryClient.invalidateQueries({ queryKey: walletKeys.all })
  void queryClient.invalidateQueries({ queryKey: budgetKeys.all })
  void queryClient.invalidateQueries({ queryKey: recurringKeys.all })
  void queryClient.invalidateQueries({ queryKey: bootstrapQueryKey })
}

/** Turns a backend error into the Vietnamese message the user should see (NFR-USE-03). */
function reportError(error: unknown, fallback: string) {
  toast.error(error instanceof ApiRequestError ? error.message : fallback)
}

/** Surfaces a budget threshold the write just crossed (FR-FIN-09, SD-01 step 76). */
function announceBudget(alert?: BudgetAlert) {
  if (!alert) {
    return
  }
  const percent = Math.round(alert.usage * 100)
  if (alert.level === 'EXCEEDED') {
    toast.error(
      `Vượt ngân sách "${alert.categoryName}": đã chi ${formatCurrency(alert.spentAmount)} / ${formatCurrency(alert.limitAmount)} (${percent}%).`,
    )
  } else {
    toast.error(
      `Sắp hết ngân sách "${alert.categoryName}": đã dùng ${percent}% của ${formatCurrency(alert.limitAmount)}.`,
    )
  }
}

/**
 * The transaction list, paged for infinite scroll (FR-FIN-12).
 *
 * <p>Paged rather than fetched whole: NFR-PERF-03 talks about 5.000 transactions, and rendering
 * all of them at once would be slow no matter how fast the query is.
 */
export function useTransactions(query: TransactionQuery) {
  return useInfiniteQuery({
    queryKey: transactionKeys.list(query),
    initialPageParam: 0,
    queryFn: ({ pageParam }) => api.fetchTransactions({ ...query, page: pageParam as number }),
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
  })
}

export function useSummary(params: SummaryParams, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.summary(params),
    queryFn: () => api.fetchSummary(params),
    enabled,
  })
}

export function useWallets() {
  return useQuery({ queryKey: walletKeys.all, queryFn: api.fetchWallets })
}

export function useCategories(type?: CategoryType) {
  return useQuery({
    queryKey: categoryKeys.byType(type),
    queryFn: () => api.fetchCategories(type),
  })
}

export function useBudgets() {
  return useQuery({ queryKey: budgetKeys.all, queryFn: api.fetchBudgets })
}

export function useRecurringRules() {
  return useQuery({ queryKey: recurringKeys.all, queryFn: api.fetchRecurringRules })
}

export function useCreateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: TransactionInput) => api.createTransaction(input),
    onSuccess: (result) => {
      invalidateLedger(queryClient)
      toast.success('Đã lưu giao dịch')
      announceBudget(result.budgetAlert)
    },
    onError: (error) => reportError(error, 'Không lưu được giao dịch'),
  })
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<TransactionInput> }) =>
      api.updateTransaction(id, input),
    onSuccess: (result) => {
      invalidateLedger(queryClient)
      toast.success('Đã lưu thay đổi')
      announceBudget(result.budgetAlert)
    },
    onError: (error) => reportError(error, 'Không lưu được thay đổi'),
  })
}

/**
 * Soft delete with a five second undo (NFR-USE-04).
 *
 * <p>The undo is genuine: the backend keeps the row and the restore endpoint brings the same
 * transaction back, rather than writing a copy with a new id.
 */
export function useDeleteTransaction() {
  const queryClient = useQueryClient()
  const restore = useRestoreTransaction()

  return useMutation({
    mutationFn: (transaction: Transaction) =>
      api.deleteTransaction(transaction.id).then(() => transaction),
    onSuccess: (transaction) => {
      invalidateLedger(queryClient)
      toast.undoable(`Đã xóa giao dịch ${formatCurrency(transaction.amount)}.`, () =>
        restore.mutate(transaction.id),
      )
    },
    onError: (error) => reportError(error, 'Không xóa được giao dịch'),
  })
}

export function useRestoreTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.restoreTransaction(id),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã khôi phục giao dịch')
    },
    onError: (error) => reportError(error, 'Không khôi phục được giao dịch'),
  })
}

export function useCreateWallet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: WalletInput) => api.createWallet(input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã tạo ví')
    },
    onError: (error) => reportError(error, 'Không tạo được ví'),
  })
}

export function useUpdateWallet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<WalletInput> }) =>
      api.updateWallet(id, input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã lưu ví')
    },
    onError: (error) => reportError(error, 'Không lưu được ví'),
  })
}

export function useDeleteWallet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteWallet(id),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã xóa ví')
    },
    onError: (error) => reportError(error, 'Không xóa được ví'),
  })
}

export function useCreateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CategoryInput) => api.createCategory(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoryKeys.all })
      toast.success('Đã tạo danh mục')
    },
    onError: (error) => reportError(error, 'Không tạo được danh mục'),
  })
}

export function useUpdateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<CategoryInput> }) =>
      api.updateCategory(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoryKeys.all })
      void queryClient.invalidateQueries({ queryKey: transactionKeys.all })
      toast.success('Đã lưu danh mục')
    },
    onError: (error) => reportError(error, 'Không lưu được danh mục'),
  })
}

export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteCategory(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: categoryKeys.all })
      toast.success('Đã xóa danh mục')
    },
    onError: (error) => reportError(error, 'Không xóa được danh mục'),
  })
}

export function useCreateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: BudgetInput) => api.createBudget(input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã tạo ngân sách')
    },
    onError: (error) => reportError(error, 'Không tạo được ngân sách'),
  })
}

export function useUpdateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: Partial<BudgetInput> }) =>
      api.updateBudget(id, input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã lưu ngân sách')
    },
    onError: (error) => reportError(error, 'Không lưu được ngân sách'),
  })
}

export function useDeleteBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteBudget(id),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã xóa ngân sách')
    },
    onError: (error) => reportError(error, 'Không xóa được ngân sách'),
  })
}

export function useCreateRecurringRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { rrule: string; startDate?: string; template: TransactionInput }) =>
      api.createRecurringRule(input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã tạo giao dịch định kỳ')
    },
    onError: (error) => reportError(error, 'Không tạo được giao dịch định kỳ'),
  })
}

export function useUpdateRecurringRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: { rrule?: string; isActive?: boolean } }) =>
      api.updateRecurringRule(id, input),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã cập nhật quy luật')
    },
    onError: (error) => reportError(error, 'Không cập nhật được quy luật'),
  })
}

export function useDeleteRecurringRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteRecurringRule(id),
    onSuccess: () => {
      invalidateLedger(queryClient)
      toast.success('Đã xóa quy luật định kỳ')
    },
    onError: (error) => reportError(error, 'Không xóa được quy luật định kỳ'),
  })
}

export function useRunRecurringRules() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => api.runRecurringRules(),
    onSuccess: (created) => {
      invalidateLedger(queryClient)
      toast.success(
        created.length === 0
          ? 'Không có giao dịch định kỳ nào tới hạn'
          : `Đã sinh ${created.length} giao dịch định kỳ`,
      )
    },
    onError: (error) => reportError(error, 'Không chạy được giao dịch định kỳ'),
  })
}
