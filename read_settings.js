import { initializeApp } from 'firebase/app';
import { getFirestore, doc, getDoc } from 'firebase/firestore';

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

async function checkSettings() {
  try {
    const docRef = doc(db, 'platform_settings', 'global');
    const docSnap = await getDoc(docRef);
    if (docSnap.exists()) {
      console.log("SUCCESS: Document data:");
      console.log(JSON.stringify(docSnap.data(), null, 2));
    } else {
      console.log("ERROR: No such document!");
    }
  } catch (error) {
    console.error("ERROR reading document:", error);
  }
}

checkSettings();
