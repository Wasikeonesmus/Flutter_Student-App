const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

// Initialize with application default credentials
const app = initializeApp({
  credential: require('firebase-admin').credential.applicationDefault(),
  projectId: 'examapp-57718'
});

const db = getFirestore(app);

async function updateSmtpSettings() {
  try {
    const ref = db.collection('platform_settings').doc('global');
    await ref.update({
      'emailSettings.smtpUser':    'semantic002@gmail.com',
      'emailSettings.smtpPass':    'mysjhedlhswrtilx',
      'emailSettings.senderEmail': 'semantic002@gmail.com',
      'emailSettings.senderName':  'SWF Exam System',
      'emailSettings.smtpHost':    'smtp.gmail.com',
      'emailSettings.smtpPort':    '587',
    });
    console.log('✅ SUCCESS: SMTP settings updated in Firestore!');
    console.log('   Sender: semantic002@gmail.com');
    console.log('   Host:   smtp.gmail.com:587');
  } catch (err) {
    console.error('❌ ERROR updating settings:', err.message);
  }
  process.exit(0);
}

updateSmtpSettings();
