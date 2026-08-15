import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';

class ExamResultsScreen extends ConsumerWidget {
  const ExamResultsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(examSessionProvider);
    final test = session.test;

    if (test == null) {
      return Scaffold(
        body: Center(
          child: ElevatedButton(
            onPressed: () => context.go('/'),
            child: const Text('Return Home'),
          ),
        ),
      );
    }

    // Calculate score
    double totalScore = 0.0;
    int correctCount = 0;
    int wrongCount = 0;
    int unattemptedCount = 0;

    for (var sec in test.sections) {
      for (var q in sec.questions) {
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

    final totalQuestions = test.totalQuestions;
    final finalScoreStr = (totalScore < 0 ? 0.0 : totalScore).toStringAsFixed(2);

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 600),
              child: Card(
                color: AppTheme.darkCard,
                elevation: 8,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                  side: const BorderSide(color: AppTheme.darkCardBorder),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(36),
                  child: Column(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(
                          color: AppTheme.accentColor.withOpacity(0.15),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(LucideIcons.checkCircle2, color: AppTheme.accentColor, size: 64),
                      ),
                      const SizedBox(height: 24),
                      Text(
                        'Exam Submitted Successfully!',
                        textAlign: TextAlign.center,
                        style: GoogleFonts.outfit(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Student: ${session.studentName} (${session.rollNumber})',
                        style: GoogleFonts.inter(fontSize: 14, color: AppTheme.darkTextSecondary),
                      ),
                      const SizedBox(height: 28),

                      // Final Score Banner
                      Container(
                        width: double.infinity,
                        padding: const EdgeInsets.all(24),
                        decoration: BoxDecoration(
                          gradient: AppTheme.primaryGradient,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Column(
                          children: [
                            Text(
                              'TOTAL SCORE',
                              style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.white70, letterSpacing: 1.5),
                            ),
                            const SizedBox(height: 8),
                            Text(
                              finalScoreStr,
                              style: GoogleFonts.outfit(fontSize: 48, fontWeight: FontWeight.bold, color: Colors.white),
                            ),
                            Text(
                              'Out of ${totalQuestions * test.positiveMarks} Points',
                              style: GoogleFonts.inter(fontSize: 13, color: Colors.white70),
                            ),
                          ],
                        ),
                      ),

                      const SizedBox(height: 28),

                      // Breakdown Grid
                      Row(
                        children: [
                          Expanded(
                            child: _buildMetricTile(
                              title: 'Correct',
                              value: '$correctCount',
                              icon: LucideIcons.check,
                              color: AppTheme.accentColor,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _buildMetricTile(
                              title: 'Wrong',
                              value: '$wrongCount',
                              icon: LucideIcons.x,
                              color: AppTheme.dangerColor,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: _buildMetricTile(
                              title: 'Skipped',
                              value: '$unattemptedCount',
                              icon: LucideIcons.minus,
                              color: AppTheme.darkTextSecondary,
                            ),
                          ),
                        ],
                      ),

                      const SizedBox(height: 28),

                      // AI Performance Insights Widget
                      FutureBuilder<String>(
                        future: ref.read(aiServiceProvider).generatePerformanceInsight(
                          result: StudentResultModel(
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
                          ),
                          test: test,
                        ),
                        builder: (context, snapshot) {
                          if (snapshot.connectionState == ConnectionState.waiting) {
                            return Container(
                              padding: const EdgeInsets.all(20),
                              decoration: BoxDecoration(
                                color: const Color(0xFF0F172A),
                                borderRadius: BorderRadius.circular(16),
                                border: Border.all(color: AppTheme.secondaryColor.withOpacity(0.3)),
                              ),
                              child: Row(
                                children: [
                                  const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(color: AppTheme.secondaryColor, strokeWidth: 2),
                                  ),
                                  const SizedBox(width: 16),
                                  Text(
                                    'Generating AI Performance Analysis & Study Plan...',
                                    style: GoogleFonts.inter(color: AppTheme.secondaryColor, fontSize: 13),
                                  ),
                                ],
                              ),
                            );
                          }

                          return Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(20),
                            decoration: BoxDecoration(
                              color: const Color(0xFF0F172A),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(color: AppTheme.secondaryColor.withOpacity(0.4)),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    const Icon(LucideIcons.sparkles, color: AppTheme.secondaryColor, size: 18),
                                    const SizedBox(width: 8),
                                    Text(
                                      'AI Diagnostic Analysis & Next Steps',
                                      style: GoogleFonts.outfit(
                                        fontSize: 16,
                                        fontWeight: FontWeight.bold,
                                        color: AppTheme.secondaryColor,
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  snapshot.data ?? 'AI Analysis unavailable.',
                                  style: GoogleFonts.inter(
                                    fontSize: 13,
                                    color: Colors.white,
                                    height: 1.5,
                                  ),
                                ),
                              ],
                            ),
                          );
                        },
                      ),

                      const SizedBox(height: 36),

                      SizedBox(
                        width: double.infinity,
                        height: 50,
                        child: ElevatedButton.icon(
                          style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryColor),
                          icon: const Icon(LucideIcons.home, size: 18),
                          label: const Text('Back to Home'),
                          onPressed: () => context.go('/'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildMetricTile({
    required String title,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF0F172A),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppTheme.darkCardBorder),
      ),
      child: Column(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(height: 8),
          Text(
            value,
            style: GoogleFonts.outfit(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.white),
          ),
          Text(
            title,
            style: GoogleFonts.inter(fontSize: 12, color: AppTheme.darkTextSecondary),
          ),
        ],
      ),
    );
  }
}
