import { Fragment, useEffect, useState, useMemo } from 'react'
import { collection, getDocs, query, where } from 'firebase/firestore'
import { Link } from 'react-router-dom'
import { Search, AlertTriangle, ExternalLink } from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import '../styles/AdminData.css'

export default function AttemptsManagement({ user }) {
  const [attempts, setAttempts] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [testFilter, setTestFilter] = useState('all')
  const [expanded, setExpanded] = useState(null)
  const isInstructor = user?.role === 'instructor'

  useEffect(() => {
    async function load() {
      try {
        let rows = []
        if (isInstructor) {
          const testSnap = await getDocs(query(collection(db, 'tests'), where('instructorId', '==', user.uid)))
          const testIdsList = testSnap.docs.map((d) => d.data().testId).filter(Boolean)

          if (testIdsList.length > 0) {
            const attemptsPromises = testIdsList.map(testId =>
              getDocs(query(collection(db, 'attempts'), where('testId', '==', testId)))
            )
            const attemptsSnaps = await Promise.all(attemptsPromises)
            rows = attemptsSnaps.flatMap(snap => snap.docs.map(d => ({ id: d.id, ...d.data() })))
          }
        } else {
          const snap = await getDocs(collection(db, 'attempts'))
          rows = snap.docs.map((d) => ({ id: d.id, ...d.data() }))
        }
        
        rows.sort((a, b) => {
          const ta = a.submittedAt?.toMillis?.() || 0
          const tb = b.submittedAt?.toMillis?.() || 0
          return tb - ta
        })
        setAttempts(rows)
      } catch (err) {
        console.error(err)
        alert('Failed to load attempts: ' + err.message)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [isInstructor, user])

  const testIds = useMemo(() => {
    const set = new Set(attempts.map((a) => a.testId).filter(Boolean))
    return ['all', ...Array.from(set).sort()]
  }, [attempts])

  const filtered = useMemo(() => {
    return attempts.filter((a) => {
      if (testFilter !== 'all' && a.testId !== testFilter) return false
      if (filter === 'cheat' && !(a.cheatAlerts > 0)) return false
      if (filter === 'paid' && !a.hasPaidForDetails) return false
      const q = search.trim().toLowerCase()
      if (!q) return true
      return (
        (a.studentName || '').toLowerCase().includes(q) ||
        (a.testId || '').toLowerCase().includes(q) ||
        (a.district || '').toLowerCase().includes(q) ||
        a.id.toLowerCase().includes(q)
      )
    })
  }, [attempts, search, filter, testFilter])

  const cheatTotal = attempts.filter((a) => a.cheatAlerts > 0).length

  if (loading) return <div className="loading-screen">Loading submissions…</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <h2 className="page-heading gradient-text">Submissions</h2>
        <p className="page-subtitle">
          {attempts.length} attempts · {cheatTotal} with anti-cheat alerts
        </p>
      </header>

      <div className="toolbar-row" style={{ marginBottom: 20 }}>
        <div className="search-container glass-panel">
          <Search size={18} style={{ color: '#737373' }} />
          <input
            placeholder="Student, test ID, attempt ID…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="filter-group glass-panel">
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">All</option>
            <option value="cheat">Cheat alerts only</option>
            <option value="paid">Paid for details</option>
          </select>
        </div>
        <div className="filter-group glass-panel">
          <select value={testFilter} onChange={(e) => setTestFilter(e.target.value)}>
            {testIds.map((id) => (
              <option key={id} value={id}>
                {id === 'all' ? 'All tests' : id}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="glass-panel table-card">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Student</th>
              <th>Test</th>
              <th>Score</th>
              <th>Cheat</th>
              <th>Submitted</th>
              <th>Links</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length ? (
              filtered.map((a) => (
                <Fragment key={a.id}>
                  <tr>
                    <td>
                      <strong>{a.studentName || '—'}</strong>
                      <div className="muted">{a.district || ''} {a.gender ? `· ${a.gender}` : ''}</div>
                    </td>
                    <td className="mono">{a.testId || '—'}</td>
                    <td>
                      <strong>{a.totalScore ?? 0}</strong>
                      {a.rank ? <span className="muted"> · rank #{a.rank}</span> : null}
                    </td>
                    <td>
                      {a.cheatAlerts > 0 ? (
                        <span className="cheat-pill">
                          <AlertTriangle size={12} /> {a.cheatAlerts}
                        </span>
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td className="muted">{formatFirestoreDate(a.submittedAt)}</td>
                    <td>
                      <div className="btn-row">
                        <Link to={`/student/${a.id}`} className="btn-sm" target="_blank" rel="noreferrer">
                          <ExternalLink size={12} /> Result
                        </Link>
                        {(a.cheatEvents?.length > 0 || a.cheatAlerts > 0) && (
                          <button type="button" className="btn-sm" onClick={() => setExpanded(expanded === a.id ? null : a.id)}>
                            Events
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {expanded === a.id && (
                    <tr>
                      <td colSpan={6} style={{ background: 'rgba(239,68,68,0.05)' }}>
                        <p className="muted" style={{ marginBottom: 6 }}>Cheat events:</p>
                        <code style={{ fontSize: '0.8rem', color: '#fca5a5' }}>
                          {(a.cheatEvents || []).join(', ') || 'No event list stored'}
                        </code>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="empty-state">No submissions match filters.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
