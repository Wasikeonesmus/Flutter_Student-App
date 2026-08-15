import { useEffect, useState } from 'react'
import { doc, getDoc, setDoc, collection, addDoc } from 'firebase/firestore'
import { 
  Settings, 
  CreditCard, 
  Palette, 
  Save, 
  RefreshCcw,
  Globe,
  Tag,
  Building,
  Activity,
  Mail
} from 'lucide-react'
import { db } from '../firebase'
import { invalidatePricingCache } from '../utils/planPricing'
import { logAdminAction } from '../utils/auditLog'
import '../styles/PlatformSettings.css'

const DEFAULT_PLANS = [
  { key: 'weekly',    label: 'Weekly',     price: 5   },
  { key: 'monthly',   label: 'Monthly',    price: 10  },
  { key: 'sixmonths', label: 'Six Months', price: 50  },
  { key: 'yearly',    label: 'Yearly',     price: 100 },
]

const DEFAULT_SUBSCRIPTION_TIERS = [
  {
    key: 'basic',
    label: 'Basic',
    price: 10,
    contactOnly: false,
    subtitle: '',
    features: ['Limited tests/month', 'Basic analytics'],
  },
  {
    key: 'pro',
    label: 'Pro',
    price: 29,
    contactOnly: false,
    subtitle: '',
    features: [
      'Unlimited tests',
      'Advanced analytics',
      'Branding/logo upload',
      'Pass certificates (PDF)',
      'Student reports',
    ],
  },
  {
    key: 'institute',
    label: 'Institute Plan',
    price: 0,
    contactOnly: false,
    subtitle: 'For academies/schools',
    features: [
      'All Pro features',
      'Multiple instructors',
      'Batch management',
      'Attendance tracking',
      'Institute dashboard',
    ],
  },
]

const DEFAULT_ACCOUNTS = [
  { method: 'JazzCash',  number: '0301-2345678', type: 'Mobile Wallet', name: 'Students Welfare Foundation' },
  { method: 'Easypaisa', number: '0311-9876543', type: 'Mobile Wallet', name: 'Students Welfare Foundation' },
  { method: 'Binance',   number: 'SWF2024',      type: 'Pay ID',        name: 'Students Welfare Foundation' },
]

