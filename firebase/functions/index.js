const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

/**
 * Triggered on creation of a new exam attempt.
 * Securely calculates scores on the server to prevent client-side tampering,
 * and updates the student ranks for the test.
 */
exports.gradeAndRankAttempt = functions.firestore.document('attempts/{attemptId}').onCreate(async (snap, context) => {
  const attemptId = context.params.attemptId;
  const attempt = snap.data();
  const testId = attempt.testId;

  if (!testId) {
    console.error(`Attempt ${attemptId} has no testId.`);
    return;
  }

  try {
    // 1. Fetch the master test document containing questions and correct answers
    const testDoc = await db.collection('tests').doc(testId.toUpperCase()).get();
    if (!testDoc.exists) {
      console.error(`Test ${testId} not found for grading attempt ${attemptId}`);
      return;
    }
    const test = testDoc.data();

    // 2. Map correct answers and question marks
    const correctAnswers = {};
    const questionMarks = {};
    const sectionIdsByQuestion = {};
    const sectionTitlesById = {};

    if (test.sections && Array.isArray(test.sections)) {
      test.sections.forEach(sec => {
        sectionTitlesById[sec.id] = sec.title;
        if (sec.questions && Array.isArray(sec.questions)) {
          sec.questions.forEach(q => {
            if (q.id) {
              correctAnswers[q.id] = (q.correctAnswer || '').trim().toUpperCase();
              questionMarks[q.id] = Number(q.marks) || 1;
              sectionIdsByQuestion[q.id] = sec.id;
            }
          });
        }
      });
    }

    // 3. Compute score and section scores
    let totalScore = 0;
    const sectionScores = {};

    // Initialize section scores to 0
    if (test.sections && Array.isArray(test.sections)) {
      test.sections.forEach(sec => {
        sectionScores[sec.id] = 0;
        sectionScores[sec.title] = 0;
      });
    }

    const answers = attempt.answers || {};
    Object.keys(answers).forEach(qId => {
      const studentAns = (answers[qId] || '').trim().toUpperCase();
      const correctAns = correctAnswers[qId];
      if (correctAns && studentAns === correctAns) {
        const marks = questionMarks[qId] || 1;
        totalScore += marks;
        const secId = sectionIdsByQuestion[qId];
        if (secId) {
          sectionScores[secId] = (sectionScores[secId] || 0) + marks;
          const secTitle = sectionTitlesById[secId];
          if (secTitle) {
            sectionScores[secTitle] = (sectionScores[secTitle] || 0) + marks;
          }
        }
      }
    });

    const totalMarks = Number(test.totalMarks) || 1;
    const percentage = Math.round((totalScore / totalMarks) * 100);
    const passed = totalScore >= (test.passingMarks || 0);

    // 4. Retrieve all attempts for this test to compute the ranks
    const attemptsSnap = await db.collection('attempts').where('testId', '==', testId).get();
    const attempts = [];
    attemptsSnap.forEach(d => {
      if (d.id === attemptId) {
        attempts.push({ id: d.id, ...d.data(), totalScore, submittedAt: attempt.submittedAt });
      } else {
        attempts.push({ id: d.id, ...d.data() });
      }
    });

    // Sort attempts by score descending, then by submission time ascending
    attempts.sort((a, b) => {
      const scoreA = Number(a.totalScore) || 0;
      const scoreB = Number(b.totalScore) || 0;
      if (scoreB !== scoreA) {
        return scoreB - scoreA;
      }
      const timeA = a.submittedAt ? (a.submittedAt.toDate ? a.submittedAt.toDate().getTime() : new Date(a.submittedAt).getTime()) : 0;
      const timeB = b.submittedAt ? (b.submittedAt.toDate ? b.submittedAt.toDate().getTime() : new Date(b.submittedAt).getTime()) : 0;
      if (timeA !== timeB) {
        return timeA - timeB;
      }
      return a.id.localeCompare(b.id);
    });

    // Update ranks in Firestore using batch writes
    const batch = db.batch();
    let currentRank = 0;
    attempts.forEach((att, idx) => {
      const rank = idx + 1;
      const docRef = db.collection('attempts').doc(att.id);
      if (att.id === attemptId) {
        currentRank = rank;
        batch.update(docRef, {
          totalScore,
          sectionScores,
          percentage,
          passed,
          rank
        });
      } else if (att.rank !== rank) {
        batch.update(docRef, { rank });
      }
    });

    await batch.commit();
    console.log(`Successfully graded attempt ${attemptId}: score=${totalScore}/${totalMarks}, rank=${currentRank}`);
  } catch (err) {
    console.error(`Error in gradeAndRankAttempt for attempt ${attemptId}:`, err);
  }
});

/**
 * Triggered when an attempt is updated. Re-ranks all attempts for the test
 * only if a student's score has changed.
 */
