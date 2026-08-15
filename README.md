# Exam System — SaaS Platform

> **Online MCQ Examination Platform** built for Students Welfare Foundation
> Three integrated systems: React Admin Dashboard · Kotlin Android App · Firebase Backend

---

## Project Structure

```
d:\EXAM SYSTEM\
│
├── src/                         ← React Super Admin Dashboard
│   ├── pages/
│   │   ├── AdminLogin.jsx       ← Firebase Auth login (superadmin role only)
│   │   ├── Dashboard.jsx        ← Platform stats overview
│   │   ├── InstructorManagement.jsx  ← Approve / Suspend / Delete instructors
│   │   ├── PaymentManagement.jsx     ← View screenshots, approve/reject payments
│   │   ├── Analytics.jsx        ← Revenue + exam charts (Recharts)
│   │   └── PlatformSettings.jsx ← Pricing, payment accounts, branding
│   ├── components/
│   │   └── AdminLayout.jsx      ← Sidebar navigation shell
│   ├── styles/                  ← Per-page CSS files
│   ├── firebase.js              ← Firebase SDK init (ADD YOUR CONFIG HERE)
│   ├── App.jsx                  ← Router + auth guard
│   └── index.css                ← Global design tokens
│
├── android-app/                 ← Kotlin Android App (copy into Android Studio)
│   ├── src/main/java/com/examsystem/app/
│   │   ├── MainActivity.kt      ← Full Navigation Graph (all screens wired)
│   │   ├── data/
│   │   │   ├── models/Models.kt ← All Firestore data models
│   │   │   └── repository/FirebaseRepository.kt  ← All Firebase operations
│   │   ├── viewmodel/
│   │   │   └── ViewModels.kt    ← InstructorVM · StudentVM · ResultsVM
│   │   └── ui/screens/
│   │       ├── SplashScreen.kt
│   │       ├── RoleSelectionScreen.kt
│   │       ├── InstructorLoginScreen.kt
│   │       ├── InstructorDashboardScreen.kt
│   │       ├── CreateTestScreen.kt
│   │       ├── TestManagementScreen.kt
│   │       ├── ResultsDashboardScreen.kt
│   │       ├── StudentTestIdScreen.kt
│   │       ├── StudentFormScreen.kt
│   │       ├── InstructionsScreen.kt
│   │       ├── ExamScreen.kt
│   │       └── SubmitSuccessScreen.kt
│   └── app/build.gradle         ← All dependencies declared
│
└── firebase/                    ← Firebase backend config
    ├── firestore.rules          ← Role-based security rules
    ├── storage.rules            ← Screenshot upload rules
    ├── firestore.indexes.json   ← All required composite indexes
    ├── firebase.json            ← Firebase CLI config
    └── functions/
        ├── index.js             ← Cloud Functions (grading, ranking, payments)
        └── package.json
```

---

## Setup Guide

### STEP 1 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. Click **Add project** → name it `exam-system`
3. Enable **Google Analytics** (optional)

### STEP 2 — Enable Firebase Services

In the Firebase console, enable:
- **Authentication** → Email/Password provider
- **Firestore Database** → Start in **production mode**
- **Storage** → Start in **production mode**
- **Functions** → Upgrade to Blaze plan (required for Cloud Functions)

### STEP 3 — Configure the React Admin Dashboard

1. In Firebase Console → **Project Settings** → **Your apps** → Add a **Web app**
2. Copy the config object
3. Open `d:\EXAM SYSTEM\src\firebase.js` and replace the placeholder values:

```js
const firebaseConfig = {
  apiKey:            "YOUR_API_KEY",
  authDomain:        "YOUR_PROJECT_ID.firebaseapp.com",
  projectId:         "YOUR_PROJECT_ID",
  storageBucket:     "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_MESSAGING_SENDER_ID",
  appId:             "YOUR_APP_ID"
};
```

### STEP 4 — Create the Super Admin Account

In Firebase Console → **Authentication** → Add user:
- Email: `admin@yourplatform.com`
- Password: (your choice)

Then in **Firestore** → Create document manually:
```
Collection: users
Document ID: <the UID from Authentication>
Fields:
  email:              "admin@yourplatform.com"
  role:               "superadmin"
  approvalStatus:     "approved"
  subscriptionStatus: "active"
```

### STEP 5 — Deploy Firebase Rules & Functions

```bash
# Install Firebase CLI if needed
npm install -g firebase-tools

# Login
firebase login

# Change to firebase directory
cd "d:\EXAM SYSTEM\firebase"

# Deploy everything
firebase deploy
```

### STEP 6 — Run the React Admin Dashboard

```bash
cd "d:\EXAM SYSTEM"
npm run dev
# Open http://localhost:5173
```

### STEP 7 — Set Up Android App in Android Studio

1. Open **Android Studio** → **Open** → select `d:\EXAM SYSTEM\android-app`
2. In Firebase Console → **Project Settings** → Add an **Android app**
   - Package: `com.examsystem.app`
3. Download `google-services.json` and place it in `android-app/app/`
4. Sync Gradle → Run on emulator or device

---

## User Roles Quick Reference

| Role        | Platform       | Access                                    |
|-------------|----------------|-------------------------------------------|
| Super Admin | React Web      | Full platform — all instructors, payments, analytics |
| Instructor  | Android App    | Own tests only — create, manage, view results |
| Student     | Android App    | Test ID entry → attempt exam only         |

## Subscription Plans

| Plan       | Price USD | Approx PKR* |
|------------|-----------|-------------|
| Weekly     | $5        | ~PKR 1,392  |
| Monthly    | $10       | ~PKR 2,785  |
| Six Months | $50       | ~PKR 13,925 |
| Yearly     | $100      | ~PKR 27,850 |

*PKR rate configurable in Platform Settings

## Payment Methods

| Method    | Flow                                                     |
|-----------|----------------------------------------------------------|
| JazzCash  | Account number shown → manual transfer → Admin approves  |
| Easypaisa | Account number shown → manual transfer → Admin approves  |
| Binance   | Pay ID shown → upload screenshot + ref number → Admin approves |

## Cloud Functions

| Function              | Trigger                     | Action                                           |
|-----------------------|-----------------------------|--------------------------------------------------|
| `gradeAndRankAttempt` | New attempt created         | Auto-grade answers, calculate scores, rank all   |
| `reRankOnUpdate`      | Attempt score updated       | Re-rank all students for the test                |
| `onPaymentUpdated`    | Payment status → approved   | Activate instructor + delete screenshot          |
| `expireSubscriptions` | Daily schedule              | Mark expired subscriptions as inactive           |

---

## Key Security Features

- ✅ Students **cannot** read correct answers (Firestore rules block access)
- ✅ Students **cannot** view other students' submissions  
- ✅ Instructors **can only** access their own tests and results
- ✅ Super Admin has **full system access**
- ✅ Questions are **shuffled** differently for every student
- ✅ Payment screenshots **auto-deleted** after admin approval
- ✅ Subscriptions **auto-expire** via daily Cloud Function
- ✅ Instructor accounts require **approval + active subscription** to login
