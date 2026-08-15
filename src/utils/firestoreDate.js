/** Safe Firestore Timestamp → locale string (web admin). */
export function formatFirestoreDate(value) {
  if (!value) return '—'
  if (typeof value.toDate === 'function') return value.toDate().toLocaleString()
  if (typeof value.seconds === 'number') return new Date(value.seconds * 1000).toLocaleString()
  if (typeof value === 'string' || typeof value === 'number') {
    const d = new Date(value)
    return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString()
  }
  return '—'
}
