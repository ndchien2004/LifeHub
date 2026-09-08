import { useEffect, useState } from 'react'
import { BellOff } from 'lucide-react'
import { AppLayout } from '@/shared/components/AppLayout'
import { Toaster } from '@/shared/components/ui/toast'
import { CalendarPage } from '@/features/calendar/CalendarPage'
import { MissedRemindersModal } from '@/features/calendar/components/MissedRemindersModal'
import { useReminders } from '@/features/calendar/useReminders'
import { DashboardPage } from '@/features/dashboard/DashboardPage'
import { useBootstrap } from '@/features/dashboard/useBootstrap'
import { ProjectsPage } from '@/features/tasks/ProjectsPage'
import { TasksPage } from '@/features/tasks/TasksPage'
import { useTheme } from '@/shared/hooks/useTheme'
import { useNavStore } from '@/shared/stores/navStore'

export default function App() {
  // Mounted once at the root so the html class tracks the stored preference app-wide.
  useTheme()
  const route = useNavStore((state) => state.route)
  const navigate = useNavStore((state) => state.navigate)
  const { notificationsBlocked } = useReminders()

  const { data: bootstrap } = useBootstrap()
  const [missedDismissed, setMissedDismissed] = useState(false)

  // The missed list comes from /bootstrap rather than a poll: it is a startup question (UC-05),
  // and asking again later would re-open a modal the user has already dealt with.
  const missed = bootstrap?.missedReminders ?? []

  useEffect(() => {
    if (missed.length > 0) {
      setMissedDismissed(false)
    }
  }, [missed.length])

  return (
    <>
      <AppLayout>
        {notificationsBlocked && (
          <div
            role="status"
            className="flex items-center gap-2 border-b border-border bg-muted/60 px-4 py-2 text-xs text-muted-foreground"
          >
            <BellOff className="h-3.5 w-3.5 shrink-0" aria-hidden />
            Hệ điều hành đang chặn thông báo của LifeHub. Nhắc hẹn sẽ hiện trong ứng dụng thay vì
            hiện ra ngoài — bật lại trong cài đặt thông báo của hệ điều hành để nhận đầy đủ.
          </div>
        )}

        {route === 'dashboard' && <DashboardPage />}
        {route === 'tasks' && <TasksPage />}
        {route === 'projects' && <ProjectsPage />}
        {route === 'calendar' && <CalendarPage />}
      </AppLayout>

      <MissedRemindersModal
        reminders={missed}
        open={!missedDismissed}
        onClose={() => setMissedDismissed(true)}
        onOpenEvent={() => {
          setMissedDismissed(true)
          navigate('calendar')
        }}
      />

      <Toaster />
    </>
  )
}
