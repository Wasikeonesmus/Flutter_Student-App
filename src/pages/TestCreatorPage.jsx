import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  collection, doc, getDocs, addDoc, updateDoc, setDoc, getDoc,
  serverTimestamp, Timestamp
} from 'firebase/firestore'
import {
  BookOpen, Plus, Trash2, ChevronDown, ChevronUp,
  Zap, Upload, CheckCircle, Copy, Settings2,
  Clock, Award, Eye, EyeOff, AlertCircle, Shield,
  GripVertical, FileText, Users
} from 'lucide-react'
import { db } from '../firebase'
import { logAdminAction } from '../utils/auditLog'
import '../styles/TestCreator.css'

// ─── Helpers ──────────────────────────────────────────────────────────────────
function genId() {
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

function genUniqueTestId() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  let id = ''
  for (let i = 0; i < 8; i++) {
    id += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return id
}

function newQuestion() {
  return {
    id: genId(),
    text: '',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    correctAnswer: 'A',
    marks: 1,
  }
}

function newSection(idx) {
  const letter = idx < 26 ? String.fromCharCode(65 + idx) : String(idx + 1)
  return { id: genId(), title: `Section ${letter}`, questions: [] }
}

/** Parse pasted MCQ text → questions array (mirrors Android QuestionParser) */
function parsePastedText(rawText) {
  if (!rawText.trim()) return []
  const cleaned = rawText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    .replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n').trim()

  const qStartRe = /(?:^|\n)(?:[*#_]{0,3}\s*)?(?:(?:Q(?:uestion)?\s*\d+[:.)])|(?:\d{1,3}[.):/]))/i
  const splitRe = /(?=(?:^|\n)(?:[*#_]{0,3}\s*)?(?:(?:Q(?:uestion)?\s*\d+[:.)])|(?:\d{1,3}[.):/])))/gim

  const normalized = qStartRe.test(cleaned) ? cleaned : `1. ${cleaned}`
  const blocks = normalized.split(splitRe).map(b => b.trim()).filter(Boolean)

  return blocks.flatMap((block, idx) => {
    const lines = block.split('\n').map(l => l.trim()).filter(Boolean)
    const qLines = [], parsed = { optionA: '', optionB: '', optionC: '', optionD: '', correctAnswer: 'A', marks: 1 }
    let mode = 'Q'

    for (const raw of lines) {
      const line = raw.replace(/\*\*/g, '').replace(/^[*#_]+\s*/, '').trim()
      if (!line || line === '---') continue
      const upper = line.toUpperCase()
      const isCorrectMarked = raw.startsWith('*') || /\(CORRECT\)|\[CORRECT\]/i.test(raw) || raw.includes('✓')

      const stripOpt = (l, re) => l.replace(re, '').replace(/\s*\(?\[?CORRECT\]?\)?\s*/gi, '').replace(/✓/g, '').trim()

      if (/^(?!A\s*=)(?:A[:.)\s]|\(A\)|\[A\])/i.test(line)) {
        mode = 'A'; if (isCorrectMarked) parsed.correctAnswer = 'A'
        parsed.optionA = stripOpt(line, /^(?:A[:.)][*_]?|\(A\)[*_]?|\[A\][*_]?)\s*/i)
      } else if (/^(?!B\s*=)(?:B[:.)\s]|\(B\)|\[B\])/i.test(line)) {
        mode = 'B'; if (isCorrectMarked) parsed.correctAnswer = 'B'
        parsed.optionB = stripOpt(line, /^(?:B[:.)][*_]?|\(B\)[*_]?|\[B\][*_]?)\s*/i)
      } else if (/^(?!C\s*=)(?:C[:.)\s]|\(C\)|\[C\])/i.test(line)) {
        mode = 'C'; if (isCorrectMarked) parsed.correctAnswer = 'C'
        parsed.optionC = stripOpt(line, /^(?:C[:.)][*_]?|\(C\)[*_]?|\[C\][*_]?)\s*/i)
      } else if (/^(?!D\s*=)(?:D[:.)\s]|\(D\)|\[D\])/i.test(line)) {
        mode = 'D'; if (isCorrectMarked) parsed.correctAnswer = 'D'
        parsed.optionD = stripOpt(line, /^(?:D[:.)][*_]?|\(D\)[*_]?|\[D\][*_]?)\s*/i)
      } else if (/^(?:CORRECT\s*ANSWER|CORRECT|ANSWER|ANS):/i.test(upper)) {
        mode = 'correct'
        const found = /[A-D]/i.exec(line.split(':')[1] || '')
        if (found) parsed.correctAnswer = found[0].toUpperCase()
      } else if (/^(?:MARKS?|POINTS?):/i.test(upper)) {
        mode = 'marks'
        const m = /\d+/.exec(line.split(':')[1] || '')
        if (m) parsed.marks = Math.max(1, parseInt(m[0]))
      } else {
        if (mode === 'Q') {
          const cleaned2 = qLines.length === 0
            ? line.replace(/^[*#_]*\s*(?:Q(?:uestion)?\s*\d*[:.)]|\d+[:/.)])\s*/i, '').trim()
            : line
          if (cleaned2) qLines.push(cleaned2)
        } else if (mode === 'A') parsed.optionA += ' ' + line
        else if (mode === 'B') parsed.optionB += ' ' + line
        else if (mode === 'C') parsed.optionC += ' ' + line
        else if (mode === 'D') parsed.optionD += ' ' + line
      }
    }

    const text = qLines.join('\n').trim()
    if (!text) return []
    return [{
      id: `q_${idx}_${genId()}`,
      text,
      optionA: parsed.optionA || 'Option A',
      optionB: parsed.optionB || 'Option B',
      optionC: parsed.optionC || 'Option C',
      optionD: parsed.optionD || 'Option D',
      correctAnswer: parsed.correctAnswer,
      marks: parsed.marks,
    }]
  })
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function Toggle({ label, icon: Icon, value, onChange }) {
  return (
    <div className="tc-toggle-row">
      <span className="tc-toggle-label">
        {Icon && <Icon size={13} />}
        {label}
      </span>
      <label className="tc-switch">
        <input type="checkbox" checked={value} onChange={e => onChange(e.target.checked)} />
        <span className="tc-switch-slider" />
      </label>
    </div>
  )
}

function QuestionCard({ question, index, onUpdate, onDelete }) {
  const [open, setOpen] = useState(index === 0)

  const opts = [
    { key: 'optionA', label: 'A' },
    { key: 'optionB', label: 'B' },
    { key: 'optionC', label: 'C' },
    { key: 'optionD', label: 'D' },
  ]

  return (
    <div className="tc-q-card">
      <div className="tc-q-header" onClick={() => setOpen(o => !o)}>
        <span className="tc-q-num">Q{index + 1}</span>
        <span className={`tc-q-preview ${!question.text ? 'empty' : ''}`}>
          {question.text || 'Empty question — click to edit'}
        </span>
        <span className="tc-q-badge">{question.marks} pt{question.marks !== 1 ? 's' : ''}</span>
        <span style={{ color: '#555', marginLeft: 4 }}>
          {open ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
        </span>
      </div>

      {open && (
        <div className="tc-q-body">
          <div className="tc-field">
            <label className="tc-label">Question Text</label>
            <textarea
              className="tc-textarea"
              rows={3}
              placeholder="Enter your question here (LaTeX supported: $x^2 + y^2 = r^2$)"
              value={question.text}
              onChange={e => onUpdate({ ...question, text: e.target.value })}
            />
          </div>

          <div className="tc-q-options">
            {opts.map(({ key, label }) => (
              <div key={key} className="tc-opt-wrap">
                <span className={`tc-opt-letter ${question.correctAnswer === label ? 'correct' : ''}`}>
                  {label}
                </span>
                <input
                  className="tc-input"
                  placeholder={`Option ${label}`}
                  value={question[key]}
                  onChange={e => onUpdate({ ...question, [key]: e.target.value })}
                />
              </div>
            ))}
          </div>

          <div className="tc-q-footer">
            <div className="tc-q-footer-left">
              <span className="tc-marks-field">
                Correct:
                <div className="tc-ans-picker">
                  {['A','B','C','D'].map(l => (
                    <button
                      key={l}
                      type="button"
                      className={`tc-ans-btn ${question.correctAnswer === l ? 'active' : ''}`}
                      onClick={() => onUpdate({ ...question, correctAnswer: l })}
                    >{l}</button>
                  ))}
                </div>
              </span>
              <span className="tc-marks-field">
                Marks:
                <input
                  type="number"
                  min={1}
                  max={100}
                  className="tc-marks-input"
                  value={question.marks}
                  onChange={e => onUpdate({ ...question, marks: Math.max(1, parseInt(e.target.value) || 1) })}
                />
              </span>
            </div>
            <button type="button" className="tc-btn tc-btn-danger tc-btn-sm" onClick={onDelete}>
              <Trash2 size={13} /> Remove
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function SectionCard({ section, index, total, onUpdate, onDelete, onMoveUp, onMoveDown }) {
  const [collapsed, setCollapsed] = useState(false)
  const [showPaste, setShowPaste] = useState(false)
  const [pasteText, setPasteText] = useState('')
  const [parsedPreview, setParsedPreview] = useState(null)

  const updateQuestion = useCallback((qIdx, updated) => {
    const qs = section.questions.map((q, i) => i === qIdx ? updated : q)
    onUpdate({ ...section, questions: qs })
  }, [section, onUpdate])

  const deleteQuestion = useCallback((qIdx) => {
    onUpdate({ ...section, questions: section.questions.filter((_, i) => i !== qIdx) })
  }, [section, onUpdate])

  const addQuestion = () => {
    onUpdate({ ...section, questions: [...section.questions, newQuestion()] })
  }

  const handleImport = () => {
    const parsed = parsePastedText(pasteText)
    if (!parsed.length) { setParsedPreview('⚠ No questions found. Check the format.'); return }
    onUpdate({ ...section, questions: [...section.questions, ...parsed] })
    setPasteText(''); setShowPaste(false); setParsedPreview(null)
  }

  const previewParse = () => {
    const parsed = parsePastedText(pasteText)
    setParsedPreview(parsed.length > 0 ? `✓ Found ${parsed.length} question${parsed.length > 1 ? 's' : ''} — click Import to add them.` : '⚠ No questions found. Check the format.')
  }

  const totalMarks = section.questions.reduce((s, q) => s + (q.marks || 1), 0)

  return (
    <>
      <div className="tc-section">
        <div className="tc-section-header" onClick={() => setCollapsed(c => !c)}>
          <span className="tc-section-num">{String.fromCharCode(65 + index)}</span>
          <input
            className="tc-section-name-input"
            value={section.title}
            onClick={e => e.stopPropagation()}
            onChange={e => onUpdate({ ...section, title: e.target.value })}
            placeholder="Section name…"
          />
          <span className="tc-section-meta">
            {section.questions.length} Q · {totalMarks} pts
          </span>
          {collapsed ? <ChevronDown size={16} style={{ color: '#555' }} /> : <ChevronUp size={16} style={{ color: '#555' }} />}
        </div>

        {!collapsed && (
          <>
            <div className="tc-section-body">
              {section.questions.length === 0 ? (
                <div style={{ padding: '20px 0', textAlign: 'center', color: '#444', fontSize: '0.875rem' }}>
                  No questions yet — add one manually or use Quick Paste ⚡
                </div>
              ) : (
                section.questions.map((q, qIdx) => (
                  <QuestionCard
                    key={q.id}
                    question={q}
                    index={qIdx}
                    onUpdate={updated => updateQuestion(qIdx, updated)}
                    onDelete={() => deleteQuestion(qIdx)}
                  />
                ))
              )}
            </div>

            <div className="tc-section-actions">
              <button type="button" className="tc-btn tc-btn-ghost tc-btn-sm" onClick={addQuestion}>
                <Plus size={13} /> Add Question
              </button>
              <button type="button" className="tc-btn tc-btn-ghost tc-btn-sm"
                style={{ color: '#fbbf24', borderColor: 'rgba(251,191,36,0.25)' }}
                onClick={() => setShowPaste(true)}>
                <Zap size={13} /> Quick Paste
              </button>
              {index > 0 && (
                <button type="button" className="tc-btn tc-btn-ghost tc-btn-sm" onClick={e => { e.stopPropagation(); onMoveUp() }}
                  title="Move section up">↑</button>
              )}
              {index < total - 1 && (
                <button type="button" className="tc-btn tc-btn-ghost tc-btn-sm" onClick={e => { e.stopPropagation(); onMoveDown() }}
                  title="Move section down">↓</button>
              )}
              <button type="button" className="tc-btn tc-btn-danger tc-btn-sm" style={{ marginLeft: 'auto' }} onClick={onDelete}>
                <Trash2 size={13} /> Delete Section
              </button>
            </div>
          </>
        )}
      </div>

      {/* Quick Paste Modal */}
      {showPaste && (
        <div className="tc-modal-overlay" onClick={() => setShowPaste(false)}>
          <div className="tc-modal" onClick={e => e.stopPropagation()}>
            <div className="tc-modal-title">⚡ Quick Paste — {section.title}</div>
            <div className="tc-modal-sub">
              Paste MCQ text below. Supports formats like:<br />
              <code style={{ fontSize: '0.78rem', color: '#888' }}>
                1. Question text<br />
                A. Option A &nbsp; B. Option B &nbsp; C. Option C &nbsp; D. Option D<br />
                ANSWER: B &nbsp; MARKS: 2
              </code>
            </div>

            <textarea
              className="tc-textarea"
              rows={10}
              placeholder={"Paste questions here…\n\nExample:\n1. What is 2 + 2?\nA. 3\nB. 4\nC. 5\nD. 6\nANSWER: B\nMARKS: 1"}
              value={pasteText}
              onChange={e => { setPasteText(e.target.value); setParsedPreview(null) }}
              autoFocus
            />

            {parsedPreview && (
              <div style={{ marginTop: 8, fontSize: '0.85rem', color: parsedPreview.startsWith('✓') ? '#4ade80' : '#fbbf24' }}>
                {parsedPreview}
              </div>
            )}

            <div className="tc-modal-footer">
              <button type="button" className="tc-btn tc-btn-ghost" onClick={() => { setShowPaste(false); setPasteText(''); setParsedPreview(null) }}>
                Cancel
              </button>
              <button type="button" className="tc-btn tc-btn-ghost" onClick={previewParse}>
                <Eye size={14} /> Preview
              </button>
              <button type="button" className="tc-btn tc-btn-primary" onClick={handleImport} disabled={!pasteText.trim()}>
                <Zap size={14} /> Import Questions
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────
export default function TestCreatorPage({ user }) {
  // ── Meta ──
  const [title, setTitle] = useState('')
  const [instructions, setInstructions] = useState('')
  const [duration, setDuration] = useState(60)
  const [passingMarks, setPassingMarks] = useState('')
  const [releaseMode, setReleaseMode] = useState('table_only')
  const [releaseDate, setReleaseDate] = useState('')

  // ── Sections ──
  const [sections, setSections] = useState([newSection(0)])

  // ── Instructors (for assigning ownership) ──
  const [instructors, setInstructors] = useState([])
  const [assignedInstructor, setAssignedInstructor] = useState('')

  // ── Anti-cheat ──
  const [ac, setAc] = useState({
    fullscreen: true,
    leaveApp: true,
    copyPaste: true,
    screenshot: true,
    camera: false,
    randQ: true,
    randO: true,
    autoSubmit: true,
  })

  // ── UI state ──
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState(null)
  const [error, setError] = useState(null)
  const [editingId, setEditingId] = useState(null) // null = create mode

  const { docId } = useParams()
  const navigate = useNavigate()

  // ── Load test details if in edit mode ──
  useEffect(() => {
    if (docId) {
      setEditingId(docId)
      getDoc(doc(db, 'tests', docId)).then(snap => {
        if (snap.exists()) {
          const test = snap.data()
          setTitle(test.title || '')
          setInstructions(test.instructions || '')
          setDuration(test.durationMinutes || 60)
          setPassingMarks(test.passingMarks || '')
          setReleaseMode(test.releaseScoreMode || 'table_only')
          setReleaseDate(test.resultReleaseTime ? new Date(test.resultReleaseTime.seconds * 1000).toISOString().slice(0, 16) : '')
          setSections(test.sections || [])
          setAssignedInstructor(test.instructorId || '')
          setAc({
            fullscreen: test.antiCheatFullscreen !== false,
            leaveApp: test.antiCheatDetectLeaveApp !== false,
            copyPaste: test.antiCheatBlockCopyPaste !== false,
            screenshot: test.antiCheatBlockScreenshot !== false,
            camera: !!test.antiCheatCamera,
            randQ: test.antiCheatRandomizeQuestions !== false,
            randO: test.antiCheatRandomizeOptions !== false,
            autoSubmit: test.antiCheatAutoSubmit !== false,
          })
        }
      }).catch(err => {
        setError('Failed to load test: ' + err.message)
      })
    }
  }, [docId])

  // ── Load instructors on mount ──
  useEffect(() => {
    if (user?.role === 'superadmin') {
      getDocs(collection(db, 'users')).then(snap => {
        const list = snap.docs
          .filter(d => d.data().role === 'instructor')
          .map(d => ({ uid: d.id, ...d.data() }))
          .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
        setInstructors(list)
      }).catch(() => {})
    } else if (user?.role === 'instructor') {
      setAssignedInstructor(user.uid)
    }
  }, [user])

  // ── Computed stats ──
  const totalQuestions = sections.reduce((s, sec) => s + sec.questions.length, 0)
  const totalMarks = sections.reduce((s, sec) => s + sec.questions.reduce((ss, q) => ss + (q.marks || 1), 0), 0)

  // ── Section helpers ──
  const addSection = () => setSections(prev => [...prev, newSection(prev.length)])
  const updateSection = useCallback((idx, updated) => {
    setSections(prev => prev.map((s, i) => i === idx ? updated : s))
  }, [])
  const deleteSection = useCallback((idx) => {
    setSections(prev => prev.filter((_, i) => i !== idx))
  }, [])
  const moveSection = useCallback((idx, dir) => {
    setSections(prev => {
      const arr = [...prev]
      const target = idx + dir
      if (target < 0 || target >= arr.length) return arr
      ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
      return arr
    })
  }, [])

  // ── Save ──
  const handleSave = async () => {
    if (!title.trim()) { setError('Please enter a test title.'); return }
    if (sections.length === 0 || totalQuestions === 0) { setError('Add at least one question.'); return }
    if (!assignedInstructor) { setError('Select an instructor to assign this test to.'); return }

    setError(null)
    setSaving(true)

    try {
      const resolvedReleaseTime = releaseDate
        ? Timestamp.fromDate(new Date(releaseDate))
        : (() => {
            const d = new Date(); d.setHours(20, 0, 0, 0); return Timestamp.fromDate(d)
          })()

      const testId = editingId || genUniqueTestId()

      const payload = {
        testId,
        title: title.trim(),
        instructions: instructions.trim(),
        durationMinutes: parseInt(duration) || 60,
        passingMarks: parseInt(passingMarks) || 0,
        totalMarks,
        sections: sections.map(sec => ({
          id: sec.id,
          title: sec.title,
          questions: sec.questions.map(q => ({
            id: q.id,
            text: q.text.trim(),
            optionA: q.optionA.trim(),
            optionB: q.optionB.trim(),
            optionC: q.optionC.trim(),
            optionD: q.optionD.trim(),
            correctAnswer: q.correctAnswer,
            marks: q.marks || 1,
          })),
        })),
        releaseScoreMode: releaseMode,
        resultReleaseTime: resolvedReleaseTime,
        resultsReleasedEarly: false,
        instructorId: assignedInstructor,
        isEnabled: true,
        antiCheatFullscreen: ac.fullscreen,
        antiCheatDetectLeaveApp: ac.leaveApp,
        antiCheatBlockCopyPaste: ac.copyPaste,
        antiCheatBlockScreenshot: ac.screenshot,
        antiCheatCamera: ac.camera,
        antiCheatRandomizeQuestions: ac.randQ,
        antiCheatRandomizeOptions: ac.randO,
        antiCheatAutoSubmit: ac.autoSubmit,
        instituteId: user?.role === 'instructor' ? (user.instituteId || '') : (instructors.find(i => i.uid === assignedInstructor)?.instituteId || ''),
        batchId: '',
        roster: [],
      }

      if (editingId) {
        await updateDoc(doc(db, 'tests', editingId), { ...payload, updatedAt: serverTimestamp() })
        
        // Also update tests_public snapshot
        const publicPayload = {
          testId,
          title: payload.title,
          instructions: payload.instructions,
          durationMinutes: payload.durationMinutes,
          passingMarks: payload.passingMarks,
          totalMarks: payload.totalMarks,
          isEnabled: payload.isEnabled,
          instructorId: payload.instructorId,
          releaseScoreMode: payload.releaseScoreMode,
          resultReleaseTime: resolvedReleaseTime,
          resultsReleasedEarly: payload.resultsReleasedEarly || false,
          antiCheatFullscreen: ac.fullscreen,
          antiCheatDetectLeaveApp: ac.leaveApp,
          antiCheatBlockCopyPaste: ac.copyPaste,
          antiCheatBlockScreenshot: ac.screenshot,
          antiCheatCamera: ac.camera,
          antiCheatRandomizeQuestions: ac.randQ,
          antiCheatRandomizeOptions: ac.randO,
          antiCheatAutoSubmit: ac.autoSubmit,
          updatedAt: serverTimestamp(),
          instituteId: payload.instituteId,
          batchId: '',
          sections: sections.map(sec => ({
            id: sec.id,
            title: sec.title,
            questions: sec.questions.map(q => ({
              id: q.id,
              text: q.text.trim(),
              optionA: q.optionA.trim(),
              optionB: q.optionB.trim(),
              optionC: q.optionC.trim(),
              optionD: q.optionD.trim(),
              // correctAnswer intentionally omitted for public snapshot
              marks: q.marks || 1,
            })),
          })),
        }
        await updateDoc(doc(db, 'tests_public', editingId), publicPayload).catch(() => {})

        if (user?.role === 'superadmin') {
          await logAdminAction('test_updated', { testId, title: payload.title })
        }
        showToast('Test updated successfully!')
        setTimeout(() => navigate('/exams'), 1000)
      } else {
        payload.createdAt = serverTimestamp()
        await setDoc(doc(db, 'tests', testId), payload)
        
        // Also write to tests_public (no correct answers)
        const publicPayload = {
          testId,
          title: payload.title,
          instructions: payload.instructions,
          durationMinutes: payload.durationMinutes,
          passingMarks: payload.passingMarks,
          totalMarks: payload.totalMarks,
          isEnabled: true,
          instructorId: payload.instructorId,
          releaseScoreMode: payload.releaseScoreMode,
          resultReleaseTime: resolvedReleaseTime,
          resultsReleasedEarly: false,
          antiCheatFullscreen: ac.fullscreen,
          antiCheatDetectLeaveApp: ac.leaveApp,
          antiCheatBlockCopyPaste: ac.copyPaste,
          antiCheatBlockScreenshot: ac.screenshot,
          antiCheatCamera: ac.camera,
          antiCheatRandomizeQuestions: ac.randQ,
          antiCheatRandomizeOptions: ac.randO,
          antiCheatAutoSubmit: ac.autoSubmit,
          createdAt: serverTimestamp(),
          instituteId: payload.instituteId,
          batchId: '',
          sections: sections.map(sec => ({
            id: sec.id,
            title: sec.title,
            questions: sec.questions.map(q => ({
              id: q.id,
              text: q.text.trim(),
              optionA: q.optionA.trim(),
              optionB: q.optionB.trim(),
              optionC: q.optionC.trim(),
              optionD: q.optionD.trim(),
              // correctAnswer intentionally omitted for public snapshot
              marks: q.marks || 1,
            })),
          })),
        }
        await setDoc(doc(db, 'tests_public', testId), publicPayload)
        
        if (user?.role === 'superadmin') {
          await logAdminAction('test_created', { testId, title: payload.title, questions: totalQuestions })
        }
        showToast(`Test created! ID: ${testId}`)
        resetForm()
        setTimeout(() => navigate('/exams'), 1000)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setSaving(false)
    }
  }

  const resetForm = () => {
    setTitle(''); setInstructions(''); setDuration(60)
    setPassingMarks(''); setReleaseMode('table_only'); setReleaseDate('')
    setSections([newSection(0)]); setAssignedInstructor(user?.role === 'instructor' ? user.uid : ''); setEditingId(null)
    setAc({ fullscreen: true, leaveApp: true, copyPaste: true, screenshot: true, camera: false, randQ: true, randO: true, autoSubmit: true })
  }

  const showToast = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(null), 3000)
  }

  return (
    <div style={{ padding: '24px 32px', maxWidth: 1600, margin: '0 auto' }}>
      {/* ── Top bar ───────────────────────────────────────────────────────── */}
      <div className="tc-topbar" style={{ marginBottom: 24 }}>
        <div className="tc-topbar-left">
          <div className="tc-page-title">{editingId ? 'Edit Test' : 'Create Test'}</div>
          <div className="tc-page-sub">Build a complete exam with sections, questions, and anti-cheat settings</div>
        </div>
        <div className="tc-topbar-right">
          {editingId && (
            <button type="button" className="tc-btn tc-btn-ghost" onClick={resetForm}>
              New Test
            </button>
          )}
          <div className="tc-stats-strip">
            <div className="tc-stat-chip"><BookOpen size={13} /> <strong>{sections.length}</strong> section{sections.length !== 1 ? 's' : ''}</div>
            <div className="tc-stat-chip"><FileText size={13} /> <strong>{totalQuestions}</strong> Q</div>
            <div className="tc-stat-chip"><Award size={13} /> <strong>{totalMarks}</strong> pts</div>
          </div>
          <button
            type="button"
            className="tc-btn tc-btn-primary"
            onClick={handleSave}
            disabled={saving}
          >
            {saving ? 'Saving…' : (editingId ? '💾 Update Test' : '🚀 Publish Test')}
          </button>
        </div>
      </div>

      {error && (
        <div style={{ background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.3)', borderRadius: 10, padding: '12px 16px', marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8, color: '#fca5a5', fontSize: '0.875rem' }}>
          <AlertCircle size={16} /> {error}
        </div>
      )}

      <div className="tc-root">
        {/* ── Sidebar ──────────────────────────────────────────────────────── */}
        <div className="tc-sidebar">

          {/* Test Info */}
          <div className="tc-card">
            <div className="tc-card-title"><Settings2 size={13} /> Test Settings</div>

            <div className="tc-field">
              <label className="tc-label">Test Title *</label>
              <input className="tc-input" placeholder="e.g. Physics Mid-Term 2024" value={title} onChange={e => setTitle(e.target.value)} />
            </div>

            <div className="tc-field">
              <label className="tc-label">Instructions (optional)</label>
              <textarea className="tc-textarea" rows={3} placeholder="Instructions shown to students before they start…" value={instructions} onChange={e => setInstructions(e.target.value)} />
            </div>

            <div className="tc-input-row">
              <div className="tc-field">
                <label className="tc-label"><Clock size={11} /> Duration (mins)</label>
                <input className="tc-input" type="number" min={5} value={duration} onChange={e => setDuration(e.target.value)} />
              </div>
              <div className="tc-field">
                <label className="tc-label"><Award size={11} /> Passing Marks</label>
                <input className="tc-input" type="number" min={0} placeholder="e.g. 40" value={passingMarks} onChange={e => setPassingMarks(e.target.value)} />
              </div>
            </div>

            <div className="tc-field">
              <label className="tc-label">Total Marks (auto)</label>
              <input className="tc-input" readOnly value={totalMarks} style={{ opacity: 0.6, cursor: 'default' }} />
            </div>

            <div className="tc-field">
              <label className="tc-label">Score Release Mode</label>
              <select className="tc-select" value={releaseMode} onChange={e => setReleaseMode(e.target.value)}>
                <option value="table_only">Score table only</option>
                <option value="full_answers">Full test + answers</option>
              </select>
            </div>

            <div className="tc-field">
              <label className="tc-label">Result Release Date & Time</label>
              <input className="tc-input" type="datetime-local" value={releaseDate} onChange={e => setReleaseDate(e.target.value)} />
            </div>

            {user?.role === 'superadmin' && (
              <div className="tc-field">
                <label className="tc-label"><Users size={11} /> Assign to Instructor *</label>
                <select className="tc-select" value={assignedInstructor} onChange={e => setAssignedInstructor(e.target.value)}>
                  <option value="">— Select instructor —</option>
                  {instructors.map(i => (
                    <option key={i.uid} value={i.uid}>{i.name || i.email}</option>
                  ))}
                </select>
              </div>
            )}
          </div>

          {/* Anti-cheat */}
          <div className="tc-card">
            <div className="tc-card-title"><Shield size={13} /> Anti-Cheat Settings</div>
            <Toggle label="Full-screen mode" value={ac.fullscreen} onChange={v => setAc(p => ({ ...p, fullscreen: v }))} />
            <Toggle label="Detect leaving app" value={ac.leaveApp} onChange={v => setAc(p => ({ ...p, leaveApp: v }))} />
            <Toggle label="Block copy & paste" value={ac.copyPaste} onChange={v => setAc(p => ({ ...p, copyPaste: v }))} />
            <Toggle label="Block screenshots" value={ac.screenshot} onChange={v => setAc(p => ({ ...p, screenshot: v }))} />
            <Toggle label="Camera proctoring" value={ac.camera} onChange={v => setAc(p => ({ ...p, camera: v }))} />
            <Toggle label="Randomize question order" value={ac.randQ} onChange={v => setAc(p => ({ ...p, randQ: v }))} />
            <Toggle label="Randomize option order" value={ac.randO} onChange={v => setAc(p => ({ ...p, randO: v }))} />
            <Toggle label="Auto-submit when time ends" value={ac.autoSubmit} onChange={v => setAc(p => ({ ...p, autoSubmit: v }))} />
          </div>

          {/* Quick Guide */}
          <div className="tc-card" style={{ background: 'rgba(239,68,68,0.06)', borderColor: 'rgba(239,68,68,0.2)' }}>
            <div className="tc-card-title" style={{ color: '#f87171' }}><Zap size={13} /> Quick Paste Guide</div>
            <div style={{ fontSize: '0.78rem', color: '#a3a3a3', lineHeight: 1.7 }}>
              Click <strong style={{ color: '#fbbf24' }}>⚡ Quick Paste</strong> on any section and paste raw MCQ text. Format:<br /><br />
              <code style={{ display: 'block', background: 'rgba(0,0,0,0.4)', padding: '8px 10px', borderRadius: 6, fontSize: '0.72rem', lineHeight: 1.6 }}>
                1. Question text here{'\n'}
                A. First option{'\n'}
                B. Second option{'\n'}
                C. Third option{'\n'}
                D. Fourth option{'\n'}
                ANSWER: B{'\n'}
                MARKS: 2
              </code>
            </div>
          </div>
        </div>

        {/* ── Main content ─────────────────────────────────────────────────── */}
        <div className="tc-main">
          {sections.map((sec, idx) => (
            <SectionCard
              key={sec.id}
              section={sec}
              index={idx}
              total={sections.length}
              onUpdate={updated => updateSection(idx, updated)}
              onDelete={() => deleteSection(idx)}
              onMoveUp={() => moveSection(idx, -1)}
              onMoveDown={() => moveSection(idx, 1)}
            />
          ))}

          <div className="tc-add-section" onClick={addSection}>
            <Plus size={18} /> Add New Section
          </div>

          {/* Final save button (bottom) */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12, paddingTop: 8 }}>
            {editingId && (
              <button type="button" className="tc-btn tc-btn-ghost" onClick={resetForm}>
                Discard Changes
              </button>
            )}
            <button
              type="button"
              className="tc-btn tc-btn-primary"
              onClick={handleSave}
              disabled={saving}
              style={{ padding: '12px 28px', fontSize: '1rem' }}
            >
              {saving ? 'Saving…' : (editingId ? '💾 Update Test' : '🚀 Publish Test')}
            </button>
          </div>
        </div>
      </div>

      {/* Toast */}
      {toast && (
        <div className="tc-success-toast">
          <CheckCircle size={16} /> {toast}
        </div>
      )}
    </div>
  )
}
