import { create } from 'zustand'

/**
 * Which screen is showing.
 *
 * <p>Deliberately not a router. The locked frontend stack (04-ARCHITECTURE.md section 2) lists no
 * routing library, and a packaged Electron app loads the bundle over {@code file://}, where URL
 * based routing needs hash mode and extra care for no benefit here - there are six screens and no
 * deep links.
 */
export type Route = 'dashboard' | 'tasks' | 'projects' | 'calendar' | 'finance' | 'settings'

interface NavState {
  route: Route
  navigate: (route: Route) => void
}

export const useNavStore = create<NavState>((set) => ({
  route: 'tasks',
  navigate: (route) => set({ route }),
}))
