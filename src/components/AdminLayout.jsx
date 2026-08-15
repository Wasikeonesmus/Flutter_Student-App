import { Outlet, NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  Users,
  CreditCard,
  BarChart3,
  Settings,
  LogOut,
  Rocket,
  Building2,
  FileText,
  ClipboardList,
  Calendar,
  ScrollText,
  ListChecks,
  PenSquare,
} from 'lucide-react'
import '../styles/AdminLayout.css'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/instructors', label: 'Instructors', icon: Users },
  { path: '/exams', label: 'Exams', icon: FileText },
  { path: '/create-test', label: 'Create Test', icon: PenSquare },
  { path: '/attempts', label: 'Submissions', icon: ClipboardList },
  { path: '/institutes', label: 'Institutes', icon: Building2 },
  { path: '/payments', label: 'Payments', icon: CreditCard },
  { path: '/subscriptions', label: 'Subscriptions', icon: Calendar },
  { path: '/results-release', label: 'Release Results', icon: Rocket },
  { path: '/analytics', label: 'Analytics', icon: BarChart3 },
  { path: '/audit', label: 'Audit Log', icon: ScrollText },
  { path: '/test-results', label: 'Test Results', icon: ListChecks },
  { path: '/settings', label: 'Settings', icon: Settings },
]

export default function AdminLayout({ user, onLogout }) {
  const isInstructor = user?.role === 'instructor'

  const filteredNavItems = navItems.filter((item) => {
    if (isInstructor) {
      return ['/', '/exams', '/create-test', '/attempts', '/results-release', '/test-results'].includes(item.path)
    }
    return true
  })

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar glass-panel">
        <div className="sidebar-brand">
          <div className="brand-logo">E</div>
          <div className="brand-info">
            <h1 className="brand-name">ExamPro</h1>
            <p className="brand-role">{isInstructor ? 'Instructor' : 'Super Admin'}</p>
          </div>
        </div>

        <nav className="admin-nav">
          {filteredNavItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              <item.icon size={20} className="nav-icon" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <button className="logout-btn" onClick={onLogout}>
          <LogOut size={20} />
          <span>Logout</span>
        </button>
      </aside>

      <main className="admin-main">
        <Outlet />
      </main>
    </div>
  )
}
