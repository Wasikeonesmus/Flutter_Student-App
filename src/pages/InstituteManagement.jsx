import { useEffect, useState } from 'react'
import {
  collection,
  onSnapshot,
  getDocs,
  doc,
  deleteDoc,
  updateDoc,
} from 'firebase/firestore'
import { Building2, Users, Layers, GraduationCap, ChevronDown, ChevronUp, UserMinus } from 'lucide-react'
import { db } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import { logAdminAction } from '../utils/auditLog'
import '../styles/InstituteManagement.css'
import '../styles/AdminData.css'

export default function InstituteManagement() {
  const [institutes, setInstitutes] = useState([])
  const [loading, setLoading] = useState(true)
  const [expandedId, setExpandedId] = useState(null)
  const [detail, setDetail] = useState({ members: [], batches: [] })
  const [detailLoading, setDetailLoading] = useState(false)

  useEffect(() => {
    const unsub = onSnapshot(collection(db, 'institutes'), async (snap) => {
      const list = await Promise.all(
        snap.docs.map(async (d) => {
          const data = d.data()
          const membersSnap = await getDocs(collection(db, 'institutes', d.id, 'members'))
          const batchesSnap = await getDocs(collection(db, 'institutes', d.id, 'batches'))
          return {
            id: d.id,
            ...data,
            memberCount: membersSnap.size,
            batchCount: batchesSnap.size,
          }
        })
      )
      setInstitutes(list)
      setLoading(false)
    })
    return () => unsub()
  }, [])

  async function loadDetail(instituteId) {
    if (expandedId === instituteId) {
      setExpandedId(null)
      return
    }
    setExpandedId(instituteId)
    setDetailLoading(true)
    try {
      const [membersSnap, batchesSnap] = await Promise.all([
        getDocs(collection(db, 'institutes', instituteId, 'members')),
        getDocs(collection(db, 'institutes', instituteId, 'batches')),
      ])
      setDetail({
        members: membersSnap.docs.map((d) => ({ id: d.id, ...d.data() })),
        batches: batchesSnap.docs.map((d) => ({ id: d.id, ...d.data() })),
      })
    } finally {
      setDetailLoading(false)
    }
  }

  async function removeMember(instituteId, memberId, email) {
    if (!window.confirm(`Remove member ${email || memberId} from institute?`)) return
    try {
      await deleteDoc(doc(db, 'institutes', instituteId, 'members', memberId))
      await updateDoc(doc(db, 'users', memberId), { instituteId: '', instituteRole: '' }).catch(() => {})
      await logAdminAction('institute_remove_member', { instituteId, memberId, email })
      setDetail((prev) => ({
        ...prev,
        members: prev.members.filter((m) => m.id !== memberId),
      }))
    } catch (err) {
      alert(err.message)
    }
  }

  return (
    <div className="institute-page">
      <header className="page-header">
        <div>
          <h1>Institutes</h1>
          <p>View members and batches · remove members when needed</p>
        </div>
      </header>

      {loading ? (
        <p className="muted">Loading institutes…</p>
      ) : institutes.length === 0 ? (
        <div className="glass-panel empty-institute">
          <Building2 size={40} className="empty-icon" />
          <p>No institutes yet.</p>
          <p className="muted">Approve an instructor with the <strong>Institute</strong> plan to create one.</p>
        </div>
      ) : (
        <div className="institute-grid">
          {institutes.map((inst) => (
            <article key={inst.id} className="glass-panel institute-card">
              <div className="institute-card-head">
                <Building2 size={22} className="icon-p" />
                <h3>{inst.name || 'Unnamed institute'}</h3>
              </div>
              <p className="institute-owner">Owner: {inst.ownerEmail || '—'}</p>
              <p className="institute-meta">Created: {formatFirestoreDate(inst.createdAt)}</p>
              <div className="institute-stats">
                <span><Users size={14} /> {inst.memberCount} members</span>
                <span><Layers size={14} /> {inst.batchCount} batches</span>
              </div>
              <button type="button" className="btn-sm" style={{ marginTop: 12 }} onClick={() => loadDetail(inst.id)}>
                {expandedId === inst.id ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                {expandedId === inst.id ? 'Hide' : 'Manage'}
              </button>

              {expandedId === inst.id && (
                <div className="institute-detail" style={{ marginTop: 16, borderTop: '1px solid var(--panel-border)', paddingTop: 16 }}>
                  {detailLoading ? (
                    <p className="muted">Loading…</p>
                  ) : (
                    <>
                      <h4 style={{ fontSize: '0.85rem', marginBottom: 8 }}>Members</h4>
                      {detail.members.length ? (
                        <ul className="institute-member-list">
                          {detail.members.map((m) => (
                            <li key={m.id}>
                              <span>
                                {m.name || m.email || m.id}
                                <span className="muted"> · {m.role || 'member'}</span>
                              </span>
                              {m.role !== 'owner' && (
                                <button
                                  type="button"
                                  className="btn-sm danger"
                                  onClick={() => removeMember(inst.id, m.id, m.email)}
                                  title="Remove member"
                                >
                                  <UserMinus size={12} />
                                </button>
                              )}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="muted">No members</p>
                      )}
                      <h4 style={{ fontSize: '0.85rem', margin: '16px 0 8px' }}>Batches</h4>
                      {detail.batches.length ? (
                        <ul className="institute-member-list">
                          {detail.batches.map((b) => (
                            <li key={b.id}>
                              <span>{b.name || b.title || b.id}</span>
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p className="muted">No batches — create in Android Institute Hub</p>
                      )}
                      <p className="institute-hint" style={{ marginTop: 12 }}>
                        <GraduationCap size={14} /> Attendance is managed in the Android app.
                      </p>
                    </>
                  )}
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  )
}
