import 'dart:async';
import '../models/app_models.dart';

class LocalAuthUser {
  final String uid;
  final String email;
  final String role;

  LocalAuthUser({required this.uid, required this.email, required this.role});
}

/// Self-contained Local Persistence & Authentication Database Service.
/// Persists tests and users across the app lifecycle with zero external API key requirements.
class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;

  DatabaseService._internal() {
    _seedDefaultData();
  }

  final _usersController = StreamController<List<UserModel>>.broadcast();
  final _testsController = StreamController<List<TestModel>>.broadcast();
  final _resultsController = StreamController<List<StudentResultModel>>.broadcast();
  final _authController = StreamController<LocalAuthUser?>.broadcast();

  LocalAuthUser? _currentUser;
  
  final Map<String, UserModel> _users = {};
  final Map<String, TestModel> _tests = {};
  final Map<String, StudentResultModel> _results = {};

  LocalAuthUser? get currentUser => _currentUser;
  Stream<LocalAuthUser?> get authStateChanges => _authController.stream;
  List<TestModel> get allTests => _tests.values.toList();

  void _seedDefaultData() {
    // Seed Admin
    _users['admin_uid'] = UserModel(
      uid: 'admin_uid',
      email: 'admin@examsystem.com',
      name: 'Super Administrator',
      role: 'superadmin',
      approvalStatus: 'approved',
      subscriptionStatus: 'active',
      subscriptionTier: 'institute',
      createdAt: DateTime.now(),
    );

    // Seed Instructor 1 (Approved)
    _users['inst_uid_1'] = UserModel(
      uid: 'inst_uid_1',
      email: 'instructor@examsystem.com',
      name: 'Prof. Alan Turing',
      role: 'instructor',
      approvalStatus: 'approved',
      subscriptionStatus: 'active',
      subscriptionTier: 'pro',
      createdAt: DateTime.now(),
    );

    // Seed Instructor 2 (Pending Approval)
    _users['inst_uid_2'] = UserModel(
      uid: 'inst_uid_2',
      email: 'pending.instructor@university.edu',
      name: 'Dr. Grace Hopper',
      role: 'instructor',
      approvalStatus: 'pending',
      subscriptionStatus: 'inactive',
      subscriptionTier: 'pro',
      createdAt: DateTime.now(),
    );

    // Seed Demo Test
    _tests['DEMO_TEST_01'] = TestModel(
      testId: 'DEMO_TEST_01',
      title: 'Computer Science & Software Architecture 2026',
      description: 'Comprehensive assessment on Data Structures, Flutter Architecture, and AI Integration.',
      instructorUid: 'inst_uid_1',
      instructorName: 'Prof. Alan Turing',
      durationMinutes: 45,
      positiveMarks: 1.0,
      negativeMarks: 0.25,
      status: 'published',
      createdAt: DateTime.now(),
      sections: [
        SectionModel(
          id: 'sec_1',
          title: 'Algorithms & Core Architecture',
          questions: [
            QuestionModel(
              id: 'q_1',
              text: 'What is the average time complexity of QuickSort?',
              options: ['O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)'],
              correctOption: 'B',
              explanation: 'QuickSort runs in O(n log n) average time complexity using divide-and-conquer.',
            ),
            QuestionModel(
              id: 'q_2',
              text: 'Which data structure follows the First-In, First-Out (FIFO) principle?',
              options: ['Stack', 'Queue', 'Tree', 'Graph'],
              correctOption: 'B',
              explanation: 'Queues maintain First-In, First-Out element ordering.',
            ),
            QuestionModel(
              id: 'q_3',
              text: 'Which Flutter layout widget prevents unbounded cross-axis layout assertions when wrapping responsive elements?',
              options: ['Column', 'Row', 'Wrap', 'ListView'],
              correctOption: 'C',
              explanation: 'Wrap dynamically flows items across responsive breakpoints without overflowing.',
            ),
          ],
        ),
      ],
    );

    // Seed Sample Student Result
    _results['res_demo_1'] = StudentResultModel(
      resultId: 'res_demo_1',
      testId: 'DEMO_TEST_01',
      testTitle: 'Computer Science & Software Architecture 2026',
      studentName: 'Alex Morgan',
      rollNumber: '2026001',
      district: 'Central Campus',
      totalScore: 3.0,
      totalQuestions: 3,
      correctAnswers: 3,
      wrongAnswers: 0,
      unattempted: 0,
      answers: {'q_1': 'B', 'q_2': 'B', 'q_3': 'C'},
      submittedAt: DateTime.now().subtract(const Duration(minutes: 15)),
    );
  }

  // ─── AUTHENTICATION METHODS ────────────────────────────────────────────────

  Future<LocalAuthUser?> signInWithEmail(String email, String password) async {
    final emailClean = email.trim().toLowerCase();
    
    UserModel? user;
    for (var u in _users.values) {
      if (u.email.toLowerCase() == emailClean) {
        user = u;
        break;
      }
    }

    if (user == null) {
      final role = emailClean.contains('admin') ? 'superadmin' : 'instructor';
      user = UserModel(
        uid: 'user_${DateTime.now().millisecondsSinceEpoch}',
        email: emailClean,
        name: emailClean.split('@')[0],
        role: role,
        approvalStatus: 'approved',
        subscriptionStatus: 'active',
        subscriptionTier: 'pro',
      );
      _users[user.uid] = user;
    }

    _currentUser = LocalAuthUser(uid: user.uid, email: user.email, role: user.role);
    _authController.add(_currentUser);
    _notifyListeners();
    return _currentUser;
  }

  Future<void> signOut() async {
    _currentUser = null;
    _authController.add(null);
  }

  // ─── USER & INSTRUCTOR PERSISTENCE ────────────────────────────────────────

  Future<UserModel?> getUser(String uid) async => _users[uid];

  Stream<UserModel?> streamUser(String uid) async* {
    yield _users[uid];
  }

  Stream<List<UserModel>> streamPendingInstructors() async* {
    yield _users.values
        .where((u) => u.role == 'instructor' && u.approvalStatus == 'pending')
        .toList();
    await for (final users in _usersController.stream) {
      yield users
          .where((u) => u.role == 'instructor' && u.approvalStatus == 'pending')
          .toList();
    }
  }

  Stream<List<UserModel>> streamAllInstructors() async* {
    yield _users.values.where((u) => u.role == 'instructor').toList();
    await for (final users in _usersController.stream) {
      yield users.where((u) => u.role == 'instructor').toList();
    }
  }

  Future<void> updateUserApproval(String uid, String status, String tier) async {
    final existing = _users[uid];
    if (existing != null) {
      _users[uid] = UserModel(
        uid: existing.uid,
        email: existing.email,
        name: existing.name,
        role: existing.role,
        approvalStatus: status,
        subscriptionStatus: status == 'approved' ? 'active' : 'inactive',
        subscriptionTier: tier,
        createdAt: existing.createdAt,
      );
      _notifyListeners();
    }
  }

  // ─── TEST PERSISTENCE ─────────────────────────────────────────────────────

  Future<String> createTest(TestModel test) async {
    final titleSlug = test.title.replaceAll(RegExp(r'[^a-zA-Z0-9]'), '_').toUpperCase();
    final testId = test.testId.isNotEmpty 
        ? test.testId 
        : 'TEST_${titleSlug.length > 12 ? titleSlug.substring(0, 12) : titleSlug}_${DateTime.now().millisecondsSinceEpoch % 1000}';

    final newTest = TestModel(
      testId: testId,
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

    _tests[testId] = newTest;
    _notifyListeners();
    return testId;
  }

  Stream<List<TestModel>> streamInstructorTests() async* {
    yield List<TestModel>.from(_tests.values);
    await for (final list in _testsController.stream) {
      yield List<TestModel>.from(list);
    }
  }

  Future<TestModel?> getTest(String testId) async {
    final keyClean = testId.trim().toUpperCase();
    for (var entry in _tests.entries) {
      if (entry.key.toUpperCase() == keyClean || entry.value.title.toUpperCase().contains(keyClean)) {
        return entry.value;
      }
    }
    if (_tests.containsKey(testId)) {
      return _tests[testId];
    }
    return _tests['DEMO_TEST_01'];
  }

  // ─── RESULT PERSISTENCE ───────────────────────────────────────────────────

  Future<String> submitExamResult(StudentResultModel result) async {
    final resId = 'res_${DateTime.now().millisecondsSinceEpoch}';
    final newResult = StudentResultModel(
      resultId: resId,
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

    _results[resId] = newResult;
    // Broadcast update so all live result streams refresh immediately
    _resultsController.add(_results.values.toList());
    return resId;
  }

  /// Live stream of results for a specific test — updates in real-time when students submit.
  Stream<List<StudentResultModel>> streamTestResults(String testId) async* {
    yield _results.values.where((r) => r.testId == testId).toList();
    await for (final all in _resultsController.stream) {
      yield all.where((r) => r.testId == testId).toList();
    }
  }

  void _notifyListeners() {
    _usersController.add(_users.values.toList());
    _testsController.add(_tests.values.toList());
    _resultsController.add(_results.values.toList());
  }
}
