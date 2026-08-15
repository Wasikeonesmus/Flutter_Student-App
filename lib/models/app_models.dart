import 'package:cloud_firestore/cloud_firestore.dart';

class UserModel {
  final String uid;
  final String email;
  final String name;
  final String role; // 'superadmin' | 'instructor'
  final String approvalStatus; // 'pending' | 'approved' | 'suspended'
  final String subscriptionStatus; // 'active' | 'inactive'
  final String subscriptionTier; // 'basic' | 'pro' | 'institute'
  final String instituteId;
  final String instituteRole;
  final DateTime? createdAt;
  final String brandingLogoUrl;
  final String brandingResultsTitle;
  final String brandingConductedBy;

  UserModel({
    required this.uid,
    required this.email,
    required this.name,
    required this.role,
    this.approvalStatus = 'pending',
    this.subscriptionStatus = 'inactive',
    this.subscriptionTier = '',
    this.instituteId = '',
    this.instituteRole = '',
    this.createdAt,
    this.brandingLogoUrl = '',
    this.brandingResultsTitle = '',
    this.brandingConductedBy = '',
  });

  bool get isSuperAdmin => role == 'superadmin';
  bool get isApproved => isSuperAdmin || approvalStatus == 'approved';
  bool get hasActiveSubscription => isSuperAdmin || subscriptionStatus == 'active';

  factory UserModel.fromMap(Map<String, dynamic> map, String docId) {
    return UserModel(
      uid: docId,
      email: map['email'] ?? '',
      name: map['name'] ?? '',
      role: map['role'] ?? '',
      approvalStatus: map['approvalStatus'] ?? 'pending',
      subscriptionStatus: map['subscriptionStatus'] ?? 'inactive',
      subscriptionTier: map['subscriptionTier'] ?? '',
      instituteId: map['instituteId'] ?? '',
      instituteRole: map['instituteRole'] ?? '',
      createdAt: map['createdAt'] != null
          ? (map['createdAt'] as Timestamp).toDate()
          : null,
      brandingLogoUrl: map['brandingLogoUrl'] ?? '',
      brandingResultsTitle: map['brandingResultsTitle'] ?? '',
      brandingConductedBy: map['brandingConductedBy'] ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'email': email,
      'name': name,
      'role': role,
      'approvalStatus': approvalStatus,
      'subscriptionStatus': subscriptionStatus,
      'subscriptionTier': subscriptionTier,
      'instituteId': instituteId,
      'instituteRole': instituteRole,
      'createdAt': createdAt != null ? Timestamp.fromDate(createdAt!) : FieldValue.serverTimestamp(),
      'brandingLogoUrl': brandingLogoUrl,
      'brandingResultsTitle': brandingResultsTitle,
      'brandingConductedBy': brandingConductedBy,
    };
  }
}

class QuestionModel {
  final String id;
  final String text;
  final List<String> options;
  final String correctOption; // "A", "B", "C", "D" or index "0", "1", ...
  final String explanation;
  final String section;

  QuestionModel({
    required this.id,
    required this.text,
    required this.options,
    required this.correctOption,
    this.explanation = '',
    this.section = '',
  });

  factory QuestionModel.fromMap(Map<String, dynamic> map, [String? id]) {
    return QuestionModel(
      id: id ?? map['id'] ?? '',
      text: map['text'] ?? '',
      options: List<String>.from(map['options'] ?? []),
      correctOption: map['correctOption']?.toString() ?? 'A',
      explanation: map['explanation'] ?? '',
      section: map['section'] ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'text': text,
      'options': options,
      'correctOption': correctOption,
      'explanation': explanation,
      'section': section,
    };
  }
}

class SectionModel {
  final String id;
  final String title;
  final List<QuestionModel> questions;

  SectionModel({
    required this.id,
    required this.title,
    required this.questions,
  });

