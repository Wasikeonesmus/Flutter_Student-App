import { useEffect, useState } from 'react'
import { collection, onSnapshot, doc, updateDoc, deleteDoc, getDocs, getDoc, setDoc, query, where, limit, serverTimestamp, Timestamp } from 'firebase/firestore'
import { 
  DollarSign, 
  Eye, 
  CheckCircle, 
  XCircle, 
  Clock,
  Filter,
  ExternalLink,
  ImageIcon,
  Trash2,
  Search,
} from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import { logAdminAction } from '../utils/auditLog'
import '../styles/PaymentManagement.css'
import '../styles/AdminData.css'
import { normalizeInstructorTier, INSTRUCTOR_TIER_LABELS } from '../utils/normalizeInstructorTier'
import { subscriptionDaysForPlan } from '../utils/planPricing'

const LEGACY_PLAN_LABELS = {
  weekly: 'Weekly',
  monthly: 'Monthly',
  sixmonths: 'Six Months',
  yearly: 'Yearly',
  student_result: 'Student — full results',
}

function formatPlanLabel(plan) {
  if (!plan) return '—'
  const key = String(plan).toLowerCase()
  if (LEGACY_PLAN_LABELS[key]) return LEGACY_PLAN_LABELS[key]
  const tier = normalizeInstructorTier(plan)
  return INSTRUCTOR_TIER_LABELS[tier] || plan
}

