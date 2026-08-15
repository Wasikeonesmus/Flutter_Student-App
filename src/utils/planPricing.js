import { doc, getDoc } from 'firebase/firestore'
import { normalizeInstructorTier, INSTRUCTOR_TIER_LABELS } from './normalizeInstructorTier'

/** Legacy billing-period keys (USD). */
export const LEGACY_PLAN_PRICES = {
  weekly: 9,
  monthly: 29,
  sixmonths: 149,
  yearly: 279,
  student_result: 5,
}

export const DEFAULT_TIER_PRICES = {
  basic: 10,
  pro: 29,
  institute: 0,
}

const DEFAULT_SUBSCRIPTION_TIERS = [
  { key: 'basic', label: 'Basic', price: 10 },
  { key: 'pro', label: 'Pro', price: 29 },
  { key: 'institute', label: 'Institute Plan', price: 0 },
]

/** Days granted per plan key when approving / renewing. */
export const PLAN_DURATION_DAYS = {
  weekly: 7,
  monthly: 30,
  sixmonths: 180,
  yearly: 365,
  basic: 30,
  pro: 30,
  institute: 365,
}

let cachedSettings = null

export async function loadPlatformPricing(db) {
  if (cachedSettings) return cachedSettings
  try {
    const snap = await getDoc(doc(db, 'platform_settings', 'global'))
    const data = snap.exists() ? snap.data() : {}
    const tiers = data.subscriptionTiers?.length ? data.subscriptionTiers : DEFAULT_SUBSCRIPTION_TIERS
    const tierPrices = {}
    tiers.forEach((t) => {
      tierPrices[t.key] = typeof t.price === 'number' ? t.price : DEFAULT_TIER_PRICES[t.key] ?? 0
    })
    cachedSettings = {
      tiers,
      tierPrices,
      legacyPlans: data.plans || [],
      usdPkr: typeof data.usdPkr === 'number' ? data.usdPkr : 278.5,
      studentResultPriceUsd:
        typeof data.studentResultPriceUsd === 'number'
          ? data.studentResultPriceUsd
          : LEGACY_PLAN_PRICES.student_result,
    }
    return cachedSettings
  } catch {
    cachedSettings = {
      tiers: DEFAULT_SUBSCRIPTION_TIERS,
      tierPrices: { ...DEFAULT_TIER_PRICES },
      legacyPlans: [],
      usdPkr: 278.5,
      studentResultPriceUsd: LEGACY_PLAN_PRICES.student_result,
    }
    return cachedSettings
  }
}

export function invalidatePricingCache() {
  cachedSettings = null
}

/** USD amount for a payment row or plan string. */
export function paymentAmountUsd(plan, settings) {
  if (!plan) return 0
  const key = String(plan).toLowerCase().trim()
  if (key === 'student_result') {
    return settings?.studentResultPriceUsd ?? LEGACY_PLAN_PRICES.student_result
  }
  if (LEGACY_PLAN_PRICES[key] != null) return LEGACY_PLAN_PRICES[key]
  const tierKey = normalizeInstructorTier(plan)
  if (settings?.tierPrices?.[tierKey] != null) return settings.tierPrices[tierKey]
  return DEFAULT_TIER_PRICES[tierKey] ?? 0
}

export function formatPlanDisplay(plan) {
  if (!plan) return '—'
  const key = String(plan).toLowerCase().trim()
  if (key === 'student_result') return 'Student result unlock'
  if (LEGACY_PLAN_PRICES[key] != null) {
    return key.charAt(0).toUpperCase() + key.slice(1)
  }
  const tier = normalizeInstructorTier(plan)
  return INSTRUCTOR_TIER_LABELS[tier] || plan
}

export function studentResultPricePkr(settings) {
  const usd = settings?.studentResultPriceUsd ?? LEGACY_PLAN_PRICES.student_result
  const rate = settings?.usdPkr ?? 278.5
  return Math.round(usd * rate)
}

export function subscriptionDaysForPlan(plan) {
  const key = String(plan || 'monthly').toLowerCase().trim()
  const tier = normalizeInstructorTier(plan)
  return PLAN_DURATION_DAYS[key] ?? PLAN_DURATION_DAYS[tier] ?? 30
}