  factory SectionModel.fromMap(Map<String, dynamic> map) {
    return SectionModel(
      id: map['id'] ?? '',
      title: map['title'] ?? '',
      questions: (map['questions'] as List? ?? [])
          .map((q) => QuestionModel.fromMap(q as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'title': title,
      'questions': questions.map((q) => q.toMap()).toList(),
    };
  }
}

class TestModel {
  final String testId;
  final String title;
  final String description;
  final String instructorUid;
  final String instructorName;
  final int durationMinutes;
  final double positiveMarks;
  final double negativeMarks;
  final bool shuffleQuestions;
  final bool shuffleOptions;
  final String status; // 'draft' | 'published' | 'ended'
  final List<SectionModel> sections;
  final DateTime? createdAt;
  final DateTime? startTime;
  final DateTime? endTime;

  TestModel({
    required this.testId,
    required this.title,
    this.description = '',
    required this.instructorUid,
    this.instructorName = '',
    required this.durationMinutes,
    this.positiveMarks = 1.0,
    this.negativeMarks = 0.25,
    this.shuffleQuestions = false,
    this.shuffleOptions = false,
    this.status = 'published',
    required this.sections,
    this.createdAt,
    this.startTime,
    this.endTime,
  });

  int get totalQuestions => sections.fold(0, (acc, sec) => acc + sec.questions.length);

  factory TestModel.fromMap(Map<String, dynamic> map, String id) {
    return TestModel(
      testId: id,
      title: map['title'] ?? '',
      description: map['description'] ?? '',
      instructorUid: map['instructorUid'] ?? '',
      instructorName: map['instructorName'] ?? '',
      durationMinutes: map['durationMinutes'] ?? 60,
      positiveMarks: (map['positiveMarks'] as num?)?.toDouble() ?? 1.0,
      negativeMarks: (map['negativeMarks'] as num?)?.toDouble() ?? 0.25,
      shuffleQuestions: map['shuffleQuestions'] ?? false,
      shuffleOptions: map['shuffleOptions'] ?? false,
      status: map['status'] ?? 'published',
      sections: (map['sections'] as List? ?? [])
          .map((s) => SectionModel.fromMap(s as Map<String, dynamic>))
          .toList(),
      createdAt: map['createdAt'] != null ? (map['createdAt'] as Timestamp).toDate() : null,
      startTime: map['startTime'] != null ? (map['startTime'] as Timestamp).toDate() : null,
      endTime: map['endTime'] != null ? (map['endTime'] as Timestamp).toDate() : null,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'title': title,
      'description': description,
      'instructorUid': instructorUid,
      'instructorName': instructorName,
      'durationMinutes': durationMinutes,
      'positiveMarks': positiveMarks,
      'negativeMarks': negativeMarks,
      'shuffleQuestions': shuffleQuestions,
      'shuffleOptions': shuffleOptions,
      'status': status,
      'sections': sections.map((s) => s.toMap()).toList(),
      'createdAt': createdAt != null ? Timestamp.fromDate(createdAt!) : FieldValue.serverTimestamp(),
      'startTime': startTime != null ? Timestamp.fromDate(startTime!) : null,
      'endTime': endTime != null ? Timestamp.fromDate(endTime!) : null,
    };
  }
}

class StudentResultModel {
  final String resultId;
  final String testId;
  final String testTitle;
  final String studentName;
  final String rollNumber;
  final String district;
  final double totalScore;
  final int totalQuestions;
  final int correctAnswers;
  final int wrongAnswers;
  final int unattempted;
  final int rank;
  final Map<String, String> answers; // questionId -> selectedOption
  final DateTime? submittedAt;

  StudentResultModel({
    required this.resultId,
    required this.testId,
    required this.testTitle,
    required this.studentName,
    required this.rollNumber,
    this.district = '',
    required this.totalScore,
    required this.totalQuestions,
    required this.correctAnswers,
    required this.wrongAnswers,
    required this.unattempted,
    this.rank = 0,
    required this.answers,
    this.submittedAt,
  });

  factory StudentResultModel.fromMap(Map<String, dynamic> map, String id) {
    return StudentResultModel(
      resultId: id,
      testId: map['testId'] ?? '',
      testTitle: map['testTitle'] ?? '',
      studentName: map['studentName'] ?? '',
      rollNumber: map['rollNumber'] ?? '',
      district: map['district'] ?? '',
      totalScore: (map['totalScore'] as num?)?.toDouble() ?? 0.0,
      totalQuestions: map['totalQuestions'] ?? 0,
      correctAnswers: map['correctAnswers'] ?? 0,
      wrongAnswers: map['wrongAnswers'] ?? 0,
      unattempted: map['unattempted'] ?? 0,
      rank: map['rank'] ?? 0,
      answers: Map<String, String>.from(map['answers'] ?? {}),
      submittedAt: map['submittedAt'] != null ? (map['submittedAt'] as Timestamp).toDate() : null,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'testId': testId,
      'testTitle': testTitle,
      'studentName': studentName,
      'rollNumber': rollNumber,
      'district': district,
      'totalScore': totalScore,
      'totalQuestions': totalQuestions,
      'correctAnswers': correctAnswers,
      'wrongAnswers': wrongAnswers,
      'unattempted': unattempted,
      'rank': rank,
      'answers': answers,
      'submittedAt': submittedAt != null ? Timestamp.fromDate(submittedAt!) : FieldValue.serverTimestamp(),
    };
  }
}
