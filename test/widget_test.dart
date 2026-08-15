import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:exam_system_flutter/main.dart';

void main() {
  testWidgets('ExamSystemApp smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ExamSystemApp()));
    expect(find.byType(ExamSystemApp), findsOneWidget);
  });
}
