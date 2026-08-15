import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  where,
  addDoc,
  onSnapshot,
  serverTimestamp,
} from 'firebase/firestore'
import { db } from '../firebase'
import { getResultsReleaseStatus } from '../utils/resultsRelease'
import '../styles/StudentResult.css'

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

export default function StudentResult() {
  const { attemptId } = useParams()
  const [attempt, setAttempt] = useState(null)
  const [test, setTest] = useState(null)
  const [loading, setLoading] = useState(true)
  const [adWatched, setAdWatched] = useState(false)
  const [reference, setReference] = useState('')
  const [receiptFile, setReceiptFile] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [paymentStatus, setPaymentStatus] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!attemptId) return
    const unsub = onSnapshot(doc(db, 'attempts', attemptId), (snap) => {
      if (snap.exists()) setAttempt({ id: snap.id, ...snap.data() })
      else setAttempt(null)
      setLoading(false)
    })
    return () => unsub()
  }, [attemptId])

  useEffect(() => {
    async function loadTest() {
      if (!attempt?.testId) return
      const id = attempt.testId.trim().toUpperCase()
      const publicSnap = await getDoc(doc(db, 'tests_public', id))
      if (publicSnap.exists()) {
        setTest({ id: publicSnap.id, ...publicSnap.data() })
        return
      }
      try {
        let snap = await getDoc(doc(db, 'tests', id))
        if (!snap.exists()) {
          const res = await getDocs(query(collection(db, 'tests'), where('testId', '==', id)))
          if (!res.empty) snap = res.docs[0]
        }
        if (snap?.exists?.()) setTest({ id: snap.id, ...snap.data() })
      } catch {
        setTest({ testId: id, title: id })
      }
    }
    loadTest()
  }, [attempt?.testId])

  const releaseStatus = test ? getResultsReleaseStatus(test) : 'locked'
  const resultsReleased = releaseStatus === 'released'
  const sectionScores = attempt?.sectionScores
    ? Object.entries(attempt.sectionScores).map(([subject, marks]) => ({ subject, marks }))
    : []

  async function handleSubmitPayment(e) {
    e.preventDefault()
    if (!attemptId || !attempt) return
    if (!reference.trim()) {
      setError('Enter your payment reference / transaction ID.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      let screenshotUrl = ''
      if (receiptFile) {
        screenshotUrl = await readFileAsDataUrl(receiptFile)
      }
      await addDoc(collection(db, 'payments'), {
        paymentType: 'student_result',
        attemptId,
        testId: attempt.testId || '',
        studentName: attempt.studentName || '',
        userEmail: '',
        plan: 'student_result',
        referenceNumber: reference.trim(),
        screenshotUrl,
        status: 'pending',
        createdAt: serverTimestamp(),
      })
      setPaymentStatus('pending')
    } catch (err) {
      setError(err.message || 'Could not submit payment.')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="student-result-page">
        <div className="loading-screen">Loading result…</div>
      </div>
    )
  }

  if (!attempt) {
    return (
      <div className="student-result-page">
        <div className="glass-panel student-result-card">
          <h2>Result not found</h2>
          <p className="muted">Invalid or expired attempt link.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="student-result-page">
      <div className="glass-panel student-result-card">
        <h2 className="gradient-text">Your Exam Result</h2>
        {test?.title && <p className="muted exam-title">{test.title}</p>}

        {!resultsReleased && !attempt.hasPaidForDetails && (
          <p className="notice warn">
            Results are not released yet. Check back after the instructor or admin releases them.
          </p>
        )}

        {resultsReleased && !adWatched && !attempt.hasPaidForDetails && (
          <button type="button" className="premium-button full-width" onClick={() => setAdWatched(true)}>
            Continue to view score
          </button>
        )}

        {(adWatched || attempt.hasPaidForDetails) && resultsReleased && (
          <div className="score-block">
            <p className="score-label">Total score</p>
            <p className="score-value">{attempt.totalScore ?? '—'}</p>
            {attempt.rank ? <p className="muted">Rank #{attempt.rank}</p> : null}
          </div>
        )}

        {attempt.hasPaidForDetails && sectionScores.length > 0 && (
          <div className="details-block">
            <h3>Section scores</h3>
            <ul>
              {sectionScores.map((s) => (
                <li key={s.subject}>
                  <span>{s.subject}</span>
                  <strong>{s.marks}</strong>
                </li>
              ))}
            </ul>
          </div>
        )}

        {resultsReleased && adWatched && !attempt.hasPaidForDetails && (
          <div className="payment-block">
            <h3>Unlock full breakdown</h3>
            <p className="muted">
              Pay via JazzCash / Easypaisa (see app instructions), then submit your reference and receipt. Admin will approve within 24h.
            </p>
            {paymentStatus === 'pending' ? (
              <p className="notice ok">Payment submitted — pending admin approval. Refresh after approval.</p>
            ) : (
              <form onSubmit={handleSubmitPayment}>
                <label>
                  Reference / transaction ID
                  <input
                    value={reference}
                    onChange={(e) => setReference(e.target.value)}
                    placeholder="e.g. T123456789"
                    required
                  />
                </label>
                <label>
                  Receipt (optional image)
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => setReceiptFile(e.target.files?.[0] || null)}
                  />
                </label>
                {error && <p className="notice err">{error}</p>}
                <button type="submit" className="premium-button full-width" disabled={submitting}>
                  {submitting ? 'Submitting…' : 'Submit payment for review'}
                </button>
              </form>
            )}
          </div>
        )}

        {attempt.cheatAlerts > 0 && (
          <p className="notice warn" style={{ marginTop: 16 }}>
            This attempt has {attempt.cheatAlerts} anti-cheat alert(s) on record.
          </p>
        )}
      </div>
    </div>
  )
}
