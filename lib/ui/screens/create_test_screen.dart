import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';
import '../../services/ai_service.dart';

class CreateTestScreen extends ConsumerStatefulWidget {
  const CreateTestScreen({super.key});

  @override
  ConsumerState<CreateTestScreen> createState() => _CreateTestScreenState();
}

class _CreateTestScreenState extends ConsumerState<CreateTestScreen> {
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _durationController = TextEditingController(text: '60');
  final _positiveController = TextEditingController(text: '1.0');
  final _negativeController = TextEditingController(text: '0.25');

  bool _shuffleQuestions = false;
  bool _shuffleOptions = false;
  bool _isLoading = false;

  final List<SectionModel> _sections = [
    SectionModel(
      id: 'section_1',
      title: 'General Section',
      questions: [],
    ),
  ];

  void _addQuestion(int sectionIndex) {
    setState(() {
      final section = _sections[sectionIndex];
      final newQIndex = section.questions.length + 1;
      final updatedQuestions = List<QuestionModel>.from(section.questions)
        ..add(
          QuestionModel(
            id: 'q_${DateTime.now().millisecondsSinceEpoch}',
            text: 'Question $newQIndex: Enter question text here...',
            options: ['Option A', 'Option B', 'Option C', 'Option D'],
            correctOption: 'A',
          ),
        );

      _sections[sectionIndex] = SectionModel(
        id: section.id,
        title: section.title,
        questions: updatedQuestions,
      );
    });
  }

  Future<void> _showAiGeneratorDialog(int sectionIndex) async {
    final topicController = TextEditingController(
      text: _titleController.text.isNotEmpty ? _titleController.text : 'Computer Science',
    );
    int count = 5;
    bool isGenerating = false;

    await showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              backgroundColor: AppTheme.darkCard,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              title: Row(
                children: [
                  const Icon(LucideIcons.sparkles, color: AppTheme.secondaryColor),
                  const SizedBox(width: 10),
                  Text('AI Question Generator', style: GoogleFonts.outfit(color: Colors.white, fontWeight: FontWeight.bold)),
                ],
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Set the topic and how many questions to generate automatically.',
                    style: GoogleFonts.inter(color: AppTheme.darkTextSecondary, fontSize: 13),
                  ),
                  const SizedBox(height: 16),
                  // Topic field
                  TextField(
                    controller: topicController,
                    style: const TextStyle(color: Colors.white),
                    decoration: const InputDecoration(
                      labelText: 'Topic / Subject',
                      prefixIcon: Icon(LucideIcons.bookOpen, color: AppTheme.darkTextSecondary),
                    ),
                  ),
                  const SizedBox(height: 20),
                  // Number of questions picker
                  Text(
                    'Number of Questions',
                    style: GoogleFonts.inter(color: AppTheme.darkTextSecondary, fontSize: 13),
                  ),
                  const SizedBox(height: 10),
                  Container(
                    decoration: BoxDecoration(
                      color: AppTheme.darkBg,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.secondaryColor.withOpacity(0.3)),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        // Minus button
                        IconButton(
                          onPressed: count > 1
                              ? () => setDialogState(() => count--)
                              : null,
                          icon: const Icon(LucideIcons.minusCircle),
                          color: count > 1 ? AppTheme.secondaryColor : Colors.grey,
                          tooltip: 'Decrease',
                        ),
                        // Count display
                        Column(
                          children: [
                            Text(
                              '$count',
                              style: GoogleFonts.outfit(
                                color: AppTheme.secondaryColor,
                                fontSize: 32,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              count == 1 ? 'question' : 'questions',
                              style: GoogleFonts.inter(color: AppTheme.darkTextSecondary, fontSize: 11),
                            ),
                          ],
                        ),
                        // Plus button
                        IconButton(
                          onPressed: count < 10
                              ? () => setDialogState(() => count++)
                              : null,
                          icon: const Icon(LucideIcons.plusCircle),
                          color: count < 10 ? AppTheme.secondaryColor : Colors.grey,
                          tooltip: 'Increase',
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  // Quick select chips
                  Wrap(
                    spacing: 8,
                    children: [3, 5, 7, 10].map((n) {
                      final selected = count == n;
                      return GestureDetector(
                        onTap: () => setDialogState(() => count = n),
                        child: Chip(
                          label: Text('$n', style: GoogleFonts.inter(
                            color: selected ? Colors.white : AppTheme.darkTextSecondary,
                            fontWeight: selected ? FontWeight.bold : FontWeight.normal,
                            fontSize: 12,
                          )),
                          backgroundColor: selected ? AppTheme.secondaryColor : AppTheme.darkBg,
                          side: BorderSide(
                            color: selected ? AppTheme.secondaryColor : Colors.grey.shade700,
                          ),
                          padding: EdgeInsets.zero,
                        ),
                      );
                    }).toList(),
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Cancel', style: TextStyle(color: AppTheme.darkTextSecondary)),
                ),
                ElevatedButton.icon(
                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.secondaryColor),
                  icon: isGenerating
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                      : const Icon(LucideIcons.wand2, size: 16),
                  label: Text(isGenerating ? 'Generating...' : 'Generate $count Questions'),
                  onPressed: isGenerating
                      ? null
                      : () async {
                          setDialogState(() => isGenerating = true);
                          final aiService = ref.read(aiServiceProvider);
                          final generated = await aiService.generateQuestionsWithAI(
                            topic: topicController.text.trim(),
                            count: count,
                          );

                          setState(() {
                            final section = _sections[sectionIndex];
                            // Replace ALL questions (including any placeholders) with generated ones
                            _sections[sectionIndex] = SectionModel(
                              id: section.id,
                              title: section.title,
                              questions: generated,
                            );
                          });

                          if (mounted) {
                            Navigator.pop(context);
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text('Generated ${generated.length} AI questions for "${topicController.text}"!')),
                            );
                          }
                        },
                ),
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _saveTest() async {
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter an Exam Title')),
      );
      return;
    }

