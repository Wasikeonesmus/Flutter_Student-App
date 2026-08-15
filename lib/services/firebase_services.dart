import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../models/app_models.dart';

class AuthService {
  FirebaseAuth? get _auth {
    try {
      return FirebaseAuth.instance;
    } catch (_) {
      return null;
    }
  }

  User? get currentUser => _auth?.currentUser;
  
  Stream<User?> get authStateChanges {
    try {
      return _auth?.authStateChanges() ?? Stream.value(null);
    } catch (_) {
      return Stream.value(null);
    }
  }

  Future<UserCredential?> signInWithEmail(String email, String password) async {
    final auth = _auth;
    if (auth == null) return null;
    return await auth.signInWithEmailAndPassword(email: email.trim(), password: password);
  }

  Future<UserCredential?> signUpWithEmail(String email, String password) async {
    final auth = _auth;
    if (auth == null) return null;
    return await auth.createUserWithEmailAndPassword(email: email.trim(), password: password);
  }

  Future<UserCredential?> signInAnonymously() async {
    final auth = _auth;
    if (auth == null) return null;
    return await auth.signInAnonymously();
  }

  Future<void> signOut() async {
    await _auth?.signOut();
  }
}

class FirestoreService {
  FirebaseFirestore? get _db {
    try {
      return FirebaseFirestore.instance;
    } catch (_) {
      return null;
    }
  }

  // ─── USER & INSTRUCTOR METHODS ─────────────────────────────────────────────

  Future<UserModel?> getUser(String uid) async {
    final db = _db;
    if (db == null) return null;
    try {
      final doc = await db.collection('users').doc(uid).get();
      if (doc.exists && doc.data() != null) {
        return UserModel.fromMap(doc.data()!, doc.id);
      }
    } catch (_) {}
    return null;
  }

  Stream<UserModel?> streamUser(String uid) {
    final db = _db;
    if (db == null) return Stream.value(null);
    try {
      return db.collection('users').doc(uid).snapshots().map((doc) {
        if (doc.exists && doc.data() != null) {
          return UserModel.fromMap(doc.data()!, doc.id);
        }
        return null;
      });
    } catch (_) {
      return Stream.value(null);
    }
  }

  Future<void> saveUser(UserModel user) async {
    final db = _db;
    if (db == null) return;
    try {
      await db.collection('users').doc(user.uid).set(user.toMap(), SetOptions(merge: true));
    } catch (_) {}
  }

  Stream<List<UserModel>> streamPendingInstructors() {
    final db = _db;
    if (db == null) return Stream.value([]);
    try {
      return db
          .collection('users')
          .where('role', isEqualTo: 'instructor')
          .where('approvalStatus', isEqualTo: 'pending')
          .snapshots()
          .map((snapshot) => snapshot.docs.map((doc) => UserModel.fromMap(doc.data(), doc.id)).toList());
    } catch (_) {
      return Stream.value([]);
    }
  }

  Stream<List<UserModel>> streamAllInstructors() {
    final db = _db;
    if (db == null) return Stream.value([]);
    try {
      return db
          .collection('users')
          .where('role', isEqualTo: 'instructor')
          .snapshots()
          .map((snapshot) => snapshot.docs.map((doc) => UserModel.fromMap(doc.data(), doc.id)).toList());
    } catch (_) {
      return Stream.value([]);
    }
  }

  Future<void> updateUserApproval(String uid, String status, String tier) async {
    final db = _db;
    if (db == null) return;
    try {
      await db.collection('users').doc(uid).update({
        'approvalStatus': status,
        'subscriptionStatus': status == 'approved' ? 'active' : 'inactive',
        'subscriptionTier': tier,
      });
    } catch (_) {}
  }

  // ─── TEST METHODS ──────────────────────────────────────────────────────────

