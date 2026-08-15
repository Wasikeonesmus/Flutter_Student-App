import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import '../../theme/app_theme.dart';

class RoleSelectionScreen extends StatelessWidget {
  const RoleSelectionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final isDesktop = screenWidth > 768;

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: AppTheme.darkGradientBg,
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 1000),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    // Header Branding
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryColor.withValues(alpha: 0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        LucideIcons.graduationCap,
                        size: 48,
                        color: AppTheme.primaryColor,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Text(
                      'Exam System SaaS Platform',
                      textAlign: TextAlign.center,
                      style: GoogleFonts.outfit(
                        fontSize: isDesktop ? 36 : 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Select your portal to continue',
                      textAlign: TextAlign.center,
                      style: GoogleFonts.inter(
                        fontSize: 16,
                        color: AppTheme.darkTextSecondary,
                      ),
                    ),
                    const SizedBox(height: 48),

                    // Role Cards Wrap
                    Wrap(
                      spacing: 20,
                      runSpacing: 20,
                      alignment: WrapAlignment.center,
                      children: [
                        _buildRoleCard(
                          context,
                          title: 'Student Portal',
                          subtitle: 'Enter Test ID & attempt online examination',
                          icon: LucideIcons.bookOpen,
                          accentColor: AppTheme.accentColor,
                          onTap: () => context.push('/student-entry'),
                          cardWidth: isDesktop ? 280 : 340,
                        ),
                        _buildRoleCard(
                          context,
                          title: 'Instructor Portal',
                          subtitle: 'Create exams, manage batches & evaluate scores',
                          icon: LucideIcons.userCheck,
                          accentColor: AppTheme.secondaryColor,
                          onTap: () => context.push('/instructor-login'),
                          cardWidth: isDesktop ? 280 : 340,
                        ),
                        _buildRoleCard(
                          context,
                          title: 'Super Admin',
                          subtitle: 'System management, instructor approvals & payments',
                          icon: LucideIcons.shieldCheck,
                          accentColor: AppTheme.primaryColor,
                          onTap: () => context.push('/admin-login'),
                          cardWidth: isDesktop ? 280 : 340,
                        ),
                      ],
                    ),

                    const SizedBox(height: 48),
                    Text(
                      'Powered by Students Welfare Foundation',
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        color: AppTheme.darkTextSecondary.withValues(alpha: 0.6),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRoleCard(
    BuildContext context, {
    required String title,
    required String subtitle,
    required IconData icon,
    required Color accentColor,
    required VoidCallback onTap,
    required double cardWidth,
  }) {
    return SizedBox(
      width: cardWidth,
      child: Card(
        color: AppTheme.darkCard,
        elevation: 4,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: accentColor.withValues(alpha: 0.3), width: 1.5),
        ),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.all(28),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: accentColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, size: 28, color: accentColor),
                ),
                const SizedBox(height: 20),
                Text(
                  title,
                  style: GoogleFonts.outfit(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  subtitle,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: AppTheme.darkTextSecondary,
                    height: 1.4,
                  ),
                ),
                const SizedBox(height: 24),
                Row(
                  children: [
                    Text(
                      'Enter Portal',
                      style: GoogleFonts.inter(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: accentColor,
                      ),
                    ),
                    const SizedBox(width: 6),
                    Icon(LucideIcons.arrowRight, size: 16, color: accentColor),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