    setState(() => _isLoading = true);

    try {
      final authUser = ref.read(authStateProvider).asData?.value;
      final aiService = AiService();

      List<SectionModel> finalSections = [];
      for (var sec in _sections) {
        final bool isPlaceholder = sec.questions.isEmpty ||
            sec.questions.any((q) =>
                q.text.contains('Sample Question') ||
                q.text.contains('capital of France') ||
                q.text.contains('Enter question text') ||
                q.options.contains('Option A'));

        if (isPlaceholder) {
          final autoQuestions = await aiService.generateQuestionsWithAI(topic: title, count: 4);
          finalSections.add(SectionModel(
            id: sec.id,
            title: sec.title.isEmpty ? '$title Core Section' : sec.title,
            questions: autoQuestions,
          ));
        } else {
          finalSections.add(sec);
        }
      }

      final newTest = TestModel(
        testId: '',
        title: title,
        description: _descriptionController.text.trim(),
        instructorUid: authUser?.uid ?? 'anon_instructor',
        instructorName: authUser?.email ?? 'Instructor',
        durationMinutes: int.tryParse(_durationController.text) ?? 60,
        positiveMarks: double.tryParse(_positiveController.text) ?? 1.0,
        negativeMarks: double.tryParse(_negativeController.text) ?? 0.25,
        shuffleQuestions: _shuffleQuestions,
        shuffleOptions: _shuffleOptions,
        status: 'published',
        sections: finalSections,
      );

      final testId = await ref.read(testsProvider.notifier).createTest(newTest);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Exam "$title" published successfully! Test ID: $testId')),
        );
        context.go('/instructor-dashboard');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to publish test: ${e.toString()}')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      appBar: AppBar(
        backgroundColor: AppTheme.darkCard,
        elevation: 0,
        title: Text(
          'Create New Exam',
          style: GoogleFonts.outfit(fontWeight: FontWeight.bold, fontSize: 20, color: Colors.white),
        ),
        actions: [
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentColor),
            icon: _isLoading
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                : const Icon(LucideIcons.check, size: 18),
            label: const Text('Publish Test'),
            onPressed: _isLoading ? null : _saveTest,
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // General Info Card
            Card(
              color: AppTheme.darkCard,
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Exam General Information',
                      style: GoogleFonts.outfit(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _titleController,
                      style: const TextStyle(color: Colors.white),
                      decoration: const InputDecoration(
                        labelText: 'Exam Title (e.g. Mathematics Midterm 2026)',
                        prefixIcon: Icon(LucideIcons.type, color: AppTheme.darkTextSecondary),
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _descriptionController,
                      maxLines: 2,
                      style: const TextStyle(color: Colors.white),
                      decoration: const InputDecoration(
                        labelText: 'Description / Instructions for Students',
                        prefixIcon: Icon(LucideIcons.fileText, color: AppTheme.darkTextSecondary),
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _durationController,
                            keyboardType: TextInputType.number,
                            style: const TextStyle(color: Colors.white),
                            decoration: const InputDecoration(
                              labelText: 'Duration (Minutes)',
                              prefixIcon: Icon(LucideIcons.clock, color: AppTheme.darkTextSecondary),
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextField(
                            controller: _positiveController,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            style: const TextStyle(color: Colors.white),
                            decoration: const InputDecoration(
                              labelText: 'Positive Mark (+)',
                              prefixIcon: Icon(LucideIcons.plusCircle, color: AppTheme.accentColor),
                            ),
                          ),
                        ),
                        const SizedBox(width: 16),
                        Expanded(
                          child: TextField(
                            controller: _negativeController,
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            style: const TextStyle(color: Colors.white),
                            decoration: const InputDecoration(
                              labelText: 'Negative Mark (-)',
                              prefixIcon: Icon(LucideIcons.minusCircle, color: AppTheme.dangerColor),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 32),

            // Questions Section
            Text(
              'Questions & Sections',
              style: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
            ),
            const SizedBox(height: 16),

            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _sections.length,
              separatorBuilder: (_, __) => const SizedBox(height: 20),
              itemBuilder: (context, sIndex) {
                final section = _sections[sIndex];
                return Card(
                  color: AppTheme.darkCard,
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              section.title,
                              style: GoogleFonts.outfit(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.secondaryColor),
                            ),
                            Row(
                              children: [
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.secondaryColor),
                                  icon: const Icon(LucideIcons.sparkles, size: 14),
                                  label: const Text('Generate with AI'),
                                  onPressed: () => _showAiGeneratorDialog(sIndex),
                                ),
                                const SizedBox(width: 8),
                                ElevatedButton.icon(
                                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryColor),
                                  icon: const Icon(LucideIcons.plus, size: 14),
                                  label: const Text('Add Question'),
                                  onPressed: () => _addQuestion(sIndex),
                                ),
                              ],
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        ...section.questions.asMap().entries.map((entry) {
                          final qIndex = entry.key;
                          final q = entry.value;
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 16),
                            child: Container(
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: const Color(0xFF0F172A),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(color: AppTheme.darkCardBorder),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Q${qIndex + 1}. ${q.text}',
                                    style: GoogleFonts.inter(fontWeight: FontWeight.w600, color: Colors.white),
                                  ),
                                  const SizedBox(height: 12),
                                  Wrap(
                                    spacing: 12,
                                    runSpacing: 8,
                                    children: q.options.asMap().entries.map((optEntry) {
                                      final isCorrect = (optEntry.key == 0 && q.correctOption == 'A') ||
                                          (optEntry.key == 2 && q.correctOption == 'C');
                                      return Container(
                                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                        decoration: BoxDecoration(
                                          color: isCorrect ? AppTheme.accentColor.withOpacity(0.2) : Colors.transparent,
                                          borderRadius: BorderRadius.circular(8),
                                          border: Border.all(
                                            color: isCorrect ? AppTheme.accentColor : AppTheme.darkCardBorder,
                                          ),
                                        ),
                                        child: Text(
                                          '${String.fromCharCode(65 + optEntry.key)}) ${optEntry.value}',
                                          style: GoogleFonts.inter(
                                            fontSize: 12,
                                            color: isCorrect ? AppTheme.accentColor : AppTheme.darkTextSecondary,
                                          ),
                                        ),
                                      );
                                    }).toList(),
                                  ),
                                ],
                              ),
                            ),
                          );
                        }),
                      ],
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}
