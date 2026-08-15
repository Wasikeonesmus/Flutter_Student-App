import { useState } from 'react'
import { signInWithEmailAndPassword, createUserWithEmailAndPassword, sendPasswordResetEmail } from 'firebase/auth'
import { doc, getDoc, setDoc } from 'firebase/firestore'
import { auth, db } from '../firebase'
import '../styles/AdminLogin.css'

export default function AdminLogin({ onLogin }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [resetMessage, setResetMessage] = useState('')
  const [isResettingPassword, setIsResettingPassword] = useState(false)
  const [loading, setLoading] = useState(false)

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const cred = await signInWithEmailAndPassword(auth, email, password)
      const userDoc = await getDoc(doc(db, 'users', cred.user.uid))
      let userData = userDoc.data()
      
      const superadminEmail = import.meta.env.VITE_SUPERADMIN_EMAIL || 'admin@yourplatform.com'
      if (!userDoc.exists()) {
        if (cred.user.email?.toLowerCase() !== superadminEmail.toLowerCase()) {
          await auth.signOut()
          setError('Access denied. Super Admin email mismatch.')
          return
        }
        const newDoc = {
          uid: cred.user.uid,
          email: cred.user.email,
          role: 'superadmin',
          name: 'Super Admin',
          approvalStatus: 'approved'
        }
        await setDoc(doc(db, 'users', cred.user.uid), newDoc)
        userData = newDoc
      }

      if (userData?.role !== 'superadmin' && userData?.role !== 'instructor') {
        await auth.signOut()
        setError('Access denied. Authorized roles only.')
        return
      }
      onLogin()
    } catch (err) {
      setError(err.message || 'Login failed.')
    } finally {
      setLoading(false)
    }
  }

  async function handleRegister(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const superadminEmail = import.meta.env.VITE_SUPERADMIN_EMAIL || 'admin@yourplatform.com'
      if (email.trim().toLowerCase() !== superadminEmail.toLowerCase()) {
        setError('Registration denied. Only the designated Super Admin email can register.')
        return
      }
      const cred = await createUserWithEmailAndPassword(auth, email, password)
      await setDoc(doc(db, 'users', cred.user.uid), {
        uid: cred.user.uid,
        email: cred.user.email,
        role: 'superadmin',
        name: 'Super Admin',
        approvalStatus: 'approved'
      })
      onLogin()
    } catch (err) {
      setError(err.message || 'Registration failed.')
    } finally {
      setLoading(false)
    }
  }

  async function handleResetPassword(e) {
    e.preventDefault()
    setError('')
    setResetMessage('')
    if (!email) {
      setError('Please enter your email address first.')
      return
    }
    setLoading(true)
    try {
      await sendPasswordResetEmail(auth, email.trim())
      setResetMessage('A password reset link has been sent to your email address.')
    } catch (err) {
      setError(err.message || 'Failed to send password reset email.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-glow-1" />
      <div className="login-glow-2" />
      <div className="login-card glass-panel">
        <div className="login-header">
          <div className="login-logo">E</div>
          <h2 className="gradient-text">ExamPro Admin</h2>
          <p>Students Welfare Foundation</p>
        </div>
        <form className="login-form">
          {error && <div className="error-banner">{error}</div>}
          {resetMessage && (
            <div className="success-banner" style={{ background: 'rgba(34, 197, 94, 0.1)', border: '1px solid #22c55e', color: '#22c55e', padding: '10px', borderRadius: '6px', fontSize: '0.875rem', marginBottom: '15px', textAlign: 'center' }}>
              {resetMessage}
            </div>
          )}
          <div className="form-group">
            <label>Email Address</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
              placeholder="admin@examplatform.com" required />
          </div>
          {!isResettingPassword && (
            <div className="form-group">
              <label>Password</label>
              <input type="password" value={password} onChange={e => setPassword(e.target.value)}
                placeholder="••••••••" required />
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px', flexDirection: 'column' }}>
            {isResettingPassword ? (
              <>
                <button type="button" onClick={handleResetPassword} className="premium-button login-btn" disabled={loading}>
                  {loading ? 'Processing…' : 'Show Reset Link'}
                </button>
                <button type="button" onClick={() => { setIsResettingPassword(false); setError(''); setResetMessage(''); }} className="premium-button login-btn" style={{ background: 'rgba(255,255,255,0.05)', color: '#fff', border: '1px solid rgba(255,255,255,0.1)' }}>
                  Back to Sign In
                </button>
              </>
            ) : (
              <>
                <button type="button" onClick={handleLogin} className="premium-button login-btn" disabled={loading}>
                  {loading ? 'Signing In…' : 'Sign In to Dashboard'}
                </button>
                <button type="button" onClick={handleRegister} className="premium-button login-btn" style={{ background: 'linear-gradient(135deg, #22c55e, #16a34a)' }} disabled={loading}>
                  First Time Setup: Register
                </button>
                <div style={{ textAlign: 'center', marginTop: '10px' }}>
                  <span onClick={() => { setIsResettingPassword(true); setError(''); setResetMessage(''); }} style={{ color: '#f43f5e', cursor: 'pointer', fontWeight: 500, fontSize: '0.85rem' }}>
                    Forgot Password?
                  </span>
                </div>
              </>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}
