import { Fragment, useEffect, useState, useMemo } from 'react'
import { collection, getDocs, query, where } from 'firebase/firestore'
import { Link } from 'react-router-dom'
import {
  Search,
  AlertTriangle,
  ExternalLink,
  Trophy,
  Users,
  ClipboardCheck,
  TrendingUp,
  ChevronDown,
  ChevronRight,
  Filter,
} from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import '../styles/TestResults.css'
import '../styles/AdminData.css'

function StatCard({ icon: Icon, label, value, color }) {
  return (
    <div className="tr-stat-card glass-panel">
      <div className="tr-stat-icon" style={{ background: color }}>
        <Icon size={20} />
      </div>
      <div>
        <p className="tr-stat-value">{value}</p>
        <p className="tr-stat-label">{label}</p>
      </div>
    </div>
  )
}

export default function TestResults({ user }) {
  const [attempts, setAttempts] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [testFilter, setTestFilter] = useState('all')
  const [sortBy, setSortBy] = useState('date') // 'date' | 'score'
  const [expandedGroups, setExpandedGroups] = useState({})
  const [sectionNames, setSectionNames] = useState({})
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

        // Only show completed / submitted attempts (have a totalScore)
        const completed = rows.filter(
          (a) => a.totalScore !== undefined && a.totalScore !== null
        )
        completed.sort((a, b) => {
          const ta = a.submittedAt?.toMillis?.() || 0
          const tb = b.submittedAt?.toMillis?.() || 0
          return tb - ta
        })
        setAttempts(completed)
        // expand all groups by default
        const groups = {}
        completed.forEach((a) => {
          const key = a.testId || 'unknown'
          groups[key] = true
        })
        setExpandedGroups(groups)

        // Fetch section titles
        const uniqueTestIds = Array.from(new Set(completed.map((a) => a.testId).filter(Boolean)))
        const nameMap = {}
        await Promise.all(
          uniqueTestIds.map(async (testId) => {
            try {
              const tid = testId.trim().toUpperCase()
              const secSnap = await getDocs(collection(db, 'tests', tid, 'sections'))
              secSnap.forEach((doc) => {
                const data = doc.data()
                nameMap[doc.id] = data.title || data.name || doc.id
              })
            } catch (err) {
              console.warn(`Failed to load sections for test ${testId}:`, err)
            }
          })
        )
        setSectionNames(nameMap)
      } catch (err) {
        console.error(err)
        alert('Failed to load results: ' + err.message)
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
    let list = attempts
    if (testFilter !== 'all') list = list.filter((a) => a.testId === testFilter)
    const q = search.trim().toLowerCase()
    if (q) {
      list = list.filter(
        (a) =>
          (a.studentName || '').toLowerCase().includes(q) ||
          (a.testId || '').toLowerCase().includes(q) ||
          (a.district || '').toLowerCase().includes(q) ||
          a.id.toLowerCase().includes(q)
      )
    }
    if (sortBy === 'score') {
      list = [...list].sort((a, b) => (b.totalScore ?? 0) - (a.totalScore ?? 0))
    }
    return list
  }, [attempts, search, testFilter, sortBy])

  // Group filtered by testId
  const grouped = useMemo(() => {
    const map = {}
    filtered.forEach((a) => {
      const key = a.testId || 'Unknown Test'
      if (!map[key]) map[key] = []
      map[key].push(a)
    })
    return map
  }, [filtered])

  // Stats
  const totalAttempts = attempts.length
  const uniqueStudents = new Set(attempts.map((a) => a.studentName).filter(Boolean)).size
  const avgScore =
    attempts.length > 0
      ? Math.round(
          attempts.reduce((sum, a) => sum + (a.totalScore ?? 0), 0) / attempts.length
        )
      : 0
  const cheatCount = attempts.filter((a) => a.cheatAlerts > 0).length

  const toggleGroup = (key) =>
    setExpandedGroups((prev) => ({ ...prev, [key]: !prev[key] }))

  if (loading) return <div className="loading-screen">Loading test results…</div>

  return (
    <div className="page-wrapper">
      {/* Header */}
      <header className="page-header">
        <h2 className="page-heading gradient-text">Test Results</h2>
        <p className="page-subtitle">
          All completed exam submissions — grouped by test, with scores and student details.
        </p>
      </header>

      {/* Stats row */}
      <div className="tr-stats-row">
        <StatCard
          icon={ClipboardCheck}
          label="Total Results"
          value={totalAttempts}
          color="rgba(99,102,241,0.18)"
        />
        <StatCard
          icon={Users}
          label="Unique Students"
          value={uniqueStudents}
          color="rgba(34,197,94,0.15)"
        />
        <StatCard
          icon={TrendingUp}
          label="Avg. Score"
          value={avgScore}
          color="rgba(245,158,11,0.15)"
        />
        <StatCard
          icon={AlertTriangle}
          label="Cheat Alerts"
          value={cheatCount}
          color="rgba(239,68,68,0.15)"
        />
      </div>

      {/* Toolbar */}
      <div className="toolbar-row" style={{ marginBottom: 20 }}>
        <div className="search-container glass-panel">
          <Search size={18} style={{ color: '#737373' }} />
          <input
            placeholder="Search student, test ID, district…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="filter-group glass-panel">
          <Filter size={16} style={{ color: '#737373' }} />
          <select value={testFilter} onChange={(e) => setTestFilter(e.target.value)}>
            {testIds.map((id) => (
              <option key={id} value={id}>
                {id === 'all' ? 'All tests' : id}
              </option>
            ))}
          </select>
        </div>
        <div className="filter-group glass-panel">
          <Trophy size={16} style={{ color: '#737373' }} />
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            <option value="date">Sort: Latest first</option>
            <option value="score">Sort: Highest score</option>
          </select>
        </div>
      </div>

      {/* Grouped results */}
      {filtered.length === 0 ? (
        <div className="glass-panel" style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
          No results match your filters.
        </div>
      ) : (
        Object.entries(grouped).map(([testId, rows]) => {
          const isOpen = expandedGroups[testId] !== false
          const groupAvg = Math.round(
            rows.reduce((s, r) => s + (r.totalScore ?? 0), 0) / rows.length
          )
          const topScore = Math.max(...rows.map((r) => r.totalScore ?? 0))

          return (
            <section key={testId} className="tr-group glass-panel">
              {/* Group header */}
              <button
                className="tr-group-header"
                onClick={() => toggleGroup(testId)}
                type="button"
              >
                <div className="tr-group-left">
                  {isOpen ? <ChevronDown size={18} /> : <ChevronRight size={18} />}
                  <span className="tr-group-name">{testId}</span>
                  <span className="badge badge-muted">{rows.length} result{rows.length !== 1 ? 's' : ''}</span>
                </div>
                <div className="tr-group-meta">
                  <span className="tr-meta-chip">Avg&nbsp;<strong>{groupAvg}</strong></span>
                  <span className="tr-meta-chip tr-meta-top">Top&nbsp;<strong>{topScore}</strong></span>
                </div>
              </button>

              {/* Table */}
              {isOpen && (
                <div className="table-card" style={{ padding: 0 }}>
                  <table className="admin-table">
                    <thead>
                      <tr>
                        <th>#</th>
                        <th>Student</th>
                        <th>Score</th>
                        <th>Rank</th>
                        <th>Cheat</th>
                        <th>Submitted</th>
                        <th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((a, idx) => (
                        <Fragment key={a.id}>
                          <tr className={idx === 0 && sortBy === 'score' ? 'tr-top-row' : ''}>
                            <td className="muted" style={{ width: 40 }}>{idx + 1}</td>
                            <td>
                              <strong>{a.studentName || '—'}</strong>
                              {(a.district || a.gender) && (
                                <div className="muted">
                                  {[a.district, a.gender].filter(Boolean).join(' · ')}
                                </div>
                              )}
                            </td>
                            <td>
                              <span className="tr-score-pill">
                                {a.totalScore ?? '—'}
                              </span>
                              {a.totalMarks && (
                                <span className="muted" style={{ marginLeft: 6, fontSize: '0.8rem' }}>
                                  / {a.totalMarks}
                                </span>
                              )}
                            </td>
                            <td>
                              {a.rank ? (
                                <span className="tr-rank-pill">
                                  <Trophy size={11} />
                                  #{a.rank}
                                </span>
                              ) : (
                                <span className="muted">—</span>
                              )}
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
                                <Link
                                  to={`/student/${a.id}`}
                                  className="btn-sm"
                                  target="_blank"
                                  rel="noreferrer"
                                >
                                  <ExternalLink size={12} /> View
                                </Link>
                              </div>
                            </td>
                          </tr>
                          {/* Section scores expandable row */}
                          {a.sectionScores && Object.keys(a.sectionScores).length > 0 && (
                            <tr>
                              <td colSpan={7} className="tr-section-scores-row">
                                <div className="tr-section-scores">
                                  {Object.entries(a.sectionScores).map(([subj, marks]) => (
                                    <span key={subj} className="tr-section-chip">
                                      <span className="tr-section-name">{sectionNames[subj] || subj}</span>
                                      <strong>{marks}</strong>
                                    </span>
                                  ))}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          )
        })
      )}
    </div>
  )
}
