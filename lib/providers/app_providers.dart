import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/app_models.dart';
import '../services/database_service.dart';
import '../services/ai_service.dart';

// Services
final databaseServiceProvider = Provider((ref) => DatabaseService());
final aiServiceProvider = Provider((ref) => AiService());

// Auth State Stream Provider
final authStateProvider = StreamProvider<LocalAuthUser?>((ref) {
  return ref.watch(databaseServiceProvider).authStateChanges;
});

// Current UserModel Stream Provider
final currentUserModelProvider = StreamProvider<UserModel?>((ref) {
  final authState = ref.watch(authStateProvider);
  return authState.when(
    data: (user) {
      if (user == null) return Stream.value(null);
      return ref.watch(databaseServiceProvider).streamUser(user.uid);
    },
    loading: () => Stream.value(null),
    error: (_, __) => Stream.value(null),
  );
});

class TestsNotifier extends StateNotifier<List<TestModel>> {
  final DatabaseService _db;

  TestsNotifier(this._db) : super(_db.allTests);

  void refresh() {
    state = List<TestModel>.from(_db.allTests);
  }

  Future<String> createTest(TestModel test) async {
    final testId = await _db.createTest(test);
    state = List<TestModel>.from(_db.allTests);
    return testId;
  }
}

final testsProvider = StateNotifierProvider<TestsNotifier, List<TestModel>>((ref) {
  return TestsNotifier(ref.watch(databaseServiceProvider));
});

// Student Test Results Stream Provider
final testResultsProvider = StreamProvider.family<List<StudentResultModel>, String>((ref, testId) {
  return ref.watch(databaseServiceProvider).streamTestResults(testId);
});

// Admin Pending Instructors Stream Provider
final pendingInstructorsProvider = StreamProvider<List<UserModel>>((ref) {
  return ref.watch(databaseServiceProvider).streamPendingInstructors();
});

// Admin All Instructors Stream Provider
final allInstructorsProvider = StreamProvider<List<UserModel>>((ref) {
  return ref.watch(databaseServiceProvider).streamAllInstructors();
});

// Active Exam State Notifier (For Students taking an exam)
class ExamSessionState {
  final TestModel? test;
  final String studentName;
  final String rollNumber;
  final String district;
  final Map<String, String> answers; // questionId -> option
  final Set<String> markedForReview;
  final int remainingSeconds;
  final bool isSubmitted;

  ExamSessionState({
    this.test,
    this.studentName = '',
    this.rollNumber = '',
    this.district = '',
    this.answers = const {},
    this.markedForReview = const {},
    this.remainingSeconds = 0,
    this.isSubmitted = false,
  });

  ExamSessionState copyWith({
    TestModel? test,
    String? studentName,
    String? rollNumber,
    String? district,
    Map<String, String>? answers,
    Set<String>? markedForReview,
    int? remainingSeconds,
    bool? isSubmitted,
  }) {
    return ExamSessionState(
      test: test ?? this.test,
      studentName: studentName ?? this.studentName,
      rollNumber: rollNumber ?? this.rollNumber,
      district: district ?? this.district,
      answers: answers ?? this.answers,
      markedForReview: markedForReview ?? this.markedForReview,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      isSubmitted: isSubmitted ?? this.isSubmitted,
    );
  }
}

class ExamSessionNotifier extends StateNotifier<ExamSessionState> {
  ExamSessionNotifier() : super(ExamSessionState());

  void startSession({
    required TestModel test,
    required String studentName,
    required String rollNumber,
    required String district,
  }) {
    state = ExamSessionState(
      test: test,
      studentName: studentName,
      rollNumber: rollNumber,
      district: district,
      answers: {},
      markedForReview: {},
      remainingSeconds: test.durationMinutes * 60,
      isSubmitted: false,
    );
  }

  void selectAnswer(String questionId, String option) {
    final updated = Map<String, String>.from(state.answers);
    updated[questionId] = option;
    state = state.copyWith(answers: updated);
  }

  void toggleMarkForReview(String questionId) {
    final updated = Set<String>.from(state.markedForReview);
    if (updated.contains(questionId)) {
      updated.remove(questionId);
    } else {
      updated.add(questionId);
    }
    state = state.copyWith(markedForReview: updated);
  }

  void decrementTimer() {
    if (state.remainingSeconds > 0) {
      state = state.copyWith(remainingSeconds: state.remainingSeconds - 1);
    }
  }

  void markSubmitted() {
    state = state.copyWith(isSubmitted: true);
  }
}

final examSessionProvider = StateNotifierProvider<ExamSessionNotifier, ExamSessionState>((ref) {
  return ExamSessionNotifier();
});
