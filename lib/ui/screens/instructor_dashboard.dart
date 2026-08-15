import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';

class InstructorDashboard extends ConsumerWidget {
  const InstructorDashboard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tests = ref.watch(testsProvider);

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      appBar: AppBar(
        backgroundColor: AppTheme.darkCard,
        elevation: 0,
        title: Row(
          children: [
            const Icon(LucideIcons.userCheck, color: AppTheme.secondaryColor),
            const SizedBox(width: 12),
            Text(
              'Instructor Workspace',
              style: GoogleFonts.outfit(fontWeight: FontWeight.bold, fontSize: 20, color: Colors.white),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(LucideIcons.logOut, color: AppTheme.darkTextSecondary),
            onPressed: () {
              ref.read(databaseServiceProvider).signOut();
              context.go('/');
            },
          ),
          const SizedBox(width: 12),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: AppTheme.secondaryColor,
        icon: const Icon(LucideIcons.plus, color: Colors.white),
        label: Text('Create Test', style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
        onPressed: () => context.push('/create-test'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Welcome Card
            Card(
              color: AppTheme.darkCard,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(color: AppTheme.secondaryColor.withOpacity(0.3)),
              ),
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.secondaryColor.withOpacity(0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(LucideIcons.fileText, color: AppTheme.secondaryColor, size: 32),
                    ),
                    const SizedBox(width: 20),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Manage Examinations & Results',
                            style: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Create multi-section exams, distribute Test IDs to students, and analyze scores in real-time.',
                            style: GoogleFonts.inter(fontSize: 14, color: AppTheme.darkTextSecondary),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 32),

            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Your Published Exams',
                  style: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryColor),
                  icon: const Icon(LucideIcons.plus, size: 16),
                  label: const Text('Create New Exam'),
                  onPressed: () => context.push('/create-test'),
                ),
              ],
            ),

            const SizedBox(height: 16),

            if (tests.isEmpty)
              Card(
                color: AppTheme.darkCard,
                child: Padding(
                  padding: const EdgeInsets.all(40),
                  child: Center(
                    child: Column(
                      children: [
                        const Icon(LucideIcons.fileQuestion, size: 48, color: AppTheme.darkTextSecondary),
                        const SizedBox(height: 16),
                        Text(
                          'No exams created yet.',
                          style: GoogleFonts.outfit(fontSize: 18, color: Colors.white, fontWeight: FontWeight.w600),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Click "Create New Exam" to build your first online MCQ test.',
                          style: GoogleFonts.inter(color: AppTheme.darkTextSecondary, fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                ),
              )
            else
              ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: tests.length,
                separatorBuilder: (_, __) => const SizedBox(height: 16),
                itemBuilder: (context, index) {
                  final test = tests[index];
                  return _buildTestCard(context, ref, test);
                },
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildTestCard(BuildContext context, WidgetRef ref, TestModel test) {
    return Card(
      color: AppTheme.darkCard,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    test.title,
                    style: GoogleFonts.outfit(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.accentColor.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppTheme.accentColor.withOpacity(0.5)),
                  ),
                  child: Text(
                    test.status.toUpperCase(),
                    style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.bold, color: AppTheme.accentColor),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              test.description.isEmpty ? 'No description provided.' : test.description,
              style: GoogleFonts.inter(fontSize: 13, color: AppTheme.darkTextSecondary),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 8,
              children: [
                _buildChip(LucideIcons.clock, '${test.durationMinutes} Mins'),
                _buildChip(LucideIcons.helpCircle, '${test.totalQuestions} Questions'),
                _buildChip(LucideIcons.layers, '${test.sections.length} Sections'),
                _buildChip(LucideIcons.award, '+${test.positiveMarks} / -${test.negativeMarks} Marks'),
              ],
            ),
            const Divider(color: AppTheme.darkCardBorder, height: 32),
            Row(
              children: [
                SelectableText(
                  'Test ID: ${test.testId}',
                  style: GoogleFonts.inter(fontWeight: FontWeight.bold, color: AppTheme.secondaryColor, fontSize: 13),
                ),
                IconButton(
                  icon: const Icon(LucideIcons.copy, size: 16, color: AppTheme.secondaryColor),
                  tooltip: 'Copy Test ID',
                  onPressed: () {
                    Clipboard.setData(ClipboardData(text: test.testId));
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Test ID copied to clipboard!')),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: 12),
            _buildStudentSubmissionsList(ref, test.testId),
          ],
        ),
      ),
    );
  }

  Widget _buildStudentSubmissionsList(WidgetRef ref, String testId) {
    final resultsAsync = ref.watch(testResultsProvider(testId));

    return resultsAsync.when(
      data: (results) {
        if (results.isEmpty) {
          return Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.darkBg,
              borderRadius: BorderRadius.circular(10),
            ),
            child: Row(
              children: [
                const Icon(LucideIcons.users, size: 16, color: AppTheme.darkTextSecondary),
                const SizedBox(width: 8),
                Text(
                  'No student submissions recorded yet.',
                  style: GoogleFonts.inter(fontSize: 12, color: AppTheme.darkTextSecondary),
                ),
              ],
            ),
          );
        }

        return Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: AppTheme.darkBg,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppTheme.darkCardBorder),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Icon(LucideIcons.users, size: 16, color: AppTheme.accentColor),
                  const SizedBox(width: 8),
                  Text(
                    'Student Submissions (${results.length})',
                    style: GoogleFonts.outfit(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Column(
                children: results.map((res) {
                  return Container(
                    margin: const EdgeInsets.only(bottom: 8),
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.darkCard,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Row(
                      children: [
                        const CircleAvatar(
                          radius: 14,
                          backgroundColor: AppTheme.accentColor,
                          child: Icon(LucideIcons.user, size: 14, color: Colors.white),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                res.studentName,
                                style: GoogleFonts.inter(fontWeight: FontWeight.bold, fontSize: 13, color: Colors.white),
                              ),
                              Text(
                                'Roll: ${res.rollNumber} • ${res.district}',
                                style: GoogleFonts.inter(fontSize: 11, color: AppTheme.darkTextSecondary),
                              ),
                            ],
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.accentColor.withValues(alpha: 0.2),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            'Score: ${res.totalScore.toStringAsFixed(1)} / ${res.totalQuestions}',
                            style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.bold, color: AppTheme.accentColor),
                          ),
                        ),
                      ],
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        );
      },
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
    );
  }

  Widget _buildChip(IconData icon, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: AppTheme.darkTextSecondary),
        const SizedBox(width: 6),
        Text(text, style: GoogleFonts.inter(fontSize: 12, color: AppTheme.darkTextSecondary)),
      ],
    );
  }
}
