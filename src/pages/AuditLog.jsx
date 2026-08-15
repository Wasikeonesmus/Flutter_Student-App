import { useEffect, useState } from 'react'
import { collection, query, orderBy, limit, getDocs } from 'firebase/firestore'
import { ScrollText, RefreshCw } from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import '../styles/AdminData.css'
import '../styles/AuditLog.css'

/* Map action names → badge colour class */
const ACTION_COLOR = {
  SETTINGS_SAVE:       'audit-blue',
  settings_save:       'audit-blue',
  INSTRUCTOR_TIER_SET: 'audit-purple',
  instructor_tier_set: 'audit-purple',
  PAYMENT_APPROVED:    'audit-green',
  payment_approved:    'audit-green',
  PAYMENT_REJECTED:    'audit-red',
  payment_rejected:    'audit-red',
  EXAM_DELETE:         'audit-red',
  exam_delete:         'audit-red',
  EXAM_TOGGLE:         'audit-orange',
  exam_toggle:         'audit-orange',
  RESULTS_RELEASE:     'audit-yellow',
  results_release:     'audit-yellow',
  EMAIL_TEST:          'audit-blue',
  email_test:          'audit-blue',
}

/* Human-readable labels for known detail keys */
const KEY_LABELS = {
  scope:      'Scope',
  tier:       'Tier',
  email:      'Email',
  plan:       'Plan',
  paymentId:  'Payment ID',
  testId:     'Exam ID',
  title:      'Exam',
  isEnabled:  'Enabled',
  to:         'Sent To',
  userId:     null,   // skip — internal
}

/* Fields to hide from the details display */
const SKIP_KEYS = new Set(['userId', 'adminUid', 'uid'])

function ActionBadge({ action }) {
  const cls = ACTION_COLOR[action] || 'audit-muted'
  // Turn PAYMENT_APPROVED → "Payment Approved"
  const label = (action || '').replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
  return <span className={`audit-badge ${cls}`}>{label}</span>
}

/* Order in which details keys should be displayed */
const KEY_ORDER = ['testId', 'title', 'email', 'plan', 'tier', 'scope', 'paymentId', 'isEnabled', 'to']

/* Render details object as key=value pills */
function DetailsPills({ details }) {
  let parsed = details
  if (typeof details === 'string') {
    try { parsed = JSON.parse(details) } catch (e) { parsed = { value: details } }
  }

  if (!parsed || typeof parsed !== 'object') {
    return <span className="muted">—</span>
  }

  const entries = Object.entries(parsed).filter(
    ([k, v]) => !SKIP_KEYS.has(k) && KEY_LABELS[k] !== null && v !== undefined && v !== null && v !== ''
  )
  if (!entries.length) return <span className="muted">—</span>

  // Sort entries based on KEY_ORDER
  entries.sort((a, b) => {
    const idxA = KEY_ORDER.indexOf(a[0])
    const idxB = KEY_ORDER.indexOf(b[0])
    if (idxA !== -1 && idxB !== -1) return idxA - idxB
    if (idxA !== -1) return -1
    if (idxB !== -1) return 1
    return a[0].localeCompare(b[0])
  })

  const formatKey = (k) => KEY_LABELS[k] || k.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())
  const truncate  = (str) => String(str).length > 22 ? String(str).slice(0, 10) + '…' + String(str).slice(-6) : String(str)

  return (
    <div className="audit-details">
      {entries.map(([k, v]) => {
        const valStr = typeof v === 'boolean' ? (v ? 'yes' : 'no') : String(v)
        return (
          <span key={k} className="audit-detail-chip" title={valStr}>
            <span className="audit-detail-key">{formatKey(k)}</span>
            <span className="audit-detail-val">{truncate(valStr)}</span>
          </span>
        )
      })}
    </div>
  )
}

export default function AuditLog() {
  const [entries, setEntries] = useState([])
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    try {
      const q = query(collection(db, 'admin_audit'), orderBy('createdAt', 'desc'), limit(200))
      const snap = await getDocs(q)
      setEntries(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    } catch (err) {
      console.error(err)
      setEntries([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <div className="header-flex">
          <div>
            <h2 className="page-heading gradient-text">Audit Log</h2>
            <p className="page-subtitle">Recent super-admin actions on the platform</p>
          </div>
          <button type="button" className="btn-sm primary" onClick={load} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </header>

      {loading ? (
        <div className="loading-screen">Loading audit log…</div>
      ) : (
        <div className="glass-panel table-card">
          <table className="admin-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Admin</th>
                <th>Action</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {entries.length ? (
                entries.map((e) => (
                  <tr key={e.id}>
                    <td className="muted audit-when">{formatFirestoreDate(e.createdAt)}</td>
                    <td className="audit-email">{e.adminEmail || '—'}</td>
                    <td><ActionBadge action={e.action} /></td>
                    <td><DetailsPills details={e.details} /></td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={4} className="empty-state">
                    <ScrollText size={32} style={{ opacity: 0.4, marginBottom: 8 }} />
                    <p>No audit entries yet. Actions from Exams, Payments, and Subscriptions will appear here.</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
