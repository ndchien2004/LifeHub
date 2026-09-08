import { AppLayout } from '@/shared/components/AppLayout'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { useTheme } from '@/shared/hooks/useTheme'

export default function App() {
  // Mounted once at the root so the html class tracks the stored preference app-wide.
  useTheme()

  return (
    <AppLayout>
      <DashboardPage />
    </AppLayout>
  )
}
