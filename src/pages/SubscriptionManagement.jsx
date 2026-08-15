import { useEffect, useState, useMemo } from 'react'
import {
  collection,
  doc,
  getDocs,
  setDoc,
  updateDoc,
  Timestamp,
} from 'firebase/firestore'
import { Calendar, RefreshCw, Search } from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import { normalizeInstructorTier, INSTRUCTOR_TIER_LABELS } from '../utils/normalizeInstructorTier'
import { subscriptionDaysForPlan } from '../utils/planPricing'
import { logAdminAction } from '../utils/auditLog'
import '../styles/AdminData.css'

function toDate(value) {
  if (!value) return null
  if (typeof value.toDate === 'function') return value.toDate()
  if (typeof value.seconds === 'number') return new Date(value.seconds * 1000)
  return null
}

export default function SubscriptionManagement() {
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [busy, setBusy] = useState(null)

  useEffect(() => {
    load()
  }, [])

  async function load() {
    setLoading(true)
    try {
      const [subSnap, userSnap] = await Promise.all([
        getDocs(collection(db, 'subscriptions')),
        getDocs(collection(db, 'users')),
      ])
      const users = {}
      userSnap.docs.forEach((d) => {
        if (d.data().role === 'instructor') users[d.id] = { id: d.id, ...d.data() }
      })

      const subByUser = {}
      subSnap.docs.forEach((d) => {
        subByUser[d.id] = { id: d.id, ...d.data() }
      })

      const merged = Object.values(users).map((u) => {
        const sub = subByUser[u.id]
        const end = toDate(sub?.endDate)
        const now = new Date()
        let status = u.subscriptionStatus || 'inactive'
        if (sub?.isActive && end && end < now) status = 'expired'
        if (sub?.isActive && end && end >= now) status = 'active'
        return {
          userId: u.id,
          email: u.email,
          name: u.name,
          tier: normalizeInstructorTier(u.subscriptionTier),
          userStatus: u.subscriptionStatus,
          sub,
          endDate: end,
          status,
        }
      })
      merged.sort((a, b) => (a.endDate?.getTime() || 0) - (b.endDate?.getTime() || 0))
      setRows(merged)
    } catch (err) {
      alert('Load failed: ' + err.message)
    } finally {
      setLoading(false)
    }
  }

  const filtered = useMemo(() => {
    return rows.filter((r) => {
      if (filter === 'active' && r.status !== 'active') return false
      if (filter === 'expired' && r.status !== 'expired' && r.userStatus !== 'expired') return false
      if (filter === 'expiring') {
        if (!r.endDate) return false
        const days = (r.endDate - new Date()) / (86400000)
        if (days < 0 || days > 14) return false
      }
      const q = search.trim().toLowerCase()
      if (!q) return true
      return (r.email || '').toLowerCase().includes(q) || (r.name || '').toLowerCase().includes(q)
    })
  }, [rows, search, filter])

  async function extendSubscription(row, extraDays) {
    setBusy(row.userId)
    try {
      const now = new Date()
      const base = row.endDate && row.endDate > now ? row.endDate : now
      const end = new Date(base)
      end.setDate(end.getDate() + extraDays)

      await setDoc(
        doc(db, 'subscriptions', row.userId),
        {
          instructorId: row.userId,
          plan: row.tier,
          startDate: row.sub?.startDate || Timestamp.fromDate(now),
          endDate: Timestamp.fromDate(end),
          isActive: true,
        },
        { merge: true }
      )
      await updateDoc(doc(db, 'users', row.userId), {
        subscriptionStatus: 'active',
        subscriptionTier: row.tier,
        approvalStatus: 'approved',
      })
      await logAdminAction('subscription_extend', {
        userId: row.userId,
        email: row.email,
        days: extraDays,
        endDate: end.toISOString(),
      })
      await load()
    } catch (err) {
      alert(err.message)
    } finally {
      setBusy(null)
    }
  }

  async function syncFromTier(row) {
    const days = subscriptionDaysForPlan(row.tier)
    await extendSubscription(row, days)
  }

  if (loading) return <div className="loading-screen">Loading subscriptions…</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <div className="header-flex">
          <div>
            <h2 className="page-heading gradient-text">Subscriptions</h2>
            <p className="page-subtitle">Expiry dates, renewals, and instructor access</p>
          </div>
          <button type="button" className="btn-sm primary" onClick={load}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </header>

      <div className="toolbar-row" style={{ marginBottom: 20 }}>
        <div className="search-container glass-panel">
          <Search size={18} style={{ color: '#737373' }} />
          <input placeholder="Search instructor…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="filter-group glass-panel">
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">All</option>
            <option value="active">Active</option>
            <option value="expiring">Expiring in 14 days</option>
            <option value="expired">Expired / inactive</option>
          </select>
        </div>
      </div>

      <div className="glass-panel table-card">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Instructor</th>
              <th>Plan</th>
              <th>Status</th>
              <th>Ends</th>
              <th>Renew</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length ? (
              filtered.map((row) => (
                <tr key={row.userId}>
                  <td>
                    <div>{row.name || '—'}</div>
                    <div className="muted">{row.email}</div>
                  </td>
                  <td>{INSTRUCTOR_TIER_LABELS[row.tier] || row.tier}</td>
                  <td>
                    <span
                      className={`badge ${
                        row.status === 'active' ? 'badge-success' : row.status === 'expired' ? 'badge-error' : 'badge-warning'
                      }`}
                    >
                      {row.status}
                    </span>
                  </td>
                  <td className="muted">
                    <Calendar size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} />
                    {row.endDate ? formatFirestoreDate(row.sub?.endDate) : 'No end date'}
                  </td>
                  <td>
                    <div className="btn-row">
                      <button
                        type="button"
                        className="btn-sm"
                        disabled={busy === row.userId}
                        onClick={() => extendSubscription(row, 30)}
                      >
                        +30d
                      </button>
                      <button
                        type="button"
                        className="btn-sm"
                        disabled={busy === row.userId}
                        onClick={() => extendSubscription(row, 90)}
                      >
                        +90d
                      </button>
                      <button
                        type="button"
                        className="btn-sm primary"
                        disabled={busy === row.userId}
                        onClick={() => syncFromTier(row)}
                      >
                        Sync plan
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={5} className="empty-state">No instructors match filters.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
