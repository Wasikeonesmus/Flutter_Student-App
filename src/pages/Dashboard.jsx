import { useEffect, useState } from 'react'
import { collection, query, orderBy, limit, getDocs, where } from 'firebase/firestore'
import { Link } from 'react-router-dom'
import {
  Users,
  GraduationCap,
  FileText,
  DollarSign,
  CheckCircle,
  Clock,
  AlertTriangle,
} from 'lucide-react'
import { auth, db } from '../firebase'
import { signOut } from 'firebase/auth'
import { formatFirestoreDate } from '../utils/firestoreDate'
import { loadPlatformPricing, paymentAmountUsd, formatPlanDisplay } from '../utils/planPricing'
import '../styles/Dashboard.css'
import '../styles/AdminData.css'

export default function Dashboard({ user }) {
  const [stats, setStats] = useState({
    instructors: 0,
    uniqueStudents: 0,
    submissions: 0,
    exams: 0,
    activeExams: 0,
    revenue: 0,
    activeSubs: 0,
    pendingPayments: 0,
    cheatAlerts: 0,
    avgScore: 0,
  })
  const [activities, setActivities] = useState([])
  const [loading, setLoading] = useState(true)
  const isInstructor = user?.role === 'instructor'

  useEffect(() => {
    async function fetchData() {
      try {
        if (isInstructor) {
          // Query only instructor's own exams
          const testQuery = query(collection(db, 'tests'), where('instructorId', '==', user.uid))
          const testDocs = await getDocs(testQuery)
          const instructorTests = testDocs.docs.map(d => ({ docId: d.id, ...d.data() }))

          let instructorAttempts = []
          if (instructorTests.length > 0) {
            // Fetch attempts for each exam in parallel
            const attemptsPromises = instructorTests.map(t =>
              getDocs(query(collection(db, 'attempts'), where('testId', '==', t.testId)))
            )
            const attemptsSnaps = await Promise.all(attemptsPromises)
            instructorAttempts = attemptsSnaps.flatMap(snap => snap.docs.map(d => ({ id: d.id, ...d.data() })))
          }

          const uniqueNames = new Set(
            instructorAttempts.map((a) => `${(a.studentName || '').trim().toLowerCase()}|${a.testId || ''}`).filter((k) => k !== '|')
          )
          const cheatAlerts = instructorAttempts.filter((a) => (a.cheatAlerts || 0) > 0).length
          const avgScore = instructorAttempts.length > 0
            ? Math.round(instructorAttempts.reduce((sum, a) => sum + (a.totalScore ?? 0), 0) / instructorAttempts.length)
            : 0

          setStats({
            instructors: 0,
            uniqueStudents: uniqueNames.size,
            submissions: instructorAttempts.length,
            exams: instructorTests.length,
            activeExams: instructorTests.filter(t => t.isEnabled !== false).length,
            revenue: 0,
            activeSubs: 0,
            pendingPayments: 0,
            cheatAlerts,
            avgScore,
          })

          const recentAttempts = instructorAttempts
            .sort((a, b) => (b.submittedAt?.toMillis?.() || 0) - (a.submittedAt?.toMillis?.() || 0))
            .slice(0, 8)
            .map((a) => ({
              id: a.id,
              type: 'attempt',
              text: `${a.studentName || 'Student'} — Attempted exam ${a.testId || ''} (${a.totalScore ?? 0} pts)`,
              time: a.submittedAt,
              status: (a.cheatAlerts || 0) > 0 ? 'cheat' : 'success'
            }))
          setActivities(recentAttempts)
        } else {
          // Super Admin overview loading
          const pricing = await loadPlatformPricing(db)
          const [instSnap, attemptSnap, testSnap, paySnap, auditSnap] = await Promise.all([
            getDocs(collection(db, 'users')),
            getDocs(collection(db, 'attempts')),
            getDocs(collection(db, 'tests')),
            getDocs(collection(db, 'payments')),
            getDocs(query(collection(db, 'admin_audit'), orderBy('createdAt', 'desc'), limit(8))).catch(() => ({ docs: [] })),
          ])

          const payments = paySnap.docs.map((d) => ({ id: d.id, ...d.data() }))
          const totalRevenue = payments
            .filter((p) => p.status === 'approved')
            .reduce((sum, p) => sum + paymentAmountUsd(p.plan, pricing), 0)

          const attempts = attemptSnap.docs.map((d) => d.data())
          const uniqueNames = new Set(
            attempts.map((a) => `${(a.studentName || '').trim().toLowerCase()}|${a.testId || ''}`).filter((k) => k !== '|')
          )
          const cheatAlerts = attempts.filter((a) => (a.cheatAlerts || 0) > 0).length

          setStats({
            instructors: instSnap.docs.filter((d) => d.data().role === 'instructor').length,
            uniqueStudents: uniqueNames.size,
            submissions: attemptSnap.size,
            exams: testSnap.size,
            activeExams: testSnap.docs.filter(d => d.data().isEnabled !== false).length,
            revenue: totalRevenue,
            activeSubs: instSnap.docs.filter(
              (d) => d.data().role === 'instructor' && d.data().subscriptionStatus === 'active'
            ).length,
            pendingPayments: payments.filter((p) => p.status === 'pending').length,
            cheatAlerts,
            avgScore: 0,
          })

          const payActivity = payments
            .sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0))
            .slice(0, 5)
            .map((p) => ({
              id: p.id,
              type: 'payment',
              text: `${p.userEmail || p.studentName || 'User'} — ${formatPlanDisplay(p.plan)} ($${paymentAmountUsd(p.plan, pricing)})`,
              time: p.createdAt,
              status: p.status,
            }))

          const auditActivity = (auditSnap.docs || []).map((d) => {
            const data = d.data()
            return {
              id: d.id,
              type: 'audit',
              text: `${data.action} by ${data.adminEmail || 'admin'}`,
              time: data.createdAt,
              status: 'audit',
            }
          })

          const merged = [...payActivity, ...auditActivity]
            .sort((a, b) => (b.time?.toMillis?.() || 0) - (a.time?.toMillis?.() || 0))
            .slice(0, 8)
          setActivities(merged)
        }
      } catch (err) {
        console.error('Dashboard Load Error:', err)
        if (err.code === 'permission-denied' || err.message?.includes('permission')) {
          try {
            await signOut(auth)
          } catch (signoutErr) {
            console.error('Failed to sign out on permission error:', signoutErr)
          }
        }
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [isInstructor, user])

  if (loading) return <div className="loading-screen">Loading dashboard overview...</div>

  const statCards = isInstructor
    ? [
        { label: 'My Exams', value: stats.exams, icon: FileText, color: '#6366f1' },
        { label: 'Active Exams', value: stats.activeExams, icon: CheckCircle, color: '#880E4F' },
        { label: 'Submissions', value: stats.submissions, icon: FileText, color: '#f59e0b' },
        { label: 'Unique examinees', value: stats.uniqueStudents, icon: GraduationCap, color: '#E91E63' },
        { label: 'Avg. Student Score', value: stats.avgScore, icon: DollarSign, color: '#ef4444' },
        { label: 'Cheat-flagged attempts', value: stats.cheatAlerts, icon: AlertTriangle, color: '#f97316' },
      ]
    : [
        { label: 'Instructors', value: stats.instructors, icon: Users, color: '#B71C1C' },
        { label: 'Unique examinees', value: stats.uniqueStudents, icon: GraduationCap, color: '#E91E63' },
        { label: 'Submissions', value: stats.submissions, icon: FileText, color: '#f59e0b' },
        { label: 'Revenue (approved)', value: `$${stats.revenue}`, icon: DollarSign, color: '#ef4444' },
        { label: 'Active subs', value: stats.activeSubs, icon: CheckCircle, color: '#880E4F' },
        { label: 'Pending payments', value: stats.pendingPayments, icon: Clock, color: '#ec4899' },
        { label: 'Exams', value: stats.exams, icon: FileText, color: '#6366f1' },
        { label: 'Cheat-flagged attempts', value: stats.cheatAlerts, icon: AlertTriangle, color: '#f97316' },
      ]

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <h2 className="page-heading gradient-text">{isInstructor ? 'Instructor Dashboard' : 'Platform Overview'}</h2>
        <p className="page-subtitle">Welcome back, {isInstructor ? (user.name || 'Instructor') : 'Super Admin'}</p>
      </header>

      <div className="stats-grid">
        {statCards.map((card, i) => (
          <div key={i} className="stat-card glass-panel">
            <div className="stat-icon-wrapper" style={{ backgroundColor: `${card.color}20`, color: card.color }}>
              <card.icon size={24} />
            </div>
            <div className="stat-content">
              <h3 className="stat-value">{card.value}</h3>
              <p className="stat-label">{card.label}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="dashboard-panels">
        <section className="glass-panel recent-activity">
          <div className="panel-header">
            <h3 className="panel-title">{isInstructor ? 'Recent Submissions' : 'Recent Activity'}</h3>
            {!isInstructor && (
              <Link to="/audit" className="muted" style={{ fontSize: '0.85rem' }}>
                Full audit log →
              </Link>
            )}
          </div>
          <div className="activity-list">
            {activities.length > 0 ? (
              activities.map((act) => (
                <div key={act.id} className="activity-item">
                  <div className="activity-icon-bg">
                    {act.type === 'audit' ? <CheckCircle size={16} /> : act.type === 'attempt' ? <GraduationCap size={16} /> : <DollarSign size={16} />}
                  </div>
                  <div className="activity-info">
                    <p className="activity-text">{act.text}</p>
                    <span className="activity-time">{formatFirestoreDate(act.time)}</span>
                  </div>
                  {act.type === 'payment' && (
                    <span
                      className={`badge badge-${act.status === 'approved' ? 'success' : act.status === 'pending' ? 'warning' : 'error'}`}
                    >
                      {act.status}
                    </span>
                  )}
                  {act.type === 'attempt' && (
                    <span
                      className={`badge badge-${act.status === 'success' ? 'success' : 'error'}`}
                    >
                      {act.status === 'success' ? 'normal' : 'flagged'}
                    </span>
                  )}
                </div>
              ))
            ) : (
              <p className="empty-state">No recent activity found.</p>
            )}
          </div>
        </section>

        <section className="glass-panel sub-breakdown">
          <div className="panel-header">
            <h3 className="panel-title">Quick links</h3>
          </div>
          <div className="breakdown-content" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Link to="/exams" className="btn-sm" style={{ textDecoration: 'none', textAlign: 'center' }}>
              Manage exams
            </Link>
            {isInstructor ? (
              <>
                <Link to="/create-test" className="btn-sm primary" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  Create New Test
                </Link>
                <Link to="/attempts" className="btn-sm" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  View submissions
                </Link>
                <Link to="/test-results" className="btn-sm" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  View test results
                </Link>
              </>
            ) : (
              <>
                <Link to="/attempts" className="btn-sm" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  View submissions
                </Link>
                <Link to="/subscriptions" className="btn-sm" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  Subscriptions
                </Link>
                <Link to="/payments" className="btn-sm primary" style={{ textDecoration: 'none', textAlign: 'center' }}>
                  Pending payments ({stats.pendingPayments})
                </Link>
              </>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
