import { useEffect, useState } from 'react'
import { collection, getDocs } from 'firebase/firestore'
import { TrendingUp, Award, AlertTriangle, PieChart as PieIcon } from 'lucide-react'
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts'
import { db } from '../firebase'
import {
  loadPlatformPricing,
  paymentAmountUsd,
  formatPlanDisplay,
} from '../utils/planPricing'
import { normalizeInstructorTier, INSTRUCTOR_TIER_LABELS } from '../utils/normalizeInstructorTier'
import '../styles/Dashboard.css'
import '../styles/Analytics.css'

const COLORS = ['#B71C1C', '#E91E63', '#880E4F', '#ef4444', '#6366f1', '#22c55e']

export default function Analytics() {
  const [data, setData] = useState({
    avgRevenue: 0,
    planCounts: [],
    tierCounts: [],
    testDensity: [],
    totalTests: 0,
    cheatAttempts: 0,
    totalRevenue: 0,
  })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function fetchMetrics() {
      try {
        const pricing = await loadPlatformPricing(db)
        const [instSnap, paySnap, testSnap, attemptSnap] = await Promise.all([
          getDocs(collection(db, 'users')),
          getDocs(collection(db, 'payments')),
          getDocs(collection(db, 'tests')),
          getDocs(collection(db, 'attempts')),
        ])

        const payments = paySnap.docs.map((d) => d.data()).filter((p) => p.status === 'approved')
        const instructors = instSnap.docs.filter((d) => d.data().role === 'instructor')

        const planMap = {}
        payments.forEach((p) => {
          const label = formatPlanDisplay(p.plan)
          planMap[label] = (planMap[label] || 0) + 1
        })

        const tierMap = { basic: 0, pro: 0, institute: 0, other: 0 }
        instructors.forEach((d) => {
          const t = normalizeInstructorTier(d.data().subscriptionTier)
          if (tierMap[t] != null) tierMap[t]++
          else tierMap.other++
        })

        const testData = testSnap.docs.map((d) => d.data())
        const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
        const densityMap = testData.reduce((acc, t) => {
          if (t.createdAt) {
            const day = days[t.createdAt.toDate().getDay()]
            acc[day] = (acc[day] || 0) + 1
          }
          return acc
        }, {})

        const totalRevenue = payments.reduce((s, p) => s + paymentAmountUsd(p.plan, pricing), 0)
        const attempts = attemptSnap.docs.map((d) => d.data())
        const cheatAttempts = attempts.filter((a) => (a.cheatAlerts || 0) > 0).length

        setData({
          totalRevenue,
          avgRevenue: instructors.length ? (totalRevenue / instructors.length).toFixed(2) : 0,
          planCounts: Object.entries(planMap).map(([name, value]) => ({ name, value })),
          tierCounts: Object.entries(tierMap)
            .filter(([, v]) => v > 0)
            .map(([key, value]) => ({
              name: INSTRUCTOR_TIER_LABELS[key] || key,
              value,
            })),
          totalTests: testSnap.size,
          testDensity: days.map((name) => ({ name, exams: densityMap[name] || 0 })),
          cheatAttempts,
        })
      } catch (err) {
        console.error('Analytics Error:', err)
      } finally {
        setLoading(false)
      }
    }
    fetchMetrics()
  }, [])

  if (loading) return <div className="loading-screen">Synthesizing platform data...</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <h2 className="page-heading gradient-text">Advanced Analytics</h2>
        <p className="page-subtitle">Revenue, plans, exams, and anti-cheat overview</p>
      </header>

      <div className="stats-grid">
        <div className="stat-card glass-panel">
          <div className="stat-icon-wrapper" style={{ backgroundColor: '#6366f120', color: '#6366f1' }}>
            <TrendingUp size={24} />
          </div>
          <div className="stat-content">
            <h3 className="stat-value">${data.avgRevenue}</h3>
            <p className="stat-label">Avg. revenue / instructor</p>
          </div>
        </div>
        <div className="stat-card glass-panel">
          <div className="stat-icon-wrapper" style={{ backgroundColor: '#10b98120', color: '#10b981' }}>
            <Award size={24} />
          </div>
          <div className="stat-content">
            <h3 className="stat-value">{data.totalTests}</h3>
            <p className="stat-label">Total exams</p>
          </div>
        </div>
        <div className="stat-card glass-panel">
          <div className="stat-icon-wrapper" style={{ backgroundColor: '#ef444420', color: '#ef4444' }}>
            <PieIcon size={24} />
          </div>
          <div className="stat-content">
            <h3 className="stat-value">${data.totalRevenue}</h3>
            <p className="stat-label">Total approved revenue</p>
          </div>
        </div>
        <div className="stat-card glass-panel">
          <div className="stat-icon-wrapper" style={{ backgroundColor: '#f9731620', color: '#f97316' }}>
            <AlertTriangle size={24} />
          </div>
          <div className="stat-content">
            <h3 className="stat-value">{data.cheatAttempts}</h3>
            <p className="stat-label">Attempts with cheat alerts</p>
          </div>
        </div>
      </div>

      <div className="dashboard-panels">
        <section className="glass-panel chart-panel">
          <div className="panel-header">
            <h3 className="panel-title">Weekly exam creation</h3>
          </div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={data.testDensity}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                <XAxis dataKey="name" stroke="#94a3b8" />
                <YAxis stroke="#94a3b8" />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1e293b',
                    border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: '8px',
                  }}
                  itemStyle={{ color: '#fff' }}
                />
                <Bar dataKey="exams" fill="#B71C1C" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="glass-panel chart-panel">
          <div className="panel-header">
            <h3 className="panel-title">Instructor tiers (active accounts)</h3>
          </div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={data.tierCounts}
                  innerRadius={60}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                  nameKey="name"
                >
                  {data.tierCounts.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1e293b',
                    border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: '8px',
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
            <div className="chart-legend">
              {data.tierCounts.map((p, i) => (
                <div key={i} className="legend-item">
                  <span className="legend-dot" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                  <span className="legend-label">
                    {p.name}: {p.value}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="glass-panel chart-panel full-width-chart">
          <div className="panel-header">
            <h3 className="panel-title">Approved payments by plan</h3>
          </div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart data={data.planCounts} layout="vertical" margin={{ left: 80 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                <XAxis type="number" stroke="#94a3b8" />
                <YAxis dataKey="name" type="category" stroke="#94a3b8" width={70} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#1e293b',
                    border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: '8px',
                  }}
                />
                <Bar dataKey="value" fill="#E91E63" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
    </div>
  )
}
