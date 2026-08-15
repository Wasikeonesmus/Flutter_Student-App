import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { onAuthStateChanged, signOut, signInWithCustomToken } from 'firebase/auth'
import { doc, getDoc, setDoc } from 'firebase/firestore'
import { auth, db } from './firebase'
import AdminLogin from './pages/AdminLogin'
import Dashboard from './pages/Dashboard'
import InstructorManagement from './pages/InstructorManagement'
import PaymentManagement from './pages/PaymentManagement'
import Analytics from './pages/Analytics'
import PlatformSettings from './pages/PlatformSettings'
import ExamResultsRelease from './pages/ExamResultsRelease'
import InstituteManagement from './pages/InstituteManagement'
import ExamManagement from './pages/ExamManagement'
import AttemptsManagement from './pages/AttemptsManagement'
import SubscriptionManagement from './pages/SubscriptionManagement'
import AuditLog from './pages/AuditLog'
import TestResults from './pages/TestResults'
import AdminLayout from './components/AdminLayout'
import './App.css'
import StudentResult from './pages/StudentResult'
import TestCreatorPage from './pages/TestCreatorPage'

function App() {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const token = params.get('token')
    if (token) {
      setLoading(true)
      signInWithCustomToken(auth, token)
        .then(() => {
          const url = new URL(window.location.href)
          url.searchParams.delete('token')
          window.history.replaceState({}, document.title, url.pathname + url.search)
        })
        .catch((err) => {
          console.error("SSO sign-in failed:", err)
          setLoading(false)
        })
    }
  }, [])

  async function autoDisablePortalRestriction() {
    try {
      await setDoc(doc(db, 'platform_settings', 'global'), {
        examPortalEnabled: false,
        examPortalStartHour: 0,
        examPortalEndHour: 24
      }, { merge: true })
      console.log("SUCCESS: Exam portal restriction auto-disabled in Firestore!")
    } catch (e) {
      console.warn("Failed to auto-disable exam portal restriction:", e.message)
    }
  }

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (u) => {
      if (u) {
        try {
          const userDoc = await getDoc(doc(db, 'users', u.uid))
          const superadminEmail = import.meta.env.VITE_SUPERADMIN_EMAIL || 'admin@yourplatform.com'
          if (!userDoc.exists()) {
            if (u.email?.toLowerCase() !== superadminEmail.toLowerCase()) {
              await signOut(auth)
              setUser(null)
              return
            }
            const newDoc = {
              uid: u.uid,
              email: u.email,
              role: 'superadmin',
              name: 'Super Admin',
              approvalStatus: 'approved',
              subscriptionStatus: 'active'
            }
            await setDoc(doc(db, 'users', u.uid), newDoc)
            setUser(newDoc)
            autoDisablePortalRestriction()
          } else {
            const userData = userDoc.data()
            if (userData?.role === 'superadmin' || userData?.role === 'instructor') {
              setUser({ uid: u.uid, email: u.email, ...userData })
              if (userData?.role === 'superadmin') {
                autoDisablePortalRestriction()
              }
            } else {
              await signOut(auth)
              setUser(null)
            }
          }
        } catch (err) {
          console.error("Auth state check failed:", err)
          await signOut(auth)
          setUser(null)
        }
      } else {
        setUser(null)
      }
      setLoading(false)
    })
    return () => unsubscribe()
  }, [])

  const handleLogout = async () => {
    await signOut(auth)
  }

  if (loading) return <div className="loading-screen">Authenticating Account...</div>

  const isRestrictedInstructor = user && user.role === 'instructor' && (user.approvalStatus !== 'approved' || user.subscriptionStatus !== 'active')
  const isInstructor = user?.role === 'instructor'

  if (isRestrictedInstructor) {
    return (
      <div className="login-page">
        <div className="login-glow-1" />
        <div className="login-glow-2" />
        <div className="login-card glass-panel" style={{ maxWidth: 500, textAlign: 'center', padding: '40px 30px' }}>
          <div className="login-header">
            <div className="login-logo" style={{ background: '#f43f5e', color: '#fff', fontSize: '2rem', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>!</div>
            <h2 className="gradient-text" style={{ fontSize: '1.75rem', marginTop: 15 }}>Access Restricted</h2>
            <p style={{ fontSize: '0.9rem', color: '#a3a3a3', marginTop: 5 }}>Account: {user.email}</p>
          </div>
          
          <div style={{ margin: '24px 0', fontSize: '0.95rem', color: '#e5e5e5', lineHeight: 1.6 }}>
            {user.approvalStatus === 'suspended' ? (
              <p>Your instructor account has been <strong>suspended</strong> by the administrator. Please contact the support team for more details.</p>
            ) : user.approvalStatus === 'pending' ? (
              <p>Your instructor account is currently <strong>pending approval</strong>. The administrator will review your account soon. Please check back later.</p>
            ) : (
              <div>
                <p style={{ marginBottom: 12 }}>Your subscription status is <strong>inactive</strong>.</p>
                <p style={{ fontSize: '0.875rem', color: '#a3a3a3' }}>To manage exams, please sign in to the <strong>ExamPro Android app</strong> on your mobile device, purchase a subscription, and submit your payment screenshot.</p>
              </div>
            )}
          </div>

          <button onClick={handleLogout} className="premium-button login-btn" style={{ marginTop: 10, width: '100%' }}>
            Sign Out
          </button>
        </div>
      </div>
    )
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={user ? <Navigate to="/" replace /> : <AdminLogin onLogin={() => {}} />}
        />
        <Route path="/student/:attemptId" element={<StudentResult />} />
        {user ? (
          <Route path="/" element={<AdminLayout user={user} onLogout={handleLogout} />}>
            <Route index element={<Dashboard user={user} />} />
            <Route path="instructors" element={isInstructor ? <Navigate to="/" replace /> : <InstructorManagement />} />
            <Route path="exams" element={<ExamManagement user={user} />} />
            <Route path="attempts" element={<AttemptsManagement user={user} />} />
            <Route path="institutes" element={isInstructor ? <Navigate to="/" replace /> : <InstituteManagement />} />
            <Route path="payments" element={isInstructor ? <Navigate to="/" replace /> : <PaymentManagement />} />
            <Route path="subscriptions" element={isInstructor ? <Navigate to="/" replace /> : <SubscriptionManagement />} />
            <Route path="results-release" element={<ExamResultsRelease user={user} />} />
            <Route path="analytics" element={isInstructor ? <Navigate to="/" replace /> : <Analytics />} />
            <Route path="audit" element={isInstructor ? <Navigate to="/" replace /> : <AuditLog />} />
            <Route path="test-results" element={<TestResults user={user} />} />
            <Route path="settings" element={isInstructor ? <Navigate to="/" replace /> : <PlatformSettings />} />
            <Route path="create-test" element={<TestCreatorPage user={user} />} />
            <Route path="edit-test/:docId" element={<TestCreatorPage user={user} />} />
          </Route>
        ) : (
          <Route path="*" element={<Navigate to="/login" replace />} />
        )}
      </Routes>
    </BrowserRouter>
  )
}

export default App
