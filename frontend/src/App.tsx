import { AppLayout } from '@/shared/components/AppLayout'
import { Toaster } from '@/shared/components/ui/toast'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { ProjectsPage } from '@/features/tasks/ProjectsPage'
import { TasksPage } from '@/features/tasks/TasksPage'
import { useTheme } from '@/shared/hooks/useTheme'
import { useNavStore } from '@/shared/stores/navStore'

export default function App() {
  // Mounted once at the root so the html class tracks the stored preference app-wide.
  useTheme()
  const route = useNavStore((state) => state.route)

  return (
    <>
      <AppLayout>
        {route === 'dashboard' && <DashboardPage />}
        {route === 'tasks' && <TasksPage />}
        {route === 'projects' && <ProjectsPage />}
      </AppLayout>
      <Toaster />
    </>
  )
}
