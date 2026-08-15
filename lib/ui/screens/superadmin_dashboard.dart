import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';
import '../../providers/app_providers.dart';
import '../../models/app_models.dart';

class SuperAdminDashboard extends ConsumerWidget {
  const SuperAdminDashboard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pendingInstructorsAsync = ref.watch(pendingInstructorsProvider);
    final allInstructorsAsync = ref.watch(allInstructorsProvider);

    return Scaffold(
      backgroundColor: AppTheme.darkBg,
      appBar: AppBar(
        backgroundColor: AppTheme.darkCard,
        elevation: 0,
        title: Row(
          children: [
            const Icon(LucideIcons.shieldCheck, color: AppTheme.primaryColor),
            const SizedBox(width: 12),
            Text(
              'Super Admin Portal',
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
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Overview Stats Grid
            LayoutBuilder(
              builder: (context, constraints) {
                final crossAxisCount = constraints.maxWidth > 900 ? 4 : (constraints.maxWidth > 600 ? 2 : 1);
                return GridView.count(
                  crossAxisCount: crossAxisCount,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  crossAxisSpacing: 16,
                  mainAxisSpacing: 16,
                  childAspectRatio: 2.2,
                  children: [
                    _buildStatCard(
                      title: 'Total Instructors',
                      value: allInstructorsAsync.when(
                        data: (list) => list.length.toString(),
                        loading: () => '...',
                        error: (_, __) => '0',
                      ),
                      icon: LucideIcons.users,
                      color: AppTheme.primaryColor,
                    ),
                    _buildStatCard(
                      title: 'Pending Approvals',
                      value: pendingInstructorsAsync.when(
                        data: (list) => list.length.toString(),
                        loading: () => '...',
                        error: (_, __) => '0',
                      ),
                      icon: LucideIcons.userPlus,
                      color: AppTheme.warningColor,
                    ),
                    _buildStatCard(
                      title: 'Active Tier Subscriptions',
                      value: '42',
                      icon: LucideIcons.award,
                      color: AppTheme.secondaryColor,
                    ),
                    _buildStatCard(
                      title: 'Platform Status',
                      value: 'Healthy',
                      icon: LucideIcons.activity,
                      color: AppTheme.accentColor,
                    ),
                  ],
                );
              },
            ),

            const SizedBox(height: 36),

            // Pending Approvals Section
            Text(
              'Pending Instructor Registrations',
              style: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
            ),
            const SizedBox(height: 16),

            pendingInstructorsAsync.when(
              data: (instructors) {
                if (instructors.isEmpty) {
                  return Card(
                    color: AppTheme.darkCard,
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Center(
                        child: Text(
                          'No pending instructor approvals.',
                          style: GoogleFonts.inter(color: AppTheme.darkTextSecondary),
                        ),
                      ),
                    ),
                  );
                }

                return ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: instructors.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final inst = instructors[index];
                    return _buildInstructorApprovalCard(context, ref, inst);
                  },
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (err, _) => Text('Error loading pending instructors: $err', style: const TextStyle(color: Colors.red)),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard({
    required String title,
    required String value,
    required IconData icon,
    required Color color,
  }) {
    return Card(
      color: AppTheme.darkCard,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: color.withOpacity(0.3)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: color.withOpacity(0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(icon, color: color, size: 24),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: GoogleFonts.inter(fontSize: 12, color: AppTheme.darkTextSecondary),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    value,
                    style: GoogleFonts.outfit(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInstructorApprovalCard(BuildContext context, WidgetRef ref, UserModel instructor) {
    String selectedTier = 'pro';

    return Card(
      color: AppTheme.darkCard,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: AppTheme.primaryColor.withOpacity(0.2),
              child: Text(
                instructor.name.isNotEmpty ? instructor.name[0].toUpperCase() : 'I',
                style: const TextStyle(color: AppTheme.primaryColor, fontWeight: FontWeight.bold),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    instructor.name.isEmpty ? 'Instructor' : instructor.name,
                    style: GoogleFonts.outfit(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                  ),
                  Text(
                    instructor.email,
                    style: GoogleFonts.inter(fontSize: 13, color: AppTheme.darkTextSecondary),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 16),
            ElevatedButton.icon(
              style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentColor),
              icon: const Icon(LucideIcons.check, size: 16),
              label: const Text('Approve'),
              onPressed: () async {
                final dbService = ref.read(databaseServiceProvider);
                await dbService.updateUserApproval(instructor.uid, 'approved', selectedTier);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('${instructor.email} approved as $selectedTier tier!')),
                  );
                }
              },
            ),
            const SizedBox(width: 8),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(foregroundColor: AppTheme.dangerColor),
              icon: const Icon(LucideIcons.x, size: 16),
              label: const Text('Reject'),
              onPressed: () async {
                final dbService = ref.read(databaseServiceProvider);
                await dbService.updateUserApproval(instructor.uid, 'suspended', 'none');
              },
            ),
          ],
        ),
      ),
    );
  }
}
