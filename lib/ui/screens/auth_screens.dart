import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';

// ─── ADMIN LOGIN SCREEN ───────────────────────────────────────────────────────

class AdminLoginScreen extends ConsumerStatefulWidget {
  const AdminLoginScreen({super.key});

  @override
  ConsumerState<AdminLoginScreen> createState() => _AdminLoginScreenState();
}

class _AdminLoginScreenState extends ConsumerState<AdminLoginScreen> {
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: 'admin@examsystem.com');
    _passwordController = TextEditingController(text: 'admin123');
  }

  Future<void> _login() async {
    setState(() => _isLoading = true);
    try {
      final dbService = ref.read(databaseServiceProvider);
      await dbService.signInWithEmail(_emailController.text, _passwordController.text);
    } catch (_) {}

    if (mounted) {
      context.go('/superadmin-dashboard');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(LucideIcons.arrowLeft, color: Colors.white),
          onPressed: () => context.go('/'),
        ),
      ),
      extendBodyBehindAppBar: true,
      body: Container(
        decoration: const BoxDecoration(gradient: AppTheme.darkGradientBg),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Card(
                  color: AppTheme.darkCard,
                  elevation: 6,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                    side: const BorderSide(color: AppTheme.darkCardBorder),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppTheme.primaryColor.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: const Icon(LucideIcons.shieldCheck, color: AppTheme.primaryColor),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              'Super Admin Portal',
                              style: GoogleFonts.outfit(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        TextField(
                          controller: _emailController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Admin Email',
                            prefixIcon: Icon(LucideIcons.mail, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Password',
                            prefixIcon: Icon(LucideIcons.lock, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 28),
                        SizedBox(
                          width: double.infinity,
                          height: 50,
                          child: ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryColor),
                            icon: const Icon(LucideIcons.logIn, size: 18),
                            label: const Text('Access Admin Dashboard'),
                            onPressed: _isLoading ? null : _login,
                          ),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          height: 44,
                          child: OutlinedButton.icon(
                            icon: const Icon(LucideIcons.sparkles, size: 16, color: AppTheme.primaryColor),
                            label: const Text('1-Click Demo Login'),
                            onPressed: () => context.go('/superadmin-dashboard'),
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
      ),
    );
  }
}

// ─── INSTRUCTOR LOGIN SCREEN ──────────────────────────────────────────────────

class InstructorLoginScreen extends ConsumerStatefulWidget {
  const InstructorLoginScreen({super.key});

  @override
  ConsumerState<InstructorLoginScreen> createState() => _InstructorLoginScreenState();
}

class _InstructorLoginScreenState extends ConsumerState<InstructorLoginScreen> {
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: 'instructor@examsystem.com');
    _passwordController = TextEditingController(text: 'password123');
  }

  Future<void> _login() async {
    setState(() => _isLoading = true);
    try {
      final dbService = ref.read(databaseServiceProvider);
      await dbService.signInWithEmail(_emailController.text, _passwordController.text);
    } catch (_) {}

    if (mounted) {
      context.go('/instructor-dashboard');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(LucideIcons.arrowLeft, color: Colors.white),
          onPressed: () => context.go('/'),
        ),
      ),
      extendBodyBehindAppBar: true,
      body: Container(
        decoration: const BoxDecoration(gradient: AppTheme.darkGradientBg),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Card(
                  color: AppTheme.darkCard,
                  elevation: 6,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                    side: const BorderSide(color: AppTheme.darkCardBorder),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppTheme.secondaryColor.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: const Icon(LucideIcons.userCheck, color: AppTheme.secondaryColor),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              'Instructor Workspace',
                              style: GoogleFonts.outfit(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        TextField(
                          controller: _emailController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Instructor Email',
                            prefixIcon: Icon(LucideIcons.mail, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _passwordController,
                          obscureText: true,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Password',
                            prefixIcon: Icon(LucideIcons.lock, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 28),
                        SizedBox(
                          width: double.infinity,
                          height: 50,
                          child: ElevatedButton(
                            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.secondaryColor),
                            onPressed: _isLoading ? null : _login,
                            child: const Text('Sign In as Instructor'),
                          ),
                        ),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          height: 44,
                          child: OutlinedButton.icon(
                            icon: const Icon(LucideIcons.sparkles, size: 16, color: AppTheme.secondaryColor),
                            label: const Text('1-Click Demo Login'),
                            onPressed: () => context.go('/instructor-dashboard'),
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
      ),
    );
  }
}

// ─── STUDENT ENTRY SCREEN ─────────────────────────────────────────────────────

class StudentEntryScreen extends ConsumerStatefulWidget {
  const StudentEntryScreen({super.key});

  @override
  ConsumerState<StudentEntryScreen> createState() => _StudentEntryScreenState();
}

class _StudentEntryScreenState extends ConsumerState<StudentEntryScreen> {
  late final TextEditingController _testIdController;
  late final TextEditingController _nameController;
  late final TextEditingController _rollController;
  late final TextEditingController _districtController;
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _testIdController = TextEditingController(text: 'DEMO_TEST_01');
    _nameController = TextEditingController(text: 'Alex Morgan');
    _rollController = TextEditingController(text: '2026001');
    _districtController = TextEditingController(text: 'Central Campus');
  }

  Future<void> _startExam() async {
    final testId = _testIdController.text.trim();
    final name = _nameController.text.trim();
    final roll = _rollController.text.trim();
    final district = _districtController.text.trim();

    setState(() => _isLoading = true);

    try {
      final dbService = ref.read(databaseServiceProvider);
      final test = await dbService.getTest(testId);

      ref.read(examSessionProvider.notifier).startSession(
        test: test ?? TestModel(
          testId: testId,
          title: 'Computer Science Sample Exam 2026',
          description: 'Comprehensive test on Data Structures & Algorithms.',
          instructorUid: 'inst_1',
          instructorName: 'Prof. Alan Turing',
          durationMinutes: 30,
          positiveMarks: 1.0,
          negativeMarks: 0.25,
          sections: [
            SectionModel(
              id: 's1',
              title: 'Algorithms & Data Structures',
              questions: [
                QuestionModel(
                  id: 'q1',
                  text: 'What is the average time complexity of QuickSort?',
                  options: ['O(n)', 'O(n log n)', 'O(n^2)', 'O(log n)'],
                  correctOption: 'B',
                  explanation: 'QuickSort runs in O(n log n) average time complexity.',
                ),
                QuestionModel(
                  id: 'q2',
                  text: 'Which data structure follows the First-In, First-Out (FIFO) principle?',
                  options: ['Stack', 'Queue', 'Tree', 'Graph'],
                  correctOption: 'B',
                  explanation: 'Queues process items in First-In, First-Out order.',
                ),
              ],
            ),
          ],
        ),
        studentName: name,
        rollNumber: roll,
        district: district,
      );

      if (mounted) {
        context.go('/exam-runner');
      }
    } catch (_) {
      if (mounted) {
        context.go('/exam-runner');
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
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(LucideIcons.arrowLeft, color: Colors.white),
          onPressed: () => context.go('/'),
        ),
      ),
      extendBodyBehindAppBar: true,
      body: Container(
        decoration: const BoxDecoration(gradient: AppTheme.darkGradientBg),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Card(
                  color: AppTheme.darkCard,
                  elevation: 6,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20),
                    side: const BorderSide(color: AppTheme.darkCardBorder),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: AppTheme.accentColor.withValues(alpha: 0.15),
                                borderRadius: BorderRadius.circular(10),
                              ),
                              child: const Icon(LucideIcons.bookOpen, color: AppTheme.accentColor),
                            ),
                            const SizedBox(width: 12),
                            Text(
                              'Student Exam Portal',
                              style: GoogleFonts.outfit(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),
                        Builder(
                          builder: (context) {
                            final availableTests = ref.watch(testsProvider);
                            final currentSelected = availableTests.any((t) => t.testId == _testIdController.text)
                                ? _testIdController.text
                                : (availableTests.isNotEmpty ? availableTests.first.testId : null);

                            if (availableTests.isEmpty) return const SizedBox.shrink();

                            return Column(
                              children: [
                                DropdownButtonFormField<String>(
                                  value: currentSelected,
                                  dropdownColor: AppTheme.darkCard,
                                  style: const TextStyle(color: Colors.white),
                                  isExpanded: true,
                                  decoration: const InputDecoration(
                                    labelText: 'Select Available Examination',
                                    prefixIcon: Icon(LucideIcons.fileText, color: AppTheme.accentColor),
                                  ),
                                  items: availableTests.map((test) {
                                    return DropdownMenuItem<String>(
                                      value: test.testId,
                                      child: Text(
                                        '${test.title} (${test.testId})',
                                        overflow: TextOverflow.ellipsis,
                                        style: GoogleFonts.inter(color: Colors.white, fontSize: 13),
                                      ),
                                    );
                                  }).toList(),
                                  onChanged: (val) {
                                    if (val != null) {
                                      setState(() {
                                        _testIdController.text = val;
                                      });
                                    }
                                  },
                                ),
                                const SizedBox(height: 16),
                              ],
                            );
                          },
                        ),
                        TextField(
                          controller: _testIdController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Test ID / Exam Code',
                            prefixIcon: Icon(LucideIcons.key, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _nameController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Student Full Name',
                            prefixIcon: Icon(LucideIcons.user, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _rollController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'Roll Number / Registration No.',
                            prefixIcon: Icon(LucideIcons.hash, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextField(
                          controller: _districtController,
                          style: const TextStyle(color: Colors.white),
                          decoration: const InputDecoration(
                            labelText: 'District / Location',
                            prefixIcon: Icon(LucideIcons.mapPin, color: AppTheme.darkTextSecondary),
                          ),
                        ),
                        const SizedBox(height: 28),
                        SizedBox(
                          width: double.infinity,
                          height: 50,
                          child: ElevatedButton.icon(
                            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentColor),
                            icon: _isLoading
                                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                                : const Icon(LucideIcons.play, size: 18),
                            label: const Text('Start Examination'),
                            onPressed: _isLoading ? null : _startExam,
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
      ),
    );
  }
}
