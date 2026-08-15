import { initializeApp } from 'firebase/app';
import { getFirestore, doc, updateDoc } from 'firebase/firestore';

const firebaseConfig = {
  apiKey: "AIzaSyD4IEXsLsMRP1WQHAH_cuE0Lste3cDydCY",
  authDomain: "examapp-57718.firebaseapp.com",
  projectId: "examapp-57718",
  storageBucket: "examapp-57718.firebasestorage.app",
  messagingSenderId: "883580931492",
  appId: "1:883580931492:web:8819e0ded451ed3537c531"
};

const app = initializeApp(firebaseConfig);
const db = getFirestore(app);

async function updateSmtp() {
  try {
    const ref = doc(db, 'platform_settings', 'global');
    await updateDoc(ref, {
      'emailSettings.smtpUser':    'semantic002@gmail.com',
      'emailSettings.smtpPass':    'mysjhedlhswrtilx',
      'emailSettings.senderEmail': 'semantic002@gmail.com',
      'emailSettings.senderName':  'SWF Exam System',
      'emailSettings.smtpHost':    'smtp.gmail.com',
      'emailSettings.smtpPort':    '587',
    });
    console.log('SUCCESS: SMTP settings updated!');
    console.log('  Sender:   semantic002@gmail.com');
    console.log('  Password: mysjhedlhswrtilx');
    console.log('  Host:     smtp.gmail.com:587');
  } catch (err) {
    console.error('ERROR:', err.message);
  }
  process.exit(0);
}

updateSmtp();
