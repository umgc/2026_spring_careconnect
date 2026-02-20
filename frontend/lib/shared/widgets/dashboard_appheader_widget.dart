  import 'package:care_connect_app/pages/settings_page.dart';
  import 'package:care_connect_app/features/emergency_qr/qr_screen.dart';
  import 'package:provider/provider.dart';
  import 'package:care_connect_app/providers/user_provider.dart';
  import 'package:flutter/material.dart';
  import 'package:shared_preferences/shared_preferences.dart';

  class DashboardAppHeader extends StatefulWidget
      implements PreferredSizeWidget {
    final String userName;
    final String? timezone;
    final String? profileImageUrl;
    final String role;

    const DashboardAppHeader({
      super.key,
      required this.userName,
      required this.role,
      this.timezone,
      this.profileImageUrl = "",
    });

    @override
    Size get preferredSize => const Size.fromHeight(210);

    @override
    State<DashboardAppHeader> createState() => _DashboardAppHeaderState();
  }

  class _DashboardAppHeaderState extends State<DashboardAppHeader> {
    bool _isOfflineMode = false;

    @override
    void initState() {
      super.initState();
      _loadOfflineStatus();
    }

    Future<void> _loadOfflineStatus() async {
      final prefs = await SharedPreferences.getInstance();
      setState(() {
        _isOfflineMode = prefs.getBool('offline_mode') ?? false;
      });
    }

    @override
    Widget build(BuildContext context) {
      final theme = Theme.of(context);
      final DateTime time = DateTime.now();
      final String timeZone = time.timeZoneName;

      String twoDigits(int n) => n.toString().padLeft(2, '0');

      String formattedTime =
          '${twoDigits(time.month)}/${twoDigits(time.day)}/${time.year} ${twoDigits(time.hour)}:${twoDigits(time.minute)}';

      return AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        automaticallyImplyLeading: false,
        toolbarHeight: widget.preferredSize.height,
        flexibleSpace: Container(
          decoration: BoxDecoration(
            color: theme.scaffoldBackgroundColor,
            borderRadius: const BorderRadius.only(
              bottomLeft: Radius.circular(25),
              bottomRight: Radius.circular(25),
            ),
          ),
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [

                  /// TOP BAR
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 35,
                            height: 35,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primary,
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.local_hospital,
                              color: theme.colorScheme.onPrimary,
                              size: 20,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            "CARECONNECT",
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.primary,
                              letterSpacing: 1.0,
                            ),
                          ),
                        ],
                      ),

                      /// SETTINGS ICON
                      IconButton(
                        onPressed: () {
                          Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => const SettingsPage(),
                            ),
                          );
                        },
                        icon: Icon(
                          Icons.settings_outlined,
                          color: theme.colorScheme.onSurface.withOpacity(0.6),
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 15),

                  /// PROFILE + TEXT
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 25,
                        backgroundColor:
                            theme.colorScheme.surfaceContainerHighest,
                        child: Icon(
                          Icons.person,
                          size: 30,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),

                      const SizedBox(width: 15),

                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [

                            /// 🔶 OFFLINE INDICATOR
                            if (_isOfflineMode)
                              Row(
                                children: const [
                                  Icon(
                                    Icons.cloud_off,
                                    color: Colors.orange,
                                    size: 16,
                                  ),
                                  SizedBox(width: 6),
                                  Text(
                                    "Offline Mode Active",
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.orange,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),

                            if (_isOfflineMode)
                              const SizedBox(height: 6),

                            /// WELCOME TEXT
                            Text(
                              "Welcome back ${widget.userName}",
                              style: TextStyle(
                                fontSize: 22,
                                fontWeight: FontWeight.w600,
                                color: theme.colorScheme.primary,
                              ),
                            ),

                            const SizedBox(height: 3),

                            Text(
                              "$formattedTime $timeZone",
                              style: TextStyle(
                                fontSize: 13,
                                color: theme.colorScheme.onSurface
                                    .withOpacity(0.6),
                              ),
                            ),

                            const SizedBox(height: 2),

                            Text(
                              widget.role == "PATIENT"
                                  ? "How are you feeling today?"
                                  : "Your patients' health summary",
                              style: TextStyle(
                                fontSize: 13,
                                color: theme.colorScheme.onSurface
                                    .withOpacity(0.6),
                              ),
                            ),
                          ],
                        ),
                      ),
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