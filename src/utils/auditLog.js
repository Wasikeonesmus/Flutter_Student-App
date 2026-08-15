import { collection, addDoc, serverTimestamp } from 'firebase/firestore'
import { auth, db } from '../firebase'

/** Append a row to admin_audit (super-admin actions). */
export async function logAdminAction(action, details = {}) {
  try {
    const user = auth.currentUser
    await addDoc(collection(db, 'admin_audit'), {
      action,
      details,
      adminEmail: user?.email || 'unknown',
      adminUid: user?.uid || '',
      createdAt: serverTimestamp(),
    })
  } catch (err) {
    console.warn('Audit log failed:', err.message)
  }
}