  Future<String> createTest(TestModel test) async {
    final db = _db;
    final testId = 'test_${DateTime.now().millisecondsSinceEpoch}';
    if (db == null) return testId;

    try {
      final docRef = db.collection('tests').doc(testId);
      final newTest = TestModel(
        testId: docRef.id,
        title: test.title,
        description: test.description,
        instructorUid: test.instructorUid,
        instructorName: test.instructorName,
        durationMinutes: test.durationMinutes,
        positiveMarks: test.positiveMarks,
        negativeMarks: test.negativeMarks,
        shuffleQuestions: test.shuffleQuestions,
        shuffleOptions: test.shuffleOptions,
        status: test.status,
        sections: test.sections,
        createdAt: DateTime.now(),
      );

      await docRef.set(newTest.toMap());

      await db.collection('tests_public').doc(docRef.id).set({
        'testId': docRef.id,
        'title': test.title,
        'instructorName': test.instructorName,
        'durationMinutes': test.durationMinutes,
        'totalQuestions': test.totalQuestions,
        'status': test.status,
      });
    } catch (_) {}

    return testId;
  }

  Stream<List<TestModel>> streamInstructorTests(String instructorUid) {
    final db = _db;
    if (db == null) return Stream.value([]);
    try {
      return db
          .collection('tests')
          .where('instructorUid', isEqualTo: instructorUid)
          .snapshots()
          .map((snap) => snap.docs.map((doc) => TestModel.fromMap(doc.data(), doc.id)).toList());
    } catch (_) {
      return Stream.value([]);
    }
  }

  Future<TestModel?> getTest(String testId) async {
    final db = _db;
    if (db != null) {
      try {
        final doc = await db.collection('tests').doc(testId).get();
        if (doc.exists && doc.data() != null) {
          return TestModel.fromMap(doc.data()!, doc.id);
        }
      } catch (_) {}
    }

    // Demo Test Fallback if Firestore test document is not found
    return TestModel(
      testId: testId.isEmpty ? 'DEMO_TEST_01' : testId,
      title: 'Sample Computer Science Examination 2026',
      description: 'Comprehensive evaluation covering Algorithms, Data Structures, and Software Architecture.',
      instructorUid: 'inst_01',
      instructorName: 'Prof. Alan Turing',
      durationMinutes: 45,
      positiveMarks: 1.0,
      negativeMarks: 0.25,
      sections: [
        SectionModel(
          id: 'sec_1',
          title: 'Algorithms & Data Structures',
          questions: [
            QuestionModel(
              id: 'q_1',
              text: 'What is the average time complexity of QuickSort?',
              options: ['O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)'],
              correctOption: 'B',
              explanation: 'QuickSort runs in O(n log n) average time complexity using divide and conquer.',
            ),
            QuestionModel(
              id: 'q_2',
              text: 'Which data structure follows the First-In, First-Out (FIFO) principle?',
              options: ['Stack', 'Queue', 'Tree', 'Graph'],
              correctOption: 'B',
              explanation: 'Queues maintain FIFO ordering for elements.',
            ),
            QuestionModel(
              id: 'q_3',
              text: 'In Flutter, which widget is used to render responsive cross-axis lists without unconstrained assertions?',
              options: ['Column', 'Row', 'Wrap', 'ListView'],
              correctOption: 'C',
              explanation: 'Wrap handles child wrapping dynamically across responsive break points.',
            ),
          ],
        ),
      ],
    );
  }

  // ─── EXAM SUBMISSION METHODS ───────────────────────────────────────────────

  Future<String> submitExamResult(StudentResultModel result) async {
    final db = _db;
    final resultId = 'res_${DateTime.now().millisecondsSinceEpoch}';
    if (db == null) return resultId;

    try {
      final docRef = db.collection('results').doc(resultId);
      final newResult = StudentResultModel(
        resultId: docRef.id,
        testId: result.testId,
        testTitle: result.testTitle,
        studentName: result.studentName,
        rollNumber: result.rollNumber,
        district: result.district,
        totalScore: result.totalScore,
        totalQuestions: result.totalQuestions,
        correctAnswers: result.correctAnswers,
        wrongAnswers: result.wrongAnswers,
        unattempted: result.unattempted,
        answers: result.answers,
        submittedAt: DateTime.now(),
      );

      await docRef.set(newResult.toMap());
    } catch (_) {}

    return resultId;
  }

  Stream<List<StudentResultModel>> streamTestResults(String testId) {
    final db = _db;
    if (db == null) return Stream.value([]);
    try {
      return db
          .collection('results')
          .where('testId', isEqualTo: testId)
          .snapshots()
          .map((snap) => snap.docs.map((doc) => StudentResultModel.fromMap(doc.data(), doc.id)).toList());
    } catch (_) {
      return Stream.value([]);
    }
  }
}
