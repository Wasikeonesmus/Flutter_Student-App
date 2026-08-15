import { useEffect, useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { collection, doc, getDocs, updateDoc, deleteDoc, query, where, setDoc, serverTimestamp } from 'firebase/firestore'
import { FileText, Search, Shield, ToggleLeft, ToggleRight, Trash2, Edit, Copy } from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import { antiCheatSummary } from '../utils/antiCheatLabels'
import { logAdminAction } from '../utils/auditLog'
import '../styles/AdminData.css'

export default function ExamManagement({ user }) {
  const [exams, setExams] = useState([])
  const [instructors, setInstructors] = useState({})
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [selected, setSelected] = useState(null)
  const [busy, setBusy] = useState(null)
  const isInstructor = user?.role === 'instructor'

  useEffect(() => {
    async function load() {
      try {
        let testSnap
        let userSnap
        
        if (isInstructor) {
          testSnap = await getDocs(query(collection(db, 'tests'), where('instructorId', '==', user.uid)))
          const instMap = { [user.uid]: user }
          setInstructors(instMap)
        } else {
          const results = await Promise.all([
            getDocs(collection(db, 'tests')),
            getDocs(collection(db, 'users')),
          ])
          testSnap = results[0]
          userSnap = results[1]
          const instMap = {}
          userSnap.docs.forEach((d) => {
            const u = d.data()
            if (u.role === 'instructor') instMap[d.id] = u
          })
          setInstructors(instMap)
        }

        const rows = testSnap.docs.map((d) => ({
          docId: d.id,
          ...d.data(),
        }))
        rows.sort((a, b) => (b.createdAt?.toMillis?.() || 0) - (a.createdAt?.toMillis?.() || 0))
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

  const filtered = useMemo(() => {
    return exams.filter((e) => {
      if (filter === 'enabled' && e.isEnabled === false) return false
      if (filter === 'disabled' && e.isEnabled !== false) return false
      const q = search.trim().toLowerCase()
      if (!q) return true
      const inst = instructors[e.instructorId]
      return (
        (e.title || '').toLowerCase().includes(q) ||
        (e.testId || '').toLowerCase().includes(q) ||
        (e.docId || '').toLowerCase().includes(q) ||
        (inst?.email || '').toLowerCase().includes(q) ||
        (inst?.name || '').toLowerCase().includes(q)
      )
    })
  }, [exams, search, filter, instructors])

  const [copiedId, setCopiedId] = useState(null)

  const handleCopy = (id) => {
    navigator.clipboard.writeText(id)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  async function toggleEnabled(exam) {
    const next = exam.isEnabled === false
    setBusy(exam.docId)
    try {
      await updateDoc(doc(db, 'tests', exam.docId), { isEnabled: next })
      await updateDoc(doc(db, 'tests_public', exam.docId), { isEnabled: next }).catch(() => {})
      setExams((prev) =>
        prev.map((e) => (e.docId === exam.docId ? { ...e, isEnabled: next } : e))
      )
      if (user?.role === 'superadmin') {
        await logAdminAction('exam_toggle', { testId: exam.testId || exam.docId, isEnabled: next })
      }
    } catch (err) {
      alert(err.message)
    } finally {
      setBusy(null)
    }
  }

  async function removeExam(exam) {
    const title = exam.title || exam.testId || exam.docId
    if (!window.confirm(`Delete exam "${title}"? This cannot be undone.`)) return
    setBusy(exam.docId)
    try {
      await deleteDoc(doc(db, 'tests', exam.docId))
      await deleteDoc(doc(db, 'tests_public', exam.docId)).catch(() => {})
      setExams((prev) => prev.filter((e) => e.docId !== exam.docId))
      if (selected?.docId === exam.docId) setSelected(null)
      if (user?.role === 'superadmin') {
        await logAdminAction('exam_delete', { testId: exam.testId || exam.docId, title })
      }
    } catch (err) {
      alert(err.message)
    } finally {
      setBusy(null)
    }
  }

  async function duplicateExam(exam) {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    let uniqueId = ''
    for (let i = 0; i < 8; i++) {
      uniqueId += chars.charAt(Math.floor(Math.random() * chars.length))
    }
    
    setBusy(exam.docId)
    try {
      const snap = await getDocs(query(collection(db, 'tests'), where('testId', '==', exam.testId)))
      const testDoc = snap.docs[0]
      if (!testDoc) throw new Error("Test details not found in database.")
      
      const fullTest = testDoc.data()
      const payload = {
        ...fullTest,
        testId: uniqueId,
        title: `Copy of ${fullTest.title || 'Exam'}`,
      }
      
      await setDoc(doc(db, 'tests', uniqueId), {
        ...payload,
        createdAt: serverTimestamp()
      })
      
      const publicSections = (fullTest.sections || []).map(sec => ({
        id: sec.id,
        title: sec.title,
        questions: (sec.questions || []).map(q => ({
          id: q.id,
          text: q.text,
          imageUrl: q.imageUrl || '',
          optionA: q.optionA,
          optionB: q.optionB,
          optionC: q.optionC,
          optionD: q.optionD,
          marks: q.marks || 1
        }))
      }))
      
      await setDoc(doc(db, 'tests_public', uniqueId), {
        testId: uniqueId,
        instructorId: fullTest.instructorId,
        instituteId: fullTest.instituteId || '',
        batchId: fullTest.batchId || '',
        roster: fullTest.roster || [],
        title: `Copy of ${fullTest.title || 'Exam'}`,
        instructions: fullTest.instructions || '',
        durationMinutes: fullTest.durationMinutes || 60,
        passingMarks: fullTest.passingMarks || 0,
        totalMarks: fullTest.totalMarks || 0,
        isEnabled: fullTest.isEnabled !== false,
        releaseScoreMode: fullTest.releaseScoreMode || 'table_only',
        resultReleaseTime: fullTest.resultReleaseTime || null,
        resultsReleasedEarly: fullTest.resultsReleasedEarly || false,
        antiCheatFullscreen: fullTest.antiCheatFullscreen !== false,
        antiCheatDetectLeaveApp: fullTest.antiCheatDetectLeaveApp !== false,
        antiCheatBlockCopyPaste: fullTest.antiCheatBlockCopyPaste !== false,
        antiCheatBlockScreenshot: fullTest.antiCheatBlockScreenshot !== false,
        antiCheatCamera: !!fullTest.antiCheatCamera,
        antiCheatRandomizeQuestions: fullTest.antiCheatRandomizeQuestions !== false,
        antiCheatRandomizeOptions: fullTest.antiCheatRandomizeOptions !== false,
        antiCheatAutoSubmit: fullTest.antiCheatAutoSubmit !== false,
        createdAt: serverTimestamp(),
        sections: publicSections
      })
      
      const newExamObject = {
        docId: uniqueId,
        testId: uniqueId,
        title: `Copy of ${fullTest.title || 'Exam'}`,
        instructorId: fullTest.instructorId,
        isEnabled: fullTest.isEnabled !== false,
        createdAt: { toMillis: () => Date.now() },
        sections: fullTest.sections || [],
        totalMarks: fullTest.totalMarks || 0,
        durationMinutes: fullTest.durationMinutes || 60
      }
      setExams((prev) => [newExamObject, ...prev])
      alert(`Test duplicated successfully! New Test ID: ${uniqueId}`)
    } catch (err) {
      alert('Failed to duplicate: ' + err.message)
    } finally {
      setBusy(null)
    }
  }

  if (loading) return <div className="loading-screen">Loading exams…</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <h2 className="page-heading gradient-text">Exam Management</h2>
        <p className="page-subtitle">{isInstructor ? 'Manage your exams and update anti-cheat settings' : 'Browse, enable/disable, and review anti-cheat settings for all tests'}</p>
      </header>

      <div className="header-flex" style={{ marginBottom: 20 }}>
        <div className="search-container glass-panel">
          <Search size={18} style={{ color: '#737373' }} />
          <input
            placeholder={isInstructor ? "Search title, exam ID..." : "Search title, exam ID, instructor…"}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <div className="filter-group glass-panel">
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="all">All exams</option>
            <option value="enabled">Enabled only</option>
            <option value="disabled">Disabled only</option>
          </select>
        </div>
      </div>

      <div className="glass-panel table-card">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Exam</th>
              {!isInstructor && <th>Instructor</th>}
              <th>Status</th>
              <th>Anti-cheat</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length ? (
              filtered.map((exam) => {
                const inst = instructors[exam.instructorId]
                return (
                  <tr key={exam.docId}>
                    <td>
                      <strong>{exam.title || 'Untitled'}</strong>
                      <div className="muted mono" style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
                        <span>{exam.testId || exam.docId}</span>
                        <button
                          type="button"
                          className="btn-sm"
                          style={{ padding: '2px 6px', fontSize: '0.75rem', height: 'auto', display: 'inline-flex', alignItems: 'center', gap: 4 }}
                          onClick={() => handleCopy(exam.testId || exam.docId)}
                          title="Copy Test ID"
                        >
                          <Copy size={11} />
                          {copiedId === (exam.testId || exam.docId) && <span style={{ fontSize: '0.7rem', color: '#4ade80' }}>Copied!</span>}
                        </button>
                      </div>
                    </td>
                    {!isInstructor && (
                      <td>
                        <div>{inst?.name || '—'}</div>
                        <div className="muted">{inst?.email || exam.instructorId || '—'}</div>
                      </td>
                    )}
                    <td>
                      <span className={`badge ${exam.isEnabled !== false ? 'badge-success' : 'badge-muted'}`}>
                        {exam.isEnabled !== false ? 'Published' : 'Draft'}
                      </span>
                    </td>
                    <td>
                      <span className="muted" style={{ fontSize: '0.8rem' }}>
                        {antiCheatSummary(exam)}
                      </span>
                    </td>
                    <td className="muted">{formatFirestoreDate(exam.createdAt)}</td>
                    <td>
                      <div className="btn-row">
                        <button
                          type="button"
                          className="btn-sm"
                          disabled={busy === exam.docId}
                          onClick={() => toggleEnabled(exam)}
                          title={exam.isEnabled !== false ? 'Disable' : 'Enable'}
                        >
                          {exam.isEnabled !== false ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}
                        </button>
                        <Link
                          to={`/edit-test/${exam.docId}`}
                          className="btn-sm"
                          style={{ display: 'inline-flex', alignItems: 'center', gap: 4, textDecoration: 'none' }}
                          title="Edit"
                        >
                          <Edit size={14} /> Edit
                        </Link>
                        <button
                          type="button"
                          className="btn-sm"
                          disabled={busy === exam.docId}
                          onClick={() => duplicateExam(exam)}
                          title="Duplicate"
                        >
                          <Copy size={14} /> Duplicate
                        </button>
                        <button type="button" className="btn-sm" onClick={() => setSelected(exam)}>
                          <Shield size={14} /> Details
                        </button>
                        <button
                          type="button"
                          className="btn-sm danger"
                          disabled={busy === exam.docId}
                          onClick={() => removeExam(exam)}
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })
            ) : (
              <tr>
                <td colSpan={isInstructor ? 5 : 6} className="empty-state">
                  <FileText size={32} style={{ opacity: 0.4, marginBottom: 8 }} />
                  <p>No exams match your filters.</p>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {selected && (
        <section className="glass-panel detail-drawer">
          <h4>{selected.title} — anti-cheat &amp; settings</h4>
          <ul className="muted" style={{ lineHeight: 1.8, listStyle: 'none' }}>
            <li>Fullscreen: {selected.antiCheatFullscreen !== false ? 'On' : 'Off'}</li>
            <li>Detect leave app: {selected.antiCheatDetectLeaveApp !== false ? 'On' : 'Off'}</li>
            <li>Block copy/paste: {selected.antiCheatBlockCopyPaste !== false ? 'On' : 'Off'}</li>
            <li>Block screenshots: {selected.antiCheatBlockScreenshot !== false ? 'On' : 'Off'}</li>
            <li>Camera proctoring: {selected.antiCheatCamera ? 'On' : 'Off'}</li>
            <li>Shuffle questions: {selected.antiCheatRandomizeQuestions !== false ? 'On' : 'Off'}</li>
            <li>Shuffle options: {selected.antiCheatRandomizeOptions !== false ? 'On' : 'Off'}</li>
            <li>Auto-submit on timeout: {selected.antiCheatAutoSubmit !== false ? 'On' : 'Off'}</li>
            <li>Duration: {selected.durationMinutes ?? 60} min · Passing: {selected.passingMarks ?? 0} / {selected.totalMarks ?? 0}</li>
            <li>Release mode: {selected.releaseScoreMode || 'table_only'}</li>
          </ul>
          <button type="button" className="btn-sm" style={{ marginTop: 12 }} onClick={() => setSelected(null)}>
            Close
          </button>
        </section>
      )}
    </div>
  )
}
