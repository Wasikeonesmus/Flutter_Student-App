/** Map payment.plan or subscriptionTier strings → basic | pro | institute */
export function normalizeInstructorTier(plan) {
  const p = String(plan || 'basic').toLowerCase().trim()
  if (!p) return 'basic'
  if (p.includes('institute')) return 'institute'
  if (p === 'pro' || p.startsWith('pro ')) return 'pro'
  if (p === 'basic') return 'basic'
  return 'basic'
}

export const INSTRUCTOR_TIER_LABELS = {
  basic: 'Basic',
  pro: 'Pro',
  institute: 'Institute Plan',
}
