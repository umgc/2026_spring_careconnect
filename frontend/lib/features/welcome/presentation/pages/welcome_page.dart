import 'package:care_connect_app/l10n/app_localizations.dart';
import 'package:care_connect_app/providers/locale_provider.dart';
import 'package:care_connect_app/widgets/language/language_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import '../../../../config/env_constant.dart';
import 'package:provider/provider.dart';
class WelcomePage extends StatefulWidget {
  const WelcomePage({super.key});

  @override
  State<WelcomePage> createState() => _WelcomePageState();
}

class _WelcomePageState extends State<WelcomePage> {
  bool _isLoading = true;
  bool _isBackendHealthy = true;

  @override
  void initState() {
    super.initState();
    _checkBackendHealth();
  }

  Future<void> _checkBackendHealth() async {
    try {
      final String baseUrl = getBackendBaseUrl();
      final response = await http
          .get(Uri.parse('$baseUrl/v1/api/test/health'))
          .timeout(const Duration(seconds: 5));

      if (mounted) {
        setState(() {
          if (response.statusCode == 200) {
            final data = jsonDecode(response.body);
            _isBackendHealthy = data['status'] == 'healthy';
          } else {
            _isBackendHealthy = false;
          }
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isBackendHealthy = false;
        });
      }
    } finally {
      await Future.delayed(const Duration(seconds: 2));
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isMobile = MediaQuery.of(context).size.width < 600;
    final t = AppLocalizations.of(context)!;
    final currentLocale = context.watch<LocaleProvider>().locale;
    final currentLangLabel = currentLocale == null
        ? t.systemDefault
        : LanguagePicker.labelFor(currentLocale);

    return Scaffold(
      body: Container(
        width: double.infinity,
        height: double.infinity,
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFF4A5FBF),
              Color(0xFF3B4DBF),
            ],
          ),
        ),
        child: SafeArea(
          child: SingleChildScrollView(
            child: Padding(
              padding: EdgeInsets.symmetric(
                horizontal: isMobile ? 24 : 48,
                vertical: isMobile ? 32 : 48,
              ),
              child: Column(
                children: [

                  /// Language Picker
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      TextButton.icon(
                        onPressed: () => LanguagePicker.show(context),
                        icon: const Icon(Icons.language, color: Colors.white),
                        label: Text(
                          currentLangLabel,
                          style: TextStyle(
                            color: Colors.white,
                            fontWeight: FontWeight.w600,
                            fontSize: isMobile ? 12 : 14,
                          ),
                        ),
                        style: TextButton.styleFrom(
                          padding: EdgeInsets.symmetric(
                            horizontal: isMobile ? 10 : 12,
                            vertical: isMobile ? 6 : 8,
                          ),
                          backgroundColor:
                              Colors.white.withOpacity(0.12),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                            side: BorderSide(
                              color: Colors.white.withOpacity(0.25),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),

                  SizedBox(height: isMobile ? 40 : 60),

                  /// Logo
                  Container(
                    width: isMobile ? 100 : 200,
                    height: isMobile ? 100 : 200,
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Center(
                      child: Image.asset(
                        'assets/images/CareConnectLogo.png',
                        width: isMobile ? 90 : 190,
                        fit: BoxFit.contain,
                        color: Colors.white,
                        colorBlendMode: BlendMode.srcIn,
                      ),
                    ),
                  ),

                  SizedBox(height: isMobile ? 40 : 48),

                  Text(
                    'CareConnect',
                    style: TextStyle(
                      fontSize: isMobile ? 36 : 42,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                    textAlign: TextAlign.center,
                  ),

                  SizedBox(height: isMobile ? 16 : 20),

                  Text(
                    t.welcome_subtitle,
                    style: TextStyle(
                      fontSize: isMobile ? 18 : 20,
                      color: Colors.white.withOpacity(0.9),
                      fontWeight: FontWeight.w500,
                    ),
                    textAlign: TextAlign.center,
                  ),

                  SizedBox(height: isMobile ? 32 : 40),

                  Text(
                    t.welcome_description,
                    style: TextStyle(
                      fontSize: isMobile ? 16 : 18,
                      color: Colors.white.withOpacity(0.8),
                    ),
                    textAlign: TextAlign.center,
                  ),

                  SizedBox(height: isMobile ? 12 : 16),

                  Text(
                    t.welcome_tagline,
                    style: TextStyle(
                      fontSize: isMobile ? 18 : 20,
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                    textAlign: TextAlign.center,
                  ),

                  SizedBox(height: isMobile ? 40 : 48),

                  if (_isLoading) ...[
                    const CircularProgressIndicator(
                      color: Colors.white,
                    ),
                  ] else ...[
                    ElevatedButton(
                      onPressed: () {
                        context.go('/dashboard');
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: const Color(0xFF4A5FBF),
                        padding: EdgeInsets.symmetric(
                          horizontal: isMobile ? 32 : 40,
                          vertical: isMobile ? 16 : 20,
                        ),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(30),
                        ),
                      ),
                      child: Text(
                        t.welcomeContinue,
                        style: TextStyle(
                          fontSize: isMobile ? 16 : 18,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],

                  SizedBox(height: isMobile ? 40 : 60),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
  }
