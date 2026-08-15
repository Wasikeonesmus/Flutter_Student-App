import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../ui/screens/role_selection_screen.dart';
import '../ui/screens/auth_screens.dart';
import '../ui/screens/superadmin_dashboard.dart';
import '../ui/screens/instructor_dashboard.dart';
import '../ui/screens/create_test_screen.dart';
import '../ui/screens/exam_runner_screen.dart';
import '../ui/screens/exam_results_screen.dart';

final appRouter = GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (context, state) => const RoleSelectionScreen(),
    ),
    GoRoute(
      path: '/admin-login',
      builder: (context, state) => const AdminLoginScreen(),
    ),
    GoRoute(
      path: '/instructor-login',
      builder: (context, state) => const InstructorLoginScreen(),
    ),
    GoRoute(
      path: '/superadmin-dashboard',
      builder: (context, state) => const SuperAdminDashboard(),
    ),
    GoRoute(
      path: '/instructor-dashboard',
      builder: (context, state) => const InstructorDashboard(),
    ),
    GoRoute(
      path: '/create-test',
      builder: (context, state) => const CreateTestScreen(),
    ),
    GoRoute(
      path: '/student-entry',
      builder: (context, state) => const StudentEntryScreen(),
    ),
    GoRoute(
      path: '/exam-runner',
      builder: (context, state) => const ExamRunnerScreen(),
    ),
    GoRoute(
      path: '/exam-results',
      builder: (context, state) => const ExamResultsScreen(),
    ),
  ],
  errorBuilder: (context, state) => Scaffold(
    body: Center(
      child: Text('Page not found: ${state.error}'),
    ),
  ),
);
