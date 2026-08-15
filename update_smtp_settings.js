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

async function updateSettings() {
  try {
    const docRef = doc(db, 'platform_settings', 'global');
    await updateDoc(docRef, {
      "emailSettings.smtpHost": "smtp.gmail.com",
      "emailSettings.senderEmail": "learnwithnisar@gmail.com"
    });
    console.log("SUCCESS: SMTP settings updated in Firestore!");
  } catch (error) {
    console.error("ERROR updating settings:", error);
  }
}

updateSettings();
