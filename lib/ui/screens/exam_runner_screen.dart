import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';

class ExamRunnerScreen extends ConsumerStatefulWidget {
  const ExamRunnerScreen({super.key});

  @override
  ConsumerState<ExamRunnerScreen> createState() => _ExamRunnerScreenState();
}

class _ExamRunnerScreenState extends ConsumerState<ExamRunnerScreen> {
  Timer? _timer;
  int _currentSectionIndex = 0;
  int _currentQuestionIndex = 0;

  @override
  void initState() {
    super.initState();
    _startTimer();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final session = ref.read(examSessionProvider);
      if (session.remainingSeconds <= 1) {
        _timer?.cancel();
        _submitExam();
      } else {
        ref.read(examSessionProvider.notifier).decrementTimer();
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _formatTimer(int totalSeconds) {
    final minutes = totalSeconds ~/ 60;
    final seconds = totalSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  Future<void> _submitExam() async {
    _timer?.cancel();
    final session = ref.read(examSessionProvider);
    final test = session.test;

    if (test == null) return;

    double totalScore = 0.0;
    int correctCount = 0;
    int wrongCount = 0;
    int unattemptedCount = 0;
    int totalQuestions = 0;

    for (var sec in test.sections) {
      for (var q in sec.questions) {
        totalQuestions++;
        final selected = session.answers[q.id];
        if (selected == null || selected.isEmpty) {
          unattemptedCount++;
        } else if (selected.toUpperCase() == q.correctOption.toUpperCase()) {
          correctCount++;
          totalScore += test.positiveMarks;
        } else {
          wrongCount++;
          totalScore -= test.negativeMarks;
        }
      }
    }

    final result = StudentResultModel(
      resultId: '',
      testId: test.testId,
      testTitle: test.title,
      studentName: session.studentName,
      rollNumber: session.rollNumber,
      district: session.district,
      totalScore: totalScore < 0 ? 0 : totalScore,
      totalQuestions: totalQuestions,
      correctAnswers: correctCount,
      wrongAnswers: wrongCount,
      unattempted: unattemptedCount,
      answers: session.answers,
    );

    try {
      final dbService = ref.read(databaseServiceProvider);
      await dbService.submitExamResult(result);
      ref.read(examSessionProvider.notifier).markSubmitted();
      if (mounted) {
        context.go('/exam-results');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Submission error: ${e.toString()}'),
            backgroundColor: Colors.red.shade700,
          ),
        );
        // Still navigate to results even on error
        ref.read(examSessionProvider.notifier).markSubmitted();
        context.go('/exam-results');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(examSessionProvider);
    final test = session.test;

    if (test == null) {
      return Scaffold(
        body: Center(
          child: ElevatedButton(
            onPressed: () => context.go('/'),
            child: const Text('No active exam session. Return Home.'),
          ),
        ),
      );
    }

    final isDesktop = MediaQuery.of(context).size.width > 900;
    final currentSection = test.sections.isNotEmpty ? test.sections[_currentSectionIndex] : null;
    final currentQuestion = (currentSection != null && currentSection.questions.isNotEmpty)
        ? currentSection.questions[_currentQuestionIndex]
        : null;

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      appBar: AppBar(
        backgroundColor: AppTheme.darkCard,
        elevation: 0,
        automaticallyImplyLeading: false,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              test.title,
              style: GoogleFonts.outfit(fontWeight: FontWeight.bold, fontSize: 16, color: Colors.white),
            ),
            Text(
              'Student: ${session.studentName} (${session.rollNumber})',
              style: GoogleFonts.inter(fontSize: 12, color: AppTheme.darkTextSecondary),
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.symmetric(vertical: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            decoration: BoxDecoration(
              color: session.remainingSeconds < 300
                  ? AppTheme.dangerColor.withValues(alpha: 0.2)
                  : AppTheme.primaryColor.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: session.remainingSeconds < 300 ? AppTheme.dangerColor : AppTheme.primaryColor,
              ),
            ),
            child: Row(
              children: [
                Icon(
                  LucideIcons.clock,
                  size: 16,
                  color: session.remainingSeconds < 300 ? AppTheme.dangerColor : AppTheme.primaryColor,
                ),
                const SizedBox(width: 8),
                Text(
                  _formatTimer(session.remainingSeconds),
                  style: GoogleFonts.outfit(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: session.remainingSeconds < 300 ? AppTheme.dangerColor : Colors.white,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.dangerColor),
            icon: const Icon(LucideIcons.send, size: 16),
            label: const Text('Submit Exam'),
            onPressed: () {
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  backgroundColor: AppTheme.darkCard,
                  title: const Text('Submit Examination?', style: TextStyle(color: Colors.white)),
                  content: const Text(
                    'Are you sure you want to finish and submit your answers?',
                    style: TextStyle(color: AppTheme.darkTextSecondary),
                  ),
                  actions: [
                    TextButton(
                      child: const Text('Cancel', style: TextStyle(color: AppTheme.darkTextSecondary)),
                      onPressed: () => Navigator.pop(ctx),
                    ),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(backgroundColor: AppTheme.dangerColor),
                      onPressed: () {
                        Navigator.pop(ctx);
                        _submitExam();
                      },
                      child: const Text('Yes, Submit Now'),
                    ),
                  ],
                ),
              );
            },
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: Row(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (test.sections.length > 1) ...[
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: test.sections.asMap().entries.map((entry) {
                          final idx = entry.key;
                          final sec = entry.value;
                          final isSelected = idx == _currentSectionIndex;
                          return Padding(
                            padding: const EdgeInsets.only(right: 8),
                            child: ChoiceChip(
                              label: Text(sec.title),
                              selected: isSelected,
                              selectedColor: AppTheme.primaryColor,
                              onSelected: (_) {
                                setState(() {
                                  _currentSectionIndex = idx;
                                  _currentQuestionIndex = 0;
                                });
                              },
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                  if (currentQuestion != null) ...[
                    Card(
                      color: AppTheme.darkCard,
                      child: Padding(
                        padding: const EdgeInsets.all(28),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  'Question ${_currentQuestionIndex + 1} of ${currentSection?.questions.length ?? 0}',
                                  style: GoogleFonts.outfit(fontSize: 16, color: AppTheme.secondaryColor, fontWeight: FontWeight.bold),
                                ),
                                IconButton(
                                  icon: Icon(
                                    LucideIcons.bookmark,
                                    color: session.markedForReview.contains(currentQuestion.id)
                                        ? AppTheme.warningColor
                                        : AppTheme.darkTextSecondary,
                                  ),
                                  tooltip: 'Mark for Review',
                                  onPressed: () {
                                    ref.read(examSessionProvider.notifier).toggleMarkForReview(currentQuestion.id);
                                  },
                                ),
                              ],
                            ),
                            const SizedBox(height: 16),
                            Text(
                              currentQuestion.text,
                              style: GoogleFonts.inter(fontSize: 18, fontWeight: FontWeight.w600, color: Colors.white, height: 1.4),
                            ),
                            const SizedBox(height: 28),
                            ...currentQuestion.options.asMap().entries.map((entry) {
                              final optionIndex = entry.key;
                              final optionText = entry.value;
                              final optionLetter = String.fromCharCode(65 + optionIndex);
                              final isSelected = session.answers[currentQuestion.id] == optionLetter;

                              return Padding(
                                padding: const EdgeInsets.only(bottom: 12),
                                child: InkWell(
                                  onTap: () {
                                    ref.read(examSessionProvider.notifier).selectAnswer(currentQuestion.id, optionLetter);
                                  },
                                  borderRadius: BorderRadius.circular(12),
                                  child: Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                                    decoration: BoxDecoration(
                                      color: isSelected ? AppTheme.primaryColor.withValues(alpha: 0.15) : const Color(0xFF0F172A),
                                      borderRadius: BorderRadius.circular(12),
                                      border: Border.all(
                                        color: isSelected ? AppTheme.primaryColor : AppTheme.darkCardBorder,
                                        width: isSelected ? 2 : 1,
                                      ),
                                    ),
                                    child: Row(
                                      children: [
                                        Container(
                                          width: 32,
                                          height: 32,
                                          decoration: BoxDecoration(
                                            color: isSelected ? AppTheme.primaryColor : Colors.transparent,
                                            shape: BoxShape.circle,
                                            border: Border.all(
                                              color: isSelected ? AppTheme.primaryColor : AppTheme.darkTextSecondary,
                                            ),
                                          ),
                                          child: Center(
                                            child: Text(
                                              optionLetter,
                                              style: TextStyle(
                                                color: isSelected ? Colors.white : AppTheme.darkTextSecondary,
                                                fontWeight: FontWeight.bold,
                                              ),
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 16),
                                        Expanded(
                                          child: Text(
                                            optionText,
                                            style: GoogleFonts.inter(
                                              fontSize: 15,
                                              color: isSelected ? Colors.white : AppTheme.darkTextPrimary,
                                            ),
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                              );
                            }),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        OutlinedButton.icon(
                          icon: const Icon(LucideIcons.chevronLeft, size: 18),
                          label: const Text('Previous'),
                          onPressed: _currentQuestionIndex > 0
                              ? () => setState(() => _currentQuestionIndex--)
                              : null,
                        ),
                        ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(
                            backgroundColor: (currentSection != null && _currentQuestionIndex == currentSection.questions.length - 1)
                                ? AppTheme.accentColor
                                : AppTheme.primaryColor,
                          ),
                          icon: Icon(
                            (currentSection != null && _currentQuestionIndex == currentSection.questions.length - 1)
                                ? LucideIcons.send
                                : LucideIcons.chevronRight,
                            size: 18,
                          ),
                          label: Text(
                            (currentSection != null && _currentQuestionIndex == currentSection.questions.length - 1)
                                ? 'Finish & Submit'
                                : 'Next Question',
                          ),
                          onPressed: () {
                            final isLastQuestion = (currentSection != null && _currentQuestionIndex == currentSection.questions.length - 1);
                            if (isLastQuestion) {
                              showDialog(
                                context: context,
                                builder: (ctx) => AlertDialog(
                                  backgroundColor: AppTheme.darkCard,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                    side: const BorderSide(color: AppTheme.darkCardBorder),
                                  ),
                                  title: Row(
                                    children: [
                                      const Icon(LucideIcons.checkCircle2, color: AppTheme.accentColor),
                                      const SizedBox(width: 10),
                                      Text(
                                        'End of Exam Reached',
                                        style: GoogleFonts.outfit(color: Colors.white, fontWeight: FontWeight.bold),
                                      ),
                                    ],
                                  ),
                                  content: Text(
                                    'You have reached the final question. Would you like to finish and submit your examination now?',
                                    style: GoogleFonts.inter(color: AppTheme.darkTextSecondary),
                                  ),
                                  actions: [
                                    TextButton(
                                      child: const Text('Review Answers', style: TextStyle(color: AppTheme.darkTextSecondary)),
                                      onPressed: () => Navigator.pop(ctx),
                                    ),
                                    ElevatedButton.icon(
                                      style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentColor),
                                      icon: const Icon(LucideIcons.send, size: 16),
                                      label: const Text('Submit Exam Now'),
                                      onPressed: () {
                                        Navigator.pop(ctx);
                                        _submitExam();
                                      },
                                    ),
                                  ],
                                ),
                              );
                            } else {
                              setState(() => _currentQuestionIndex++);
                            }
                          },
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (isDesktop && currentSection != null)
            Container(
              width: 280,
              color: AppTheme.darkCard,
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Question Palette',
                    style: GoogleFonts.outfit(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                  const SizedBox(height: 16),
                  Expanded(
                    child: GridView.builder(
                      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                        crossAxisCount: 4,
                        crossAxisSpacing: 8,
                        mainAxisSpacing: 8,
                      ),
                      itemCount: currentSection.questions.length,
                      itemBuilder: (context, idx) {
                        final q = currentSection.questions[idx];
                        final isAnswered = session.answers.containsKey(q.id);
                        final isCurrent = idx == _currentQuestionIndex;
                        final isMarked = session.markedForReview.contains(q.id);

                        Color btnColor = const Color(0xFF0F172A);
                        if (isCurrent) {
                          btnColor = AppTheme.primaryColor;
                        } else if (isMarked) {
                          btnColor = AppTheme.warningColor;
                        } else if (isAnswered) {
                          btnColor = AppTheme.accentColor;
                        }

                        return InkWell(
                          onTap: () => setState(() => _currentQuestionIndex = idx),
                          borderRadius: BorderRadius.circular(8),
                          child: Container(
                            decoration: BoxDecoration(
                              color: btnColor,
                              borderRadius: BorderRadius.circular(8),
                              border: Border.all(color: AppTheme.darkCardBorder),
                            ),
                            child: Center(
                              child: Text(
                                '${idx + 1}',
                                style: GoogleFonts.inter(
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
