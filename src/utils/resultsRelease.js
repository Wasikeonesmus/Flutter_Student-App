import { Timestamp } from 'firebase/firestore'
import { formatFirestoreDate } from './firestoreDate'

export function getReleaseTimeDate(test) {
  const t = test?.resultReleaseTime
  if (!t) return null
  if (typeof t.toDate === 'function') return t.toDate()
  if (t.seconds != null) return new Date(t.seconds * 1000)
  return null
}

/** @returns {'released' | 'locked'} */
export function getResultsReleaseStatus(test) {
  if (test?.resultsReleasedEarly === true) return 'released'
  const release = getReleaseTimeDate(test)
  if (!release) return 'released'
  return Date.now() >= release.getTime() ? 'released' : 'locked'
}

export function getResultsReleaseLabel(test) {
  if (test?.resultsReleasedEarly === true) return 'Released early (admin)'
  const release = getReleaseTimeDate(test)
  if (!release) return 'Immediate — no schedule'
  const released = Date.now() >= release.getTime()
  if (released) return `Released at ${formatFirestoreDate(test.resultReleaseTime)}`
  return `Locked until ${formatFirestoreDate(test.resultReleaseTime)}`
}

export function releaseResultsPatch() {
  return {
    resultsReleasedEarly: true,
    resultReleaseTime: Timestamp.now(),
  }
}
