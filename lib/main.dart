import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';
import 'theme/app_theme.dart';
import 'router/app_router.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  try {
    await Firebase.initializeApp(
      options: const FirebaseOptions(
        apiKey: "AIzaSyDemoApiKeyForExamSystem12345",
        appId: "1:100000000000:web:exam-system-demo",
        messagingSenderId: "100000000000",
        projectId: "exam-system-demo",
      ),
    );
  } catch (_) {
    // Ignore duplicate or platform initialization errors
  }

  runApp(const ProviderScope(child: ExamSystemApp()));
}

class ExamSystemApp extends StatelessWidget {
  const ExamSystemApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Exam System SaaS Platform',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      routerConfig: appRouter,
    );
  }
}