export default function PlatformSettings() {
  const [plans, setPlans] = useState(DEFAULT_PLANS)
  const [subscriptionTiers, setSubscriptionTiers] = useState(DEFAULT_SUBSCRIPTION_TIERS)
  const [accounts, setAccounts] = useState(DEFAULT_ACCOUNTS)
  const [branding, setBranding] = useState({ platformName: 'Exam System', orgName: 'Students Welfare Foundation', tagline: 'Professional MCQ Examination Platform' })
  const [usdPkr, setUsdPkr] = useState(278.5)
  const [studentResultPriceUsd, setStudentResultPriceUsd] = useState(5)
  const [examPortalEnabled, setExamPortalEnabled] = useState(true)
  const [examPortalStartHour, setExamPortalStartHour] = useState(8)
  const [examPortalEndHour, setExamPortalEndHour] = useState(20)
  const [openRouterApiKeyInput, setOpenRouterApiKeyInput] = useState('')
  const [aiConfigured, setAiConfigured] = useState(false)
  const [emailSettings, setEmailSettings] = useState({ smtpHost: '', smtpPort: '587', smtpUser: '', smtpPass: '', senderName: 'SWF Exam System', senderEmail: '' })
  const [loading, setLoading] = useState(true)
  const [saved, setSaved] = useState(false)
  const [testEmailTo, setTestEmailTo] = useState('')
  const [emailTestStatus, setEmailTestStatus] = useState('')

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const docSnap = await getDoc(doc(db, 'platform_settings', 'global'))
        const data = docSnap.exists() ? docSnap.data() : {}
        if (docSnap.exists()) {
          if (data.plans) setPlans(data.plans)
          if (data.subscriptionTiers) setSubscriptionTiers(data.subscriptionTiers)
          if (data.accounts) setAccounts(data.accounts)
          if (data.branding) setBranding(data.branding)
          if (data.usdPkr) setUsdPkr(data.usdPkr)
          if (data.studentResultPriceUsd != null) setStudentResultPriceUsd(data.studentResultPriceUsd)
          if (data.examPortalEnabled != null) setExamPortalEnabled(data.examPortalEnabled)
          if (data.examPortalStartHour != null) setExamPortalStartHour(data.examPortalStartHour)
          if (data.examPortalEndHour != null) setExamPortalEndHour(data.examPortalEndHour)
          if (data.emailSettings) setEmailSettings(data.emailSettings)
          setAiConfigured(!!data.openRouterApiKey || !!data.hasSecretsAiKey)
        }
      } catch (err) {
        console.error("Settings Load Error:", err)
      } finally {
        setLoading(false)
      }
    }
    fetchSettings()
  }, [])

  async function save() {
    try {
      const keyTrim = openRouterApiKeyInput.trim()
      // FIX #2: NEVER write the raw API key to platform_settings (allow read: if true).
      // Only the safe boolean indicator is stored there; the real key goes to
      // platform_secrets/ai which has `allow read: if false`.
      await setDoc(doc(db, 'platform_settings', 'global'), {
        plans,
        subscriptionTiers,
        accounts,
        branding,
        usdPkr,
        studentResultPriceUsd,
        examPortalEnabled,
        examPortalStartHour,
        examPortalEndHour,
        emailSettings,
        ...(keyTrim ? { hasSecretsAiKey: true } : {}),
        updatedAt: new Date()
      }, { merge: true })
      if (keyTrim) {
        await setDoc(doc(db, 'platform_secrets', 'ai'), {
          openRouterApiKey: keyTrim,
          updatedAt: new Date(),
        })
        setAiConfigured(true)
        setOpenRouterApiKeyInput('')
      }
      invalidatePricingCache()
      await logAdminAction('settings_save', { scope: 'platform_settings' })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    } catch (err) {
      alert("Save failed: " + err.message)
    }
  }

  async function sendTestEmail() {
    const to = (testEmailTo || emailSettings.senderEmail || emailSettings.smtpUser || '').trim()
    if (!to) {
      alert('Enter a recipient email or configure sender email first.')
      return
    }
    if (!emailSettings.smtpHost || !emailSettings.smtpUser) {
      alert('Save SMTP host and user first. Emails are sent via Firebase Trigger Email extension (mail collection) or Cloud Functions.')
    }
    setEmailTestStatus('Sending…')
    try {
      await addDoc(collection(db, 'mail'), {
        to,
        message: {
          subject: 'ExamPro — test email',
          text: 'This is a test message from the super-admin panel. SMTP / Trigger Email is configured correctly.',
          html: '<p>This is a <strong>test</strong> from ExamPro admin settings.</p>',
        },
      })
      await logAdminAction('email_test', { to })
      setEmailTestStatus(`Queued test email to ${to}`)
      setTimeout(() => setEmailTestStatus(''), 5000)
    } catch (err) {
      setEmailTestStatus('Failed: ' + err.message)
    }
  }

  if (loading) return <div className="loading-screen">Configuring environment...</div>

  return (
    <div className="page-wrapper">
      <header className="page-header">
        <h2 className="page-heading gradient-text">System Settings</h2>
        <p className="page-subtitle">Configure pricing, payment methods, and branding</p>
      </header>

      {saved && <div className="save-toast">✅ Configuration updated successfully</div>}

      <div className="settings-grid">
        {/* Instructor tiers (Basic / Pro / Institute) */}
        <section className="glass-panel settings-card full-width">
          <div className="card-head">
            <Tag size={20} className="icon-p" />
            <h3>Instructor Subscription Plans</h3>
          </div>
          <p className="tier-section-hint">Shown to instructors when they activate their account (features + monthly USD price).</p>
          <div className="tiers-admin-grid">
            {subscriptionTiers.map((tier, ti) => (
              <div key={tier.key} className="tier-admin-card">
                <div className="tier-admin-head">
                  <input
                    className="tier-label-input"
                    value={tier.label}
                    onChange={e => setSubscriptionTiers(prev => prev.map((t, i) => i === ti ? { ...t, label: e.target.value } : t))}
                  />
                  <label className="tier-contact-check">
                    <input
                      type="checkbox"
                      checked={!!tier.contactOnly}
                      onChange={e => setSubscriptionTiers(prev => prev.map((t, i) => i === ti ? { ...t, contactOnly: e.target.checked } : t))}
                    />
                    Contact / custom pricing
                  </label>
                </div>
                <input
                  className="tier-subtitle-input"
                  placeholder="Subtitle (e.g. For academies)"
                  value={tier.subtitle || ''}
                  onChange={e => setSubscriptionTiers(prev => prev.map((t, i) => i === ti ? { ...t, subtitle: e.target.value } : t))}
                />
                {!tier.contactOnly && (
                  <div className="p-input-wrap tier-price-row">
                    <span>$</span>
                    <input
                      type="number"
                      value={tier.price}
                      onChange={e => setSubscriptionTiers(prev => prev.map((t, i) => i === ti ? { ...t, price: +e.target.value } : t))}
                    />
                    <span className="p-equiv">/ month · ≈ PKR {(tier.price * usdPkr).toLocaleString()}</span>
                  </div>
                )}
                <label className="features-label">Features (one per line)</label>
                <textarea
                  className="tier-features-input"
                  rows={5}
                  value={(tier.features || []).join('\n')}
                  onChange={e => setSubscriptionTiers(prev => prev.map((t, i) =>
                    i === ti ? { ...t, features: e.target.value.split('\n').map(s => s.trim()).filter(Boolean) } : t
                  ))}
                />
              </div>
            ))}
          </div>
        </section>

        {/* Student result unlock + exam hours */}
        <section className="glass-panel settings-card">
          <div className="card-head">
            <Activity size={20} className="icon-p" />
            <h3>Student exams &amp; results</h3>
          </div>
          <div className="rate-box">
            <label>Student full-results fee (USD)</label>
            <input
              type="number"
              min={0}
              step={0.5}
              value={studentResultPriceUsd}
              onChange={e => setStudentResultPriceUsd(+e.target.value)}
            />
            <span className="p-equiv">
              ≈ PKR {Math.round(studentResultPriceUsd * usdPkr).toLocaleString()} (shown in Android app)
            </span>
          </div>
          <label className="tier-contact-check" style={{ display: 'block', marginTop: 12 }}>
            <input
              type="checkbox"
              checked={examPortalEnabled}
              onChange={e => setExamPortalEnabled(e.target.checked)}
            />
            Restrict student exams to daily hours (Pakistan time)
          </label>
          {examPortalEnabled && (
            <div className="rate-box" style={{ marginTop: 8 }}>
              <label>Open hour (0–23)</label>
              <input type="number" min={0} max={23} value={examPortalStartHour}
                onChange={e => setExamPortalStartHour(+e.target.value)} />
              <label>Close hour (1–24, exclusive)</label>
              <input type="number" min={1} max={24} value={examPortalEndHour}
                onChange={e => setExamPortalEndHour(+e.target.value)} />
              <p className="muted" style={{ fontSize: '0.8rem' }}>
                Default 8–20 = 8:00 AM until 8:00 PM PKT. Cloud Functions use the same settings when deployed.
              </p>
            </div>
          )}
        </section>

        {/* AI (OpenRouter) — key stored server-side only */}
        <section className="glass-panel settings-card">
          <div className="card-head">
            <Globe size={20} className="icon-s" />
            <h3>AI question formatting (OpenRouter)</h3>
          </div>
          <p className="muted" style={{ fontSize: '0.85rem', marginBottom: 8 }}>
            <strong>Spark / free Firebase:</strong> key is saved on <code>platform_settings/global</code> so the Android app can format MCQs (no Blaze or Cloud Functions).
            Optional copy also goes to <code>platform_secrets/ai</code> if you upgrade later.
          </p>
          {aiConfigured && (
            <p className="muted" style={{ color: '#16a34a', marginBottom: 8 }}>✓ OpenRouter key is configured</p>
          )}
          <div className="field-group">
            <label>{aiConfigured ? 'Replace API key (optional)' : 'OpenRouter API key'}</label>
            <input
              type="password"
              value={openRouterApiKeyInput}
              placeholder={aiConfigured ? 'Leave blank to keep current key' : 'sk-or-v1-...'}
              onChange={e => setOpenRouterApiKeyInput(e.target.value)}
            />
          </div>
          <p className="muted" style={{ fontSize: '0.8rem' }}>
            If this key was ever in the Android APK, rotate it at openrouter.ai after saving a new key here.
          </p>
        </section>

        {/* Legacy billing periods (optional add-ons) */}
        <section className="glass-panel settings-card">
          <div className="card-head">
            <Tag size={20} className="icon-p" />
            <h3>Billing Period Pricing (legacy)</h3>
          </div>
          <div className="rate-box">
            <RefreshCcw size={14} />
            <label>USD to PKR Conversion Rate</label>
            <input type="number" value={usdPkr} onChange={e => setUsdPkr(+e.target.value)} />
          </div>
          <div className="plans-list">
            {plans.map((plan, i) => (
              <div key={plan.key} className="plan-item">
                <span className="p-label">{plan.label}</span>
                <div className="p-input-wrap">
                  <span>$</span>
                  <input type="number" value={plan.price}
                    onChange={e => setPlans(prev => prev.map((p, idx) => idx === i ? { ...p, price: +e.target.value } : p))} />
                </div>
                <span className="p-equiv">≈ PKR {(plan.price * usdPkr).toLocaleString()}</span>
              </div>
            ))}
          </div>
        </section>

        {/* Branding */}
        <section className="glass-panel settings-card">
          <div className="card-head">
            <Palette size={20} className="icon-s" />
            <h3>Branding & Identity</h3>
          </div>
          <div className="branding-form">
            <div className="field-group">
              <label><Globe size={14} /> Platform Name</label>
              <input value={branding.platformName} onChange={e => setBranding({...branding, platformName: e.target.value})} />
            </div>
            <div className="field-group">
              <label><Building size={14} /> Organization</label>
              <input value={branding.orgName} onChange={e => setBranding({...branding, orgName: e.target.value})} />
            </div>
            <div className="field-group">
              <label><Activity size={14} /> Tagline</label>
              <textarea value={branding.tagline} onChange={e => setBranding({...branding, tagline: e.target.value})} />
            </div>
          </div>
        </section>

        {/* Email settings */}
        <section className="glass-panel settings-card">
          <div className="card-head">
            <Mail size={20} className="icon-p" style={{ stroke: '#f43f5e' }} />
            <h3>SMTP Email Configuration</h3>
          </div>
          <div className="branding-form">
            <div className="field-group">
              <label>SMTP Host</label>
              <input value={emailSettings.smtpHost} placeholder="e.g. smtp.gmail.com" onChange={e => setEmailSettings({...emailSettings, smtpHost: e.target.value})} />
            </div>
            <div className="field-group">
              <label>SMTP Port</label>
              <input value={emailSettings.smtpPort} placeholder="e.g. 587 or 465" onChange={e => setEmailSettings({...emailSettings, smtpPort: e.target.value})} />
            </div>
            <div className="field-group">
              <label>SMTP User (Email)</label>
              <input value={emailSettings.smtpUser} placeholder="e.g. yourname@domain.com" onChange={e => setEmailSettings({...emailSettings, smtpUser: e.target.value})} />
            </div>
            <div className="field-group">
              <label>SMTP Password / App Password</label>
              <input type="password" value={emailSettings.smtpPass} placeholder="Enter password" onChange={e => setEmailSettings({...emailSettings, smtpPass: e.target.value})} />
            </div>
            <div className="field-group">
              <label>Sender Name</label>
              <input value={emailSettings.senderName} placeholder="e.g. SWF Exam System" onChange={e => setEmailSettings({...emailSettings, senderName: e.target.value})} />
            </div>
            <div className="field-group">
              <label>Sender Email Address</label>
              <input value={emailSettings.senderEmail} placeholder="e.g. no-reply@domain.com" onChange={e => setEmailSettings({...emailSettings, senderEmail: e.target.value})} />
            </div>

            <div className="field-group" style={{ marginTop: 16 }}>
              <label>Send test email to</label>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <input
                  value={testEmailTo}
                  onChange={(e) => setTestEmailTo(e.target.value)}
                  placeholder={emailSettings.senderEmail || 'your@email.com'}
                  style={{ flex: 1, minWidth: 200 }}
                />
                <button type="button" className="premium-button" onClick={sendTestEmail}>
                  Send test
                </button>
              </div>
              {emailTestStatus && <p className="muted" style={{ marginTop: 8, fontSize: '0.85rem' }}>{emailTestStatus}</p>}
              <p className="muted" style={{ marginTop: 8, fontSize: '0.8rem' }}>
                Requires Firebase &quot;Trigger Email&quot; extension on the <code>mail</code> collection, or deployed Cloud Functions with SMTP.
              </p>
            </div>
          </div>
        </section>

        {/* Accounts */}
        <section className="glass-panel settings-card full-width">
          <div className="card-head">
            <CreditCard size={20} className="icon-a" />
            <h3>Receiving Accounts</h3>
          </div>
          <div className="accounts-grid">
            {accounts.map((acc, i) => (
              <div key={acc.method} className="acc-config-card">
                <h4 className="acc-method">{acc.method}</h4>
                <div className="acc-fields">
                  <input value={acc.number} placeholder="Account Number"
                    onChange={e => setAccounts(prev => prev.map((a, idx) => idx === i ? { ...a, number: e.target.value } : a))} />
                  <input value={acc.type} placeholder="Type (e.g. Wallet)"
                    onChange={e => setAccounts(prev => prev.map((a, idx) => idx === i ? { ...a, type: e.target.value } : a))} />
                  <input value={acc.name} placeholder="Account Holder Name"
                    onChange={e => setAccounts(prev => prev.map((a, idx) => idx === i ? { ...a, name: e.target.value } : a))} />
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="sticky-footer">
        <button className="premium-button save-all-btn" onClick={save}>
          <Save size={20} />
          <span>Save Changes</span>
        </button>
      </div>
    </div>
  )
}
