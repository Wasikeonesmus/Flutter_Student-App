/** Summarize anti-cheat flags on a test document. */
export function antiCheatSummary(test) {
  if (!test) return '—'
  const flags = []
  if (test.antiCheatFullscreen !== false) flags.push('Fullscreen')
  if (test.antiCheatDetectLeaveApp !== false) flags.push('Leave-app')
  if (test.antiCheatBlockCopyPaste !== false) flags.push('No copy')
  if (test.antiCheatBlockScreenshot !== false) flags.push('No screenshot')
  if (test.antiCheatCamera) flags.push('Camera')
  if (test.antiCheatRandomizeQuestions !== false) flags.push('Shuffle Q')
  if (test.antiCheatRandomizeOptions !== false) flags.push('Shuffle opts')
  if (test.antiCheatAutoSubmit !== false) flags.push('Auto-submit')
  return flags.length ? flags.join(' · ') : 'Default'
}

export function antiCheatEnabledCount(test) {
  if (!test) return 0
  let n = 0
  if (test.antiCheatFullscreen !== false) n++
  if (test.antiCheatDetectLeaveApp !== false) n++
  if (test.antiCheatBlockCopyPaste !== false) n++
  if (test.antiCheatBlockScreenshot !== false) n++
  if (test.antiCheatCamera) n++
  if (test.antiCheatRandomizeQuestions !== false) n++
  if (test.antiCheatRandomizeOptions !== false) n++
  if (test.antiCheatAutoSubmit !== false) n++
  return n
}