exports.reRankOnUpdate = functions.firestore.document('attempts/{attemptId}').onUpdate(async (change, context) => {
  const before = change.before.data();
  const after = change.after.data();
  const attemptId = context.params.attemptId;
  const testId = after.testId;

  if (before.totalScore === after.totalScore || !testId) {
    return null;
  }

  try {
    const attemptsSnap = await db.collection('attempts').where('testId', '==', testId).get();
    const attempts = [];
    attemptsSnap.forEach(d => {
      attempts.push({ id: d.id, ...d.data() });
    });

    attempts.sort((a, b) => {
      const scoreA = Number(a.totalScore) || 0;
      const scoreB = Number(b.totalScore) || 0;
      if (scoreB !== scoreA) {
        return scoreB - scoreA;
      }
      const timeA = a.submittedAt ? (a.submittedAt.toDate ? a.submittedAt.toDate().getTime() : new Date(a.submittedAt).getTime()) : 0;
      const timeB = b.submittedAt ? (b.submittedAt.toDate ? b.submittedAt.toDate().getTime() : new Date(b.submittedAt).getTime()) : 0;
      if (timeA !== timeB) {
        return timeA - timeB;
      }
      return a.id.localeCompare(b.id);
    });

    const batch = db.batch();
    attempts.forEach((att, idx) => {
      const rank = idx + 1;
      if (att.rank !== rank) {
        batch.update(db.collection('attempts').doc(att.id), { rank });
      }
    });

    await batch.commit();
    console.log(`Successfully re-ranked attempts for test ${testId} after score change on ${attemptId}`);
  } catch (err) {
    console.error(`Error in reRankOnUpdate for test ${testId}:`, err);
  }
  return null;
});

/**
 * Triggered on updating payment document.
 * Detects transition of status to 'approved' and performs:
 *  - Activates Student results review (enables details, adds answer keys)
 *  - Activates Instructor account and subscription plan
 *  - Deletes receipt image from Firebase Storage to save space, and clears screenshotUrl
 */
