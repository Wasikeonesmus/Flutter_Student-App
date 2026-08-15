import { useEffect, useState } from 'react'
import { collection, doc, getDoc, getDocs, updateDoc, query, where } from 'firebase/firestore'
import { Calendar, Filter, Rocket, Lock, Unlock } from 'lucide-react'
import { db } from '../firebase'
import {
  getResultsReleaseLabel,
  getResultsReleaseStatus,
  releaseResultsPatch,
} from '../utils/resultsRelease'
import { logAdminAction } from '../utils/auditLog'
import '../styles/ExamResultsRelease.css'

export default function ExamResultsRelease({ user }) {
  const [exams, setExams] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('all')
  const [actionLoading, setActionLoading] = useState(null)
  const isInstructor = user?.role === 'instructor'

  useEffect(() => {
    async function load() {
      try {
        const snap = isInstructor
          ? await getDocs(query(collection(db, 'tests'), where('instructorId', '==', user.uid)))
          : await getDocs(collection(db, 'tests'))
        const rows = snap.docs.map(d => ({
          docId: d.id,
          ...d.data(),
        }))
        rows.sort((a, b) => (a.title || '').localeCompare(b.title || ''))
        setExams(rows)
      } catch (err) {
        console.error(err)
        alert('Failed to load exams: ' + err.message)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [isInstructor, user])

  async function handleReleaseNow(exam) {
    const title = exam.title || exam.testId || exam.docId
    if (!window.confirm(`Release results now for "${title}"?\n\nStudents can then watch the ad / pay to view scores.`)) {
      return
    }
    setActionLoading(exam.docId)
    try {
      const patch = releaseResultsPatch()
      await updateDoc(doc(db, 'tests', exam.docId), patch)

      const publicId = (exam.testId || exam.docId || '').trim().toUpperCase()
      if (publicId) {
        const publicRef = doc(db, 'tests_public', publicId)
        const publicSnap = await getDoc(publicRef)
        if (publicSnap.exists()) {
          await updateDoc(publicRef, patch)
        }
      }

      setExams(prev =>
        prev.map(e =>
          e.docId === exam.docId
            ? { ...e, resultsReleasedEarly: true, resultReleaseTime: patch.resultReleaseTime }
            : e
        )
      )
      if (user?.role === 'superadmin') {
        await logAdminAction('results_release', { testId: publicId || exam.docId, title })
      }
    } catch (err) {
      alert('Release failed: ' + err.message)
    } finally {
      setActionLoading(null)
    }
  }

  const filtered = exams.filter(e => {
    if (filter === 'all') return true
    const status = getResultsReleaseStatus(e)
    return filter === 'locked' ? status === 'locked' : status === 'released'
  })

  if (loading) return <div className="loading-screen">Loading exams...</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <div className="header-flex">
          <div>
            <h2 className="page-heading gradient-text">Exam Results Release</h2>
            <p className="page-subtitle">
              Release scores before the scheduled time. Students still need ad (free score) or payment (full details).
            </p>
          </div>
          <div className="filter-group glass-panel">
            <Filter size={18} className="filter-icon" />
            <select value={filter} onChange={e => setFilter(e.target.value)}>
              <option value="all">All exams</option>
              <option value="locked">Locked only</option>
              <option value="released">Released</option>
            </select>
          </div>
        </div>
      </header>

      <section className="glass-panel results-release-table-wrap">
        <table className="admin-table results-release-table">
          <thead>
            <tr>
              <th>Exam</th>
              <th>Exam ID</th>
              <th>Release status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={4} className="empty-state">No exams match this filter.</td>
              </tr>
            ) : (
              filtered.map(exam => {
                const status = getResultsReleaseStatus(exam)
                const isReleased = status === 'released'
                return (
                  <tr key={exam.docId}>
                    <td>
                      <strong>{exam.title || '—'}</strong>
                      <div className="exam-meta">{exam.totalMarks ?? '—'} marks · {exam.durationMinutes ?? 60} min</div>
                    </td>
                    <td>
                      <code className="exam-id-code">{exam.testId || exam.docId}</code>
                    </td>
                    <td>
                      <span className={`badge badge-${isReleased ? 'success' : 'warning'}`}>
                        {isReleased ? <Unlock size={12} /> : <Lock size={12} />}
                        {isReleased ? ' Released' : ' Locked'}
                      </span>
                      <div className="release-schedule">
                        <Calendar size={12} />
                        {getResultsReleaseLabel(exam)}
                      </div>
                    </td>
                    <td>
                      {isReleased ? (
                        <span className="released-done">✓ Available to students</span>
                      ) : (
                        <button
                          type="button"
                          className="btn-release-now"
                          disabled={actionLoading === exam.docId}
                          onClick={() => handleReleaseNow(exam)}
                        >
                          <Rocket size={16} />
                          {actionLoading === exam.docId ? 'Releasing…' : 'Release results now'}
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </section>
    </div>
  )
}