export default function PaymentManagement() {
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('all')
  const [previewUrl, setPreviewUrl] = useState(null)
  const [previewIsPdf, setPreviewIsPdf] = useState(false)
  const [actionLoading, setActionLoading] = useState(null)
  const [searchTerm, setSearchTerm] = useState('')

  function openPreview(url) {
    setPreviewUrl(url)
    setPreviewIsPdf(url?.toLowerCase().endsWith('.pdf') || url?.startsWith('data:application/pdf'))
  }

  function handleOpenExternal(url, e) {
    e?.preventDefault()
    if (url?.startsWith('data:application/pdf')) {
      try {
        const base64 = url.split(',')[1]
        const binary = atob(base64)
        const array = new Uint8Array(binary.length)
        for (let i = 0; i < binary.length; i++) {
          array[i] = binary.charCodeAt(i)
        }
        const blob = new Blob([array], { type: 'application/pdf' })
        const blobUrl = URL.createObjectURL(blob)
        window.open(blobUrl, '_blank')
      } catch (err) {
        alert('Failed to open PDF: ' + err.message)
      }
    } else if (url?.startsWith('data:image/')) {
      const w = window.open('')
      w.document.write(`<iframe src="${url}" frameborder="0" style="border:0; top:0px; left:0px; bottom:0px; right:0px; width:100%; height:100%;" allowfullscreen></iframe>`)
    } else {
      window.open(url, '_blank')
    }
  }

  useEffect(() => {
    const unsub = onSnapshot(collection(db, 'payments'), (snap) => {
      setPayments(snap.docs.map(d => ({ id: d.id, ...d.data() })))
      setLoading(false)
    })
    return () => unsub()
  }, [])

  async function buildCorrectAnswersMap(testId) {
    const id = (testId || '').trim().toUpperCase()
    if (!id) return {}
    const keySnap = await getDoc(doc(db, 'tests_answerkeys', id))
    if (keySnap.exists()) {
      const fromKey = keySnap.data().answers || {}
      if (Object.keys(fromKey).length > 0) return fromKey
    }
    const testSnap = await getDoc(doc(db, 'tests', id))
    if (!testSnap.exists()) return {}
    const correctAnswers = {}
    ;(testSnap.data().sections || []).forEach(sec => {
      ;(sec.questions || []).forEach(q => {
        if (q.id && q.correctAnswer) {
          correctAnswers[q.id] = String(q.correctAnswer).trim().toUpperCase().charAt(0)
        }
      })
    })
    return correctAnswers
  }

  async function handleAction(payment, status) {
    setActionLoading(payment.id + status)
    try {
      // Update payment status
      await updateDoc(doc(db, 'payments', payment.id), { status })

      if (status === 'approved') {
        const isStudentPayment = payment.paymentType === 'student_result' || payment.plan?.toLowerCase() === 'student_result'
        
        if (isStudentPayment) {
          let attemptDocId = payment.attemptId?.trim() || ''
          if (!attemptDocId && payment.testId) {
            const q = query(
              collection(db, 'attempts'),
              where('testId', '==', payment.testId.trim().toUpperCase()),
              limit(20)
            )
            const snap = await getDocs(q)
            const name = (payment.studentName || '').trim().toLowerCase()
            const match = snap.docs.find(d =>
              !name || (d.data().studentName || '').trim().toLowerCase() === name
            )
            attemptDocId = match?.id || snap.docs[0]?.id || ''
          }
          if (attemptDocId) {
            const correctAnswers = await buildCorrectAnswersMap(payment.testId)
            await updateDoc(doc(db, 'attempts', attemptDocId), {
              hasPaidForDetails: true,
              ...(Object.keys(correctAnswers).length > 0 ? { correctAnswers } : {})
            })
          } else {
            alert(`Warning: Could not link this payment to an attempt. Ask the student to resubmit payment from the app.`)
          }
        } else {
          // Instructor subscription payment
          const usersSnap = await getDocs(collection(db, 'users'))
          const targetUser = usersSnap.docs.find(d => 
            d.data().email?.trim().toLowerCase() === payment.userEmail?.trim().toLowerCase()
          )
          if (targetUser) {
            const tier = normalizeInstructorTier(payment.plan)
            const userData = targetUser.data()
            const updates = {
              approvalStatus: 'approved',
              subscriptionStatus: 'active',
              subscriptionTier: tier
            }
            if (tier === 'institute') {
              let instituteId = userData.instituteId || ''
              if (!instituteId) {
                const instituteRef = doc(collection(db, 'institutes'))
                instituteId = instituteRef.id
                const instituteName = `${userData.name || 'Academy'} Institute`
                await setDoc(instituteRef, {
                  instituteId,
                  name: instituteName,
                  ownerUid: targetUser.id,
                  ownerEmail: (payment.userEmail || userData.email || '').trim().toLowerCase(),
                  createdAt: serverTimestamp()
                })
              }
              const memberRef = doc(db, 'institutes', instituteId, 'members', targetUser.id)
              const memberSnap = await getDoc(memberRef)
              if (!memberSnap.exists()) {
                await setDoc(memberRef, {
                  uid: targetUser.id,
                  email: userData.email || payment.userEmail,
                  name: userData.name || '',
                  role: 'owner',
                  status: 'active',
                  addedAt: serverTimestamp()
                })
              }
              updates.instituteId = instituteId
              updates.instituteRole = 'owner'
            }
            await updateDoc(targetUser.ref, updates)
            const days = subscriptionDaysForPlan(tier)
            const end = new Date()
            end.setDate(end.getDate() + days)
            await setDoc(
              doc(db, 'subscriptions', targetUser.id),
              {
                instructorId: targetUser.id,
                plan: tier,
                startDate: Timestamp.now(),
                endDate: Timestamp.fromDate(end),
                isActive: true,
              },
              { merge: true }
            )
          } else {
            alert(`Warning: No user found with email "${payment.userEmail}". Payment marked approved but account not activated.`)
          }
        }
      }
      await logAdminAction(`payment_${status}`, {
        paymentId: payment.id,
        plan: payment.plan,
        email: payment.userEmail || payment.studentName,
      })
    } catch (err) {
      alert('Action failed: ' + err.message)
    } finally {
      setActionLoading(null)
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Are you sure you want to delete this payment record?')) return
    try {
      await deleteDoc(doc(db, 'payments', id))
      await logAdminAction('payment_delete', { paymentId: id })
    } catch (err) {
      alert('Failed to delete: ' + err.message)
    }
  }

  const filtered = payments.filter((p) => {
    if (filter !== 'all' && p.status !== filter) return false
    const q = searchTerm.trim().toLowerCase()
    if (!q) return true
    return (
      (p.userEmail || '').toLowerCase().includes(q) ||
      (p.studentName || '').toLowerCase().includes(q) ||
      (p.referenceNumber || '').toLowerCase().includes(q) ||
      (p.plan || '').toLowerCase().includes(q) ||
      (p.testId || '').toLowerCase().includes(q)
    )
  })

  if (loading) return <div className="loading-screen">Loading payments...</div>

  return (
    <div className="page-wrapper">
      {/* Receipt Preview Modal */}
      {previewUrl && (
              <div className="receipt-modal-overlay" onClick={() => setPreviewUrl(null)}>
          <div className="receipt-modal" onClick={e => e.stopPropagation()}>
            <div className="receipt-modal-header">
              <span>{previewIsPdf ? '📄 Payment Receipt (PDF)' : '🖼️ Payment Receipt'}</span>
              <div style={{ display: 'flex', gap: 8 }}>
                <a href={previewUrl} onClick={(e) => handleOpenExternal(previewUrl, e)} className="btn-icon view" title="Open in new tab">
                  <ExternalLink size={16} />
                </a>
                <button className="btn-icon reject" onClick={() => setPreviewUrl(null)} title="Close">
                  <XCircle size={16} />
                </button>
              </div>
            </div>
            <div className="receipt-modal-body">
              {previewIsPdf ? (
                <div style={{ textAlign: 'center', padding: '32px 20px' }}>
                  <div style={{ fontSize: 64, marginBottom: 16 }}>📄</div>
                  <p style={{ color: '#ccc', marginBottom: 20 }}>PDF files cannot be previewed inline.</p>
                  <a href={previewUrl} onClick={(e) => handleOpenExternal(previewUrl, e)}
                    style={{ background: '#ef4444', color: '#fff', padding: '10px 24px', borderRadius: 8, textDecoration: 'none', fontWeight: 600, display: 'inline-block' }}>
                    Open PDF ↗
                  </a>
                </div>
              ) : (
                <>
                  <img
                    src={previewUrl}
                    alt="Payment receipt"
                    className="receipt-preview-img"
                    onError={e => { e.target.style.display='none'; e.target.nextSibling.style.display='block' }}
                  />
                  <div style={{ display: 'none', textAlign: 'center', padding: 32 }}>
                    <p>Cannot display preview.</p>
                    <a href={previewUrl} onClick={(e) => handleOpenExternal(previewUrl, e)} className="btn-primary">Open File ↗</a>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      <header className="page-header">
        <div className="header-flex">
          <div>
            <h2 className="page-heading gradient-text">Payment Verifications</h2>
            <p className="page-subtitle">Review receipts and activate instructor accounts</p>
          </div>
          <div className="search-container glass-panel" style={{ minWidth: 220 }}>
            <Search size={18} style={{ color: '#737373' }} />
            <input
              placeholder="Email, ref#, plan…"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div className="filter-group glass-panel">
            <Filter size={18} className="filter-icon" />
            <select value={filter} onChange={e => setFilter(e.target.value)}>
              <option value="all">All Payments</option>
              <option value="pending">Pending Only</option>
              <option value="approved">Approved</option>
              <option value="rejected">Rejected</option>
            </select>
          </div>
        </div>
      </header>

      <div className="glass-panel table-card">
        <table className="admin-table">
          <thead>
            <tr>
              <th>User / Type</th>
              <th>Payment Details</th>
              <th>Receipt</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length > 0 ? filtered.map(pay => (
              <tr key={pay.id}>
                <td>
                  <div className="instructor-cell">
                    <div className="instructor-avatar"><DollarSign size={18} /></div>
                    <div>
                      <div className="instructor-email">
                        {pay.paymentType === 'student_result' ? (pay.studentName || 'Student') : (pay.userEmail || '—')}
                      </div>
                      <div className="instructor-name" style={{ fontSize: 11, color: '#888' }}>
                        {pay.paymentType === 'student_result' ? 'Student Result Payment' : 'Instructor Subscription'}
                        <br/>
                        {formatFirestoreDate(pay.createdAt).split(',')[0]}
                      </div>
                    </div>
                  </div>
                </td>
                <td>
                  <div className="payment-details">
                    <div className="detail-row">
                      <span className="detail-label">Plan:</span>
                      <span className="detail-value">{formatPlanLabel(pay.plan)}</span>
                    </div>
                    <div className="detail-row">
                      <span className="detail-label">Ref#:</span>
                      <span className="detail-value">{pay.referenceNumber || '—'}</span>
                    </div>
                  </div>
                </td>
                <td>
                  {pay.screenshotUrl ? (
                    (() => {
                      const isPdf = pay.screenshotUrl.toLowerCase().endsWith('.pdf') || pay.screenshotUrl.startsWith('data:application/pdf')
                      return (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          {isPdf ? (
                            <div
                              className="receipt-thumb pdf-thumb"
                              onClick={() => openPreview(pay.screenshotUrl)}
                              title="Click to open PDF"
                            >
                              📄
                            </div>
                          ) : (
                            <img
                              src={pay.screenshotUrl}
                              alt="receipt"
                              className="receipt-thumb"
                              onClick={() => openPreview(pay.screenshotUrl)}
                              title="Click to preview"
                            />
                          )}
                          <a
                            href={pay.screenshotUrl}
                            onClick={(e) => handleOpenExternal(pay.screenshotUrl, e)}
                            className="btn-icon view"
                            title={isPdf ? 'Open PDF' : 'Open full image'}
                          >
                            <ExternalLink size={15} />
                          </a>
                        </div>
                      )
                    })()
                  ) : (
                    <span style={{ color: '#aaa', fontSize: 12 }}>No file</span>
                  )}
                </td>
                <td>
                  <span className={`badge badge-${pay.status === 'approved' ? 'success' : pay.status === 'pending' ? 'warning' : 'error'}`}>
                    {pay.status || 'pending'}
                  </span>
                </td>
                <td>
                  <div className="action-row">
                    {pay.status === 'pending' && (
                      <>
                        <button
                          className="btn-icon approve"
                          onClick={() => handleAction(pay, 'approved')}
                          disabled={actionLoading !== null}
                          title="Approve & Activate"
                        >
                          {actionLoading === pay.id + 'approved'
                            ? <Clock size={18} className="spin" />
                            : <CheckCircle size={18} />}
                        </button>
                        <button
                          className="btn-icon reject"
                          onClick={() => handleAction(pay, 'rejected')}
                          disabled={actionLoading !== null}
                          title="Reject"
                        >
                          {actionLoading === pay.id + 'rejected'
                            ? <Clock size={18} className="spin" />
                            : <XCircle size={18} />}
                        </button>
                      </>
                    )}
                    {pay.status !== 'pending' && (
                      <span style={{ fontSize: 12, color: '#888', marginRight: 8 }}>
                        {pay.status === 'approved' ? '✓ Done' : '✗ Rejected'}
                      </span>
                    )}
                    <button className="btn-icon delete" onClick={() => handleDelete(pay.id)} title="Delete Record">
                      <Trash2 size={18} />
                    </button>
                  </div>
                </td>
              </tr>
            )) : (
              <tr>
                <td colSpan="5" className="empty-state">No payment records found.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
