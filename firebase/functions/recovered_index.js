Created At: 2026-05-28T20:04:46Z
Completed At: 2026-05-28T20:04:46Z
File Path: `file:///d:/EXAM%20SYSTEM/README.md`
Total Lines: 195
Total Bytes: 7586
Showing lines 1 to 195
The following code has been modified to include a line number before every line, in the format: <line_number>: <original_line>. Please note that any changes targeting the original code should remove the line number, colon, and leading space.
1: # Exam System — SaaS Platform
2: 
3: > **Online MCQ Examination Platform** built for Students Welfare Foundation
4: > Three integrated systems: React Admin Dashboard · Kotlin Android App · Firebase Backend
5: 
6: ---
7: 
8: ## Project Structure
9: 
10: ```
11: d:\EXAM SYSTEM\
12: │
13: ├── src/                         ← React Super Admin Dashboard
14: │   ├── pages/
15: │   │   ├── AdminLogin.jsx       ← Firebase Auth login (superadmin role only)
16: │   │   ├── Dashboard.jsx        ← Platform stats overview
17: │   │   ├── InstructorManagement.jsx  ← Approve / Suspend / Delete instructors
18: │   │   ├── PaymentManagement.jsx     ← View screenshots, approve/reject payments
19: │   │   ├── Analytics.jsx        ← Revenue + exam charts (Recharts)
20: │   │   └── PlatformSettings.jsx ← Pricing, payment accounts, branding
21: │   ├── components/
22: │   │   └── AdminLayout.jsx      ← Sidebar navigation shell
23: │   ├── styles/                  ← Per-page CSS files
24: │   ├── firebase.js              ← Firebase SDK init (ADD YOUR CONFIG HERE)
25: │   ├── App.jsx                  ← Router + auth guard
26: │   └── index.css                ← Global design tokens
27: │
28: ├── android-app/                 ← Kotlin Android App (copy into Android Studio)
29: │   ├── src/main/java/com/examsystem/app/
30: │   │   ├── MainActivity.kt      ← Full Navigation Graph (all screens wired)
31: │   │   ├── data/
32: │   │   │ 
<truncated 4856 bytes>
160: | Monthly    | $10       | ~PKR 2,785  |
161: | Six Months | $50       | ~PKR 13,925 |
162: | Yearly     | $100      | ~PKR 27,850 |
163: 
164: *PKR rate configurable in Platform Settings
165: 
166: ## Payment Methods
167: 
168: | Method    | Flow                                                     |
169: |-----------|----------------------------------------------------------|
170: | JazzCash  | Account number shown → manual transfer → Admin approves  |
171: | Easypaisa | Account number shown → manual transfer → Admin approves  |
172: | Binance   | Pay ID shown → upload screenshot + ref number → Admin approves |
173: 
174: ## Cloud Functions
175: 
176: | Function              | Trigger                     | Action                                           |
177: |-----------------------|-----------------------------|--------------------------------------------------|
178: | `gradeAndRankAttempt` | New attempt created         | Auto-grade answers, calculate scores, rank all   |
179: | `reRankOnUpdate`      | Attempt score updated       | Re-rank all students for the test                |
180: | `onPaymentUpdated`    | Payment status → approved   | Activate instructor + delete screenshot          |
181: | `expireSubscriptions` | Daily schedule              | Mark expired subscriptions as inactive           |
182: 
183: ---
184: 
185: ## Key Security Features
186: 
187: - ✅ Students **cannot** read correct answers (Firestore rules block access)
188: - ✅ Students **cannot** view other students' submissions  
189: - ✅ Instructors **can only** access their own tests and results
190: - ✅ Super Admin has **full system access**
191: - ✅ Questions are **shuffled** differently for every student
192: - ✅ Payment screenshots **auto-deleted** after admin approval
193: - ✅ Subscriptions **auto-expire** via daily Cloud Function
194: - ✅ Instructor accounts require **approval + active subscription** to login
195: 
The above content shows the entire, complete file contents of the requested file.
