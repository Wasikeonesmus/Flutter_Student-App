import { useEffect, useState, useCallback } from 'react'
import { collection, onSnapshot, doc, updateDoc, deleteDoc, getDoc, setDoc, serverTimestamp, Timestamp } from 'firebase/firestore'
import { 
  Users, 
  Trash2, 
  Search,
  UserCheck,
  UserX,
  RefreshCw,
  AlertCircle
} from 'lucide-react'
import { db, auth } from '../firebase'
import { formatFirestoreDate } from '../utils/firestoreDate'
import '../styles/InstructorManagement.css'
import { normalizeInstructorTier, INSTRUCTOR_TIER_LABELS } from '../utils/normalizeInstructorTier'
import { subscriptionDaysForPlan } from '../utils/planPricing'
import { logAdminAction } from '../utils/auditLog'

export default function InstructorManagement() {
  const [instructors, setInstructors] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [searchTerm, setSearchTerm] = useState('')

  const setupSnapshot = useCallback(() => {
    setLoading(true)
    setError(null)

    const unsub = onSnapshot(collection(db, 'users'), (snap) => {
      const list = snap.docs
        .map(d => ({ id: d.id, ...d.data() }))
        .filter(u => u.role === 'instructor')
      setInstructors(list)
      setLoading(false)
    }, (err) => {
      console.error('Error fetching instructors:', err)
      setError(err.message || 'Failed to load instructors.')
      setLoading(false)
    })

    return unsub
  }, [])

  useEffect(() => {
    // Ensure the admin's own Firestore record exists so security rules work.
    // Uses the self-create rule: allow create if isAuth() && uid() == userId
    async function ensureAdminRecord() {
      if (!auth.currentUser) return
      try {
        const adminRef = doc(db, 'users', auth.currentUser.uid)
        const snap = await getDoc(adminRef)
        if (!snap.exists()) {
          console.log('Admin Firestore record missing — creating it now...')
          await setDoc(adminRef, {
            uid: auth.currentUser.uid,
            email: auth.currentUser.email,
            role: 'superadmin',
            approvalStatus: 'approved',
            subscriptionStatus: 'active',
            createdAt: serverTimestamp()
          })
          console.log('Admin record created.')
        }
      } catch (err) {
        // Non-fatal — admin record might already be managed by Firebase Console
        console.warn('Could not ensure admin record:', err.message)
      }
    }

    ensureAdminRecord()
    const unsub = setupSnapshot()
    return () => unsub()
  }, [setupSnapshot])

  async function updateApproval(id, newStatus, tier = 'basic') {
    try {
      const updates = { approvalStatus: newStatus }
      if (newStatus === 'approved') {
        const normalized = normalizeInstructorTier(tier)
        updates.subscriptionStatus = 'active'
        updates.subscriptionTier = normalized
        if (normalized === 'institute') {
          const userSnap = await getDoc(doc(db, 'users', id))
          const instituteId = await ensureInstituteForUser(id, userSnap.data() || {})
          updates.instituteId = instituteId
          updates.instituteRole = 'owner'
        }
      }
      if (newStatus === 'suspended') {
        updates.subscriptionStatus = 'inactive'
      }
      await updateDoc(doc(db, 'users', id), updates)
      if (newStatus === 'approved') {
        const days = subscriptionDaysForPlan(tier)
        const end = new Date()
        end.setDate(end.getDate() + days)
        await setDoc(
          doc(db, 'subscriptions', id),
          {
            instructorId: id,
            plan: normalizeInstructorTier(tier),
            startDate: Timestamp.now(),
            endDate: Timestamp.fromDate(end),
            isActive: true,
          },
          { merge: true }
        )
      }
      await logAdminAction('instructor_approval', { userId: id, status: newStatus, tier })
    } catch (err) {
      alert('Failed to update approval: ' + err.message)
    }
  }

  async function ensureInstituteForUser(userId, userData) {
    let instituteId = userData.instituteId || ''
    if (instituteId) return instituteId
    const instituteRef = doc(collection(db, 'institutes'))
    instituteId = instituteRef.id
    const instituteName = `${userData.name || 'Academy'} Institute`
    await setDoc(instituteRef, {
      instituteId,
      name: instituteName,
      ownerUid: userId,
      ownerEmail: (userData.email || '').trim().toLowerCase(),
      createdAt: serverTimestamp(),
    })
    await setDoc(doc(db, 'institutes', instituteId, 'members', userId), {
      uid: userId,
      email: userData.email || '',
      name: userData.name || '',
      role: 'owner',
      status: 'active',
      addedAt: serverTimestamp(),
    })
    return instituteId
  }

  async function setInstructorTier(id, tier) {
    try {
      const normalized = normalizeInstructorTier(tier)
      const userSnap = await getDoc(doc(db, 'users', id))
      const userData = userSnap.data() || {}
      const updates = {
        subscriptionTier: normalized,
        subscriptionStatus: 'active',
        approvalStatus: 'approved',
      }
      if (normalized === 'institute') {
        const instituteId = await ensureInstituteForUser(id, userData)
        updates.instituteId = instituteId
        updates.instituteRole = 'owner'
      }
      await updateDoc(doc(db, 'users', id), updates)
      const days = subscriptionDaysForPlan(normalized)
      const end = new Date()
      end.setDate(end.getDate() + days)
      await setDoc(
        doc(db, 'subscriptions', id),
        {
          instructorId: id,
          plan: normalized,
          startDate: Timestamp.now(),
          endDate: Timestamp.fromDate(end),
          isActive: true,
        },
        { merge: true }
      )
      await logAdminAction('instructor_tier_set', { userId: id, tier: normalized })
    } catch (err) {
      alert('Failed to update plan: ' + err.message)
    }
  }

  async function removeInstructor(id) {
    if (!window.confirm('Are you sure you want to delete this instructor?')) return
    try {
      await deleteDoc(doc(db, 'users', id))
      await logAdminAction('instructor_delete', { userId: id })
    } catch (err) {
      alert('Failed to delete: ' + err.message)
    }
  }

  const filtered = instructors.filter(i => {
    if (searchTerm === '') return true
    const emailMatch = i.email?.toLowerCase().includes(searchTerm.toLowerCase()) ?? false
    const nameMatch  = i.name?.toLowerCase().includes(searchTerm.toLowerCase())  ?? false
    return emailMatch || nameMatch
  })

  if (loading) return <div className="loading-screen">Loading instructors...</div>

  if (error) return (
    <div className="page-wrapper">
      <div className="glass-panel" style={{ padding: '40px', textAlign: 'center' }}>
        <AlertCircle size={48} color="#ef4444" style={{ marginBottom: '16px' }} />
        <h3 style={{ color: '#ef4444', marginBottom: '8px' }}>Failed to load instructors</h3>
        <p style={{ color: '#94a3b8', marginBottom: '24px' }}>{error}</p>
        <button className="btn-icon approve" onClick={setupSnapshot} style={{ padding: '10px 24px', borderRadius: '8px', display: 'inline-flex', gap: '8px', alignItems: 'center' }}>
          <RefreshCw size={16} /> Retry
        </button>
      </div>
    </div>
  )

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <div className="header-flex">
          <div>
            <h2 className="page-heading gradient-text">Instructors</h2>
            <p className="page-subtitle">Verify and manage platform educators</p>
          </div>
          <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
            <div className="search-container glass-panel">
              <Search size={18} className="search-icon" />
              <input 
                type="text" 
                placeholder="Search by name or email..." 
                value={searchTerm}
                onChange={e => setSearchTerm(e.target.value)}
              />
            </div>
            <button
              title="Refresh list"
              onClick={setupSnapshot}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: '8px' }}
            >
              <RefreshCw size={18} />
            </button>
          </div>
        </div>
      </header>

      <div className="glass-panel table-card">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Instructor Profile</th>
              <th>Status</th>
              <th>Plan</th>
              <th>Member Since</th>
              <th style={{ textAlign: 'right' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length > 0 ? filtered.map(inst => (
              <tr key={inst.id}>
                <td>
                  <div className="instructor-cell">
                    <div className="instructor-avatar"><Users size={18} /></div>
                    <div>
                      <div className="instructor-name">{inst.name || 'Anonymous'}</div>
                      <div className="instructor-email">{inst.email}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <span className={`badge badge-${inst.approvalStatus === 'approved' ? 'success' : inst.approvalStatus === 'pending' ? 'warning' : 'error'}`}>
                    {inst.approvalStatus || 'pending'}
                  </span>
                </td>
                <td>
                  <select
                    value={normalizeInstructorTier(inst.subscriptionTier)}
                    onChange={e => setInstructorTier(inst.id, e.target.value)}
                    style={{ padding: '6px 8px', borderRadius: '6px', fontSize: '13px' }}
                    title="Subscription plan (Basic / Pro / Institute)"
                  >
                    <option value="basic">{INSTRUCTOR_TIER_LABELS.basic}</option>
                    <option value="pro">{INSTRUCTOR_TIER_LABELS.pro}</option>
                    <option value="institute">{INSTRUCTOR_TIER_LABELS.institute}</option>
                  </select>
                </td>
                <td>{inst.createdAt ? formatFirestoreDate(inst.createdAt).split(',')[0] : 'N/A'}</td>
                <td style={{ textAlign: 'right' }}>
                  <div className="action-row">
                    {inst.approvalStatus !== 'approved' ? (
                      <button
                        className="btn-icon approve"
                        onClick={() => {
                          const tier = window.prompt(
                            'Set plan: basic, pro, or institute',
                            normalizeInstructorTier(inst.subscriptionTier) || 'basic'
                          )
                          if (tier == null) return
                          updateApproval(inst.id, 'approved', tier)
                        }}
                        title="Approve & set plan"
                      >
                        <UserCheck size={18} />
                      </button>
                    ) : (
                      <button className="btn-icon suspend" onClick={() => updateApproval(inst.id, 'suspended')} title="Suspend">
                        <UserX size={18} />
                      </button>
                    )}
                    <button className="btn-icon delete" onClick={() => removeInstructor(inst.id)} title="Delete">
                      <Trash2 size={18} />
                    </button>
                  </div>
                </td>
              </tr>
            )) : (
              <tr>
                <td colSpan="5" className="empty-state">
                  {searchTerm ? `No instructors matching "${searchTerm}"` : 'No instructors registered yet.'}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