exports.onPaymentUpdated = functions.firestore.document('payments/{paymentId}').onUpdate(async (change, context) => {
  const before = change.before.data();
  const after = change.after.data();
  const paymentId = context.params.paymentId;

  // Run only when transition to approved status occurs
  if (after.status !== 'approved' || before.status === 'approved') {
    return null;
  }

  const payment = after;
  try {
    const isStudentPayment = payment.paymentType === 'student_result' || String(payment.plan).toLowerCase() === 'student_result';

    if (isStudentPayment) {
      let attemptId = (payment.attemptId || '').trim();
      const testId = (payment.testId || '').trim().toUpperCase();

      // Fallback lookup if attempt ID wasn't directly supplied
      if (!attemptId && testId) {
        const attemptsSnap = await db.collection('attempts').where('testId', '==', testId).limit(50).get();
        const name = (payment.studentName || '').trim().toLowerCase();
        let matchedAttemptDoc = null;
        attemptsSnap.forEach(d => {
          const dName = (d.data().studentName || '').trim().toLowerCase();
          if (!name || dName === name) {
            matchedAttemptDoc = d;
          }
        });
        if (!matchedAttemptDoc && !attemptsSnap.empty) {
          matchedAttemptDoc = attemptsSnap.docs[0];
        }
        if (matchedAttemptDoc) {
          attemptId = matchedAttemptDoc.id;
        }
      }

      if (attemptId) {
        // Build answer key from answers collection or full test document
        const correctAnswers = {};
        if (testId) {
          const keyDoc = await db.collection('tests_answerkeys').doc(testId).get();
          if (keyDoc.exists && keyDoc.data().answers) {
            Object.assign(correctAnswers, keyDoc.data().answers);
          } else {
            const testDoc = await db.collection('tests').doc(testId).get();
            if (testDoc.exists && testDoc.data().sections) {
              testDoc.data().sections.forEach(sec => {
                if (sec.questions) {
                  sec.questions.forEach(q => {
                    if (q.id && q.correctAnswer) {
                      correctAnswers[q.id] = String(q.correctAnswer).trim().toUpperCase().charAt(0);
                    }
                  });
                }
              });
            }
          }
        }

        await db.collection('attempts').doc(attemptId).update({
          hasPaidForDetails: true,
          ...(Object.keys(correctAnswers).length > 0 ? { correctAnswers } : {})
        });
        console.log(`Payment approved for student attempt ${attemptId}. Detailed review unlocked.`);
      } else {
        console.warn(`Could not map payment ${paymentId} to a student attempt`);
      }
    } else {
      // Instructor Subscription payment
      const email = (payment.userEmail || '').trim().toLowerCase();
      if (email) {
        const usersSnap = await db.collection('users').where('email', '==', email).get();
        let targetUserDoc = null;
        usersSnap.forEach(d => {
          targetUserDoc = d;
        });

        if (targetUserDoc) {
          const instructorId = targetUserDoc.id;
          const userData = targetUserDoc.data();
          const planKey = (payment.plan || 'monthly').trim().toLowerCase();

          // Normalize subscription tier name
          let tier = 'basic';
          if (['pro', 'yearly', 'sixmonths'].includes(planKey)) {
            tier = 'pro';
          } else if (planKey === 'institute') {
            tier = 'institute';
          } else if (planKey === 'weekly' || planKey === 'monthly') {
            tier = 'basic';
          } else {
            tier = planKey;
          }

          const updates = {
            approvalStatus: 'approved',
            subscriptionStatus: 'active',
            subscriptionTier: tier
          };

          // Provision academy if it is an Institute plan
          if (tier === 'institute') {
            let instituteId = userData.instituteId || '';
            if (!instituteId) {
              const instituteRef = db.collection('institutes').doc();
              instituteId = instituteRef.id;
              const instituteName = `${userData.name || 'Academy'} Institute`;
              await instituteRef.set({
                instituteId,
                name: instituteName,
                ownerUid: instructorId,
                ownerEmail: email,
                createdAt: admin.firestore.FieldValue.serverTimestamp()
              });
            }
            const memberRef = db.collection('institutes').doc(instituteId).collection('members').doc(instructorId);
            const memberDoc = await memberRef.get();
            if (!memberDoc.exists) {
              await memberRef.set({
                uid: instructorId,
                email: userData.email || email,
                name: userData.name || '',
                role: 'owner',
                status: 'active',
                addedAt: admin.firestore.FieldValue.serverTimestamp()
              });
            }
            updates.instituteId = instituteId;
            updates.instituteRole = 'owner';
          }

          await db.collection('users').doc(instructorId).update(updates);

          // Calculate period days
          let days = 30;
          if (planKey === 'weekly') days = 7;
          else if (planKey === 'monthly' || tier === 'basic') days = 30;
          else if (planKey === 'sixmonths') days = 180;
          else if (planKey === 'yearly') days = 365;

          const endDate = new Date();
          endDate.setDate(endDate.getDate() + days);

          await db.collection('subscriptions').doc(instructorId).set({
            instructorId,
            plan: tier,
            startDate: admin.firestore.Timestamp.now(),
            endDate: admin.firestore.Timestamp.fromDate(endDate),
            isActive: true
          }, { merge: true });

          console.log(`Successfully activated Instructor ${email} on tier "${tier}" for ${days} days.`);
        } else {
          console.warn(`No registered user found with email "${email}" to activate.`);
        }
      }
    }

    // 5. Screenshot cleanup (delete from storage if present, and remove base64 payload from document)
    const screenshotUrl = payment.screenshotUrl || '';
    if (screenshotUrl) {
      if (screenshotUrl.startsWith('gs://') || screenshotUrl.includes('firebasestorage.googleapis.com')) {
        try {
          let fileRef = null;
          if (screenshotUrl.startsWith('gs://')) {
            fileRef = admin.storage().bucket().file(screenshotUrl.substring(5).split('/').slice(1).join('/'));
          } else {
            // Extract the Storage path from the public download URL
            const decodedUrl = decodeURIComponent(screenshotUrl);
            const pathParts = decodedUrl.split('/o/');
            if (pathParts.length > 1) {
              const fullPath = pathParts[1].split('?')[0];
              fileRef = admin.storage().bucket().file(fullPath);
            }
          }
          if (fileRef) {
            await fileRef.delete();
            console.log(`Deleted receipt image from Storage: ${screenshotUrl}`);
          }
        } catch (err) {
          console.error(`Failed to delete screenshot image from storage:`, err);
        }
      }
      // Remove base64 or URL string from payment document to save space
      await db.collection('payments').doc(paymentId).update({
        screenshotUrl: ''
      });
    }
  } catch (err) {
    console.error(`Error in onPaymentUpdated trigger for payment ${paymentId}:`, err);
  }
  return null;
});

/**
 * Scheduled cron job running daily at midnight PKT.
 * Deactivates expired subscriptions and flags instructors as inactive.
 */
exports.expireSubscriptions = functions.pubsub.schedule('0 0 * * *')
  .timeZone('Asia/Karachi')
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    try {
      const subscriptionsSnap = await db.collection('subscriptions')
        .where('isActive', '==', true)
        .where('endDate', '<', now)
        .get();

      if (subscriptionsSnap.empty) {
        console.log('No expired subscriptions detected today.');
        return null;
      }

      const batch = db.batch();
      subscriptionsSnap.forEach(d => {
        const sub = d.data();
        const instructorId = sub.instructorId;

        // Deactivate subscription
        batch.update(db.collection('subscriptions').doc(d.id), { isActive: false });

        // Set instructor user profile to inactive
        if (instructorId) {
          batch.update(db.collection('users').doc(instructorId), { subscriptionStatus: 'inactive' });
        }
      });

      await batch.commit();
      console.log(`Expired and deactivated ${subscriptionsSnap.size} instructor subscription(s).`);
    } catch (err) {
      console.error('Error executing expireSubscriptions daily schedule:', err);
    }
    return null;
  });

/**
 * Generates a custom token for the authenticated user to enable SSO with WebView.
 */
exports.getCustomToken = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'The function must be called while authenticated.');
  }
  const uid = context.auth.uid;
  try {
    const customToken = await admin.auth().createCustomToken(uid);
    return { token: customToken };
  } catch (error) {
    throw new functions.https.HttpsError('internal', error.message);
  }
});