import 'dart:async';

import 'package:care_connect_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:go_router/go_router.dart';
import '../providers/user_provider.dart';
import '../config/navigation/bottom_nav_config.dart';
import '../config/navigation/main_screen_config.dart';
import '../services/local_db/connectivity_router_service.dart';
import '../services/local_db/mood_storage_service.dart';

/// Main screen of the application. This is where the user is navigated to
/// after logging in. This contains the bottom nav bar and main screens
class MainScreen extends StatefulWidget {
  final int? initialTabIndex;
  final MainScreenConfig? config;

  const MainScreen({
    super.key,
    this.initialTabIndex,
    this.config,
  });

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _selectedIndex = 0;
  List<BottomNavItem> _navItems = [];
  late PageController _pageController;
  late MainScreenConfig _config;
  UserProvider? _observedUserProvider;
  MoodStorageService? _moodStorageService;
  bool _isMoodSyncInProgress = false;
  bool? _lastKnownOnlineState;
  List<MoodQueueItem> _pendingMoodQueue = const [];
  int? _currentlySyncingMoodId;
  final Set<int> _failedMoodIds = <int>{};
  Timer? _syncStartDelayTimer;
  bool _showSyncCompleteBanner = false;
  Timer? _syncCompleteBannerHideTimer;

  @override
  void initState() {
    super.initState();
    _initializeConfig();
    _pageController = PageController(initialPage: widget.initialTabIndex ?? 0);
    _selectedIndex = widget.initialTabIndex ?? 0;
    _initializeNavigation();
    _initializeConnectivitySyncBridge();
  }

  @override
  void dispose() {
    _observedUserProvider?.removeListener(_handleConnectivityTransition);
    _syncStartDelayTimer?.cancel();
    _syncCompleteBannerHideTimer?.cancel();
    if (_moodStorageService != null) {
      unawaited(_moodStorageService!.close());
    }
    _pageController.dispose();
    super.dispose();
  }

  /// Connects global connectivity state to background sync.
  ///
  /// This listens to [UserProvider] network transitions and triggers a focused
  /// sync of offline mood records once the device comes back online.
  void _initializeConnectivitySyncBridge() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) {
        return;
      }
      final provider = Provider.of<UserProvider>(context, listen: false);
      _observedUserProvider = provider;
      _lastKnownOnlineState = provider.isDeviceOnline;
      _moodStorageService = MoodStorageService(
        connectivityRouter: ConnectivityRouterService(
          isOnline: () async => provider.isDeviceOnline,
        ),
        currentUserIdProvider: () async => provider.user?.id,
        canUseOfflineFallback: () async => provider.offlineModeEnabled,
      );
      provider.addListener(_handleConnectivityTransition);

      // On cold start, also recover and schedule any existing queue.
      final userId = provider.user?.id;
      if (provider.isDeviceOnline && userId != null) {
        unawaited(_prepareQueuedSync(userId: userId));
      }
    });
  }

  Future<void> _handleConnectivityTransition() async {
    final provider = _observedUserProvider;
    if (provider == null) {
      return;
    }

    final isOnlineNow = provider.isDeviceOnline;
    final transitionedToOnline =
        _lastKnownOnlineState == false && isOnlineNow == true;
    _lastKnownOnlineState = isOnlineNow;

    if (isOnlineNow == false) {
      _syncStartDelayTimer?.cancel();
      setState(() {
        _currentlySyncingMoodId = null;
        _isMoodSyncInProgress = false;
      });
      return;
    }

    if (!transitionedToOnline) {
      return;
    }

    final userId = provider.user?.id;
    if (userId == null || _moodStorageService == null) {
      return;
    }

    await _prepareQueuedSync(userId: userId);
  }

  /// Loads pending local moods and schedules delayed sync after reconnect.
  ///
  /// UX behavior:
  /// 1. Show a blue banner with pending queue state.
  /// 2. Wait 30 seconds before first sync.
  /// 3. Process one item every 15 seconds until queue is empty.
  Future<void> _prepareQueuedSync({required int userId}) async {
    if (_moodStorageService == null) {
      return;
    }
    _syncStartDelayTimer?.cancel();
    final queue = await _moodStorageService!.getPendingMoodQueue(userId: userId);
    if (!mounted || queue.isEmpty) {
      return;
    }

    setState(() {
      _pendingMoodQueue = queue;
      _failedMoodIds.clear();
      _currentlySyncingMoodId = null;
      _showSyncCompleteBanner = false;
    });

    _syncStartDelayTimer = Timer(const Duration(seconds: 30), () {
      unawaited(_runQueuedSyncCycle(userId: userId));
    });
  }

  /// Processes queued moods sequentially with a 15-second pacing interval.
  Future<void> _runQueuedSyncCycle({required int userId}) async {
    if (_isMoodSyncInProgress || _moodStorageService == null) {
      return;
    }
    _isMoodSyncInProgress = true;

    try {
      while (mounted && _pendingMoodQueue.isNotEmpty) {
        final provider = _observedUserProvider;
        if (provider == null || !provider.isDeviceOnline) {
          break;
        }

        final item = _pendingMoodQueue.first;
        setState(() {
          _currentlySyncingMoodId = item.localId;
          _failedMoodIds.remove(item.localId);
        });

        final synced = await _moodStorageService!.syncQueuedMoodItemById(
          userId: userId,
          localId: item.localId,
        );

        if (!mounted) {
          break;
        }

        setState(() {
          _currentlySyncingMoodId = null;
          if (synced) {
            _pendingMoodQueue = _pendingMoodQueue
                .where((queued) => queued.localId != item.localId)
                .toList();
          } else {
            _failedMoodIds.add(item.localId);
            // Keep failed item visible and move it to the end for later retry.
            if (_pendingMoodQueue.length > 1) {
              final nextQueue = List<MoodQueueItem>.from(_pendingMoodQueue);
              nextQueue.removeAt(0);
              nextQueue.add(item);
              _pendingMoodQueue = nextQueue;
            }
          }
        });

        if (_pendingMoodQueue.isEmpty) {
          _showSyncCompleteToastBanner();
          break;
        }

        await Future<void>.delayed(const Duration(seconds: 15));
      }
    } catch (_) {
      // Best-effort sync remains non-fatal to app flow.
    } finally {
      _isMoodSyncInProgress = false;
    }
  }

  void _showSyncCompleteToastBanner() {
    _syncCompleteBannerHideTimer?.cancel();
    if (!mounted) {
      return;
    }
    setState(() {
      _showSyncCompleteBanner = true;
    });
    _syncCompleteBannerHideTimer = Timer(const Duration(seconds: 5), () {
      if (!mounted) {
        return;
      }
      setState(() {
        _showSyncCompleteBanner = false;
      });
    });
  }

  /// Initialize the MainScreenConfig object.
  void _initializeConfig() {
    final userProvider = Provider.of<UserProvider>(context, listen: false);

    if (widget.config != null) {
      _config = widget.config!;
    } else {
      final user = userProvider.user;

      // Check if user data is missing or invalid
      if (user == null || user.role.isEmpty || user.id <= 0) {
        _redirectToLoginWithMessage('Please log in again');
        return;
      }

      final role = user.role;
      final userId = user.id;
      final patientId = user.patientId;
      final caregiverId = user.caregiverId;

      _config = MainScreenConfig(
        userRole: role,
        userId: userId,
        patientId: patientId,
        caregiverId: caregiverId,
      );
    }
  }

  /// Redirect to login screen with a message.
  void _redirectToLoginWithMessage(String message) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Clear user data
      final userProvider = Provider.of<UserProvider>(context, listen: false);
      userProvider.clearUser();

      // Show message
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message)),
        );

        // Navigate to login
        context.go('/login');
      }
    });
  }

  /// Initialize the navigation items.
  void _initializeNavigation() {
    setState(() {
      _navItems = _config.getNavItems();
      // Ensure selected index is within bounds
      if (_selectedIndex >= _navItems.length) {
        _selectedIndex = 0;
      }
    });
  }

  /// Handle bottom nav bar item tap.
  void _onItemTapped(int index) {
    final navItem = _navItems[index];

    // Check if onPress callback exists and call it
    if (navItem.onPress != null) {
      navItem.onPress!(context, (context) => Container());
      // Don't change screen if only onPress is present
      return;
    }

    // Only change screen if there's an actual screen to navigate to
    if (navItem.screen != null) {
      setState(() {
        _selectedIndex = index;
      });

      if (_config.enablePageAnimation) {
        _pageController.animateToPage(
          index,
          duration: _config.animationDuration,
          curve: _config.animationCurve,
        );
      } else {
        _pageController.jumpToPage(index);
      }
    }
  }

  void _onPageChanged(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Consumer<UserProvider>(
      builder: (context, userProvider, child) {
        // Check if user data is missing or invalid
        final currentUser = userProvider.user;
        if (widget.config == null && (currentUser == null || currentUser.role.isEmpty || currentUser.id <= 0)) {
          // Return a loading screen while redirecting
          _redirectToLoginWithMessage('Please log in again');
          return const Scaffold(
            body: Center(
              child: CircularProgressIndicator(),
            ),
          );
        }

        // Update configuration if user changes
        final currentRole = currentUser?.role ?? '';
        final currentUserId = currentUser?.id ?? 0;

        if (widget.config == null && (_config.userRole != currentRole || _config.userId != currentUserId)) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _initializeConfig();
            _initializeNavigation();
          });
        }
        
        return Scaffold(
          backgroundColor: _config.backgroundColor,
          appBar: _config.showAppBar ? AppBar(
            title: Text(_config.appBarTitle ?? 'CareConnect'),
            backgroundColor: _config.primaryColor ?? Theme.of(context).primaryColor,
            foregroundColor: Colors.white,
            actions: _config.appBarActions,
          ) : null,
          // BNS 5: Global Banners (No Internet & Offline Mode)
          body: Column(
            children: [
              // Hardware Connection Lost
              if (!userProvider.isDeviceOnline)
                _buildGlobalNoInternetBanner(theme)
              else if (_showSyncCompleteBanner)
                _buildSyncCompleteBanner(theme)
              else if (_pendingMoodQueue.isNotEmpty)
                _buildQueuedSyncBanner(theme)

              // BNS 5 offline mode Banner
              else if (!userProvider.offlineModeEnabled)
                _buildGlobalOfflineBanner(context),

              // The actual tab content
              Expanded(
                child: PageView.builder(
                  controller: _pageController,
                  onPageChanged: _onPageChanged,
                  itemCount: _navItems.length,
                  itemBuilder: (context, index) {
                    return _navItems[index].screen;
                  },
                ),
              ),
            ],
          ),
          bottomNavigationBar: _buildBottomNavigationBar(),
        );
      },
    );
  }

  Widget _buildGlobalNoInternetBanner(ThemeData theme) {
    return Container(
      width: double.infinity,
      color: theme.colorScheme.error, // Use a solid error color (usually Red)
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      child: Row(
        children: [
          const Icon(Icons.wifi_off, color: Colors.white),
          const SizedBox(width: 12),
          const Expanded(
            child: Text(
              'No Internet Connection.',
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSyncCompleteBanner(ThemeData theme) {
    return Container(
      width: double.infinity,
      color: Colors.green.shade600,
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
      child: const Row(
        children: [
          Icon(Icons.check_circle_outline, color: Colors.white),
          SizedBox(width: 12),
          Expanded(
            child: Text(
              'Sync complete',
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildQueuedSyncBanner(ThemeData theme) {
    final queuedCount = _pendingMoodQueue.length;
    final statusText = _currentlySyncingMoodId == null
        ? 'Queued items: $queuedCount. Sync starts in 30s, then every 15s.'
        : 'Syncing 1 of $queuedCount. Tap to review queue.';

    return Material(
      color: Colors.blue.shade600,
      child: InkWell(
        onTap: _openQueuedSyncSheet,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
          child: Row(
            children: [
              const Icon(Icons.sync, color: Colors.white),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  statusText,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              TextButton(
                onPressed: _openQueuedSyncSheet,
                child: const Text(
                  'View Queue',
                  style: TextStyle(color: Colors.white),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _openQueuedSyncSheet() {
    if (!mounted) {
      return;
    }
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) {
        return StreamBuilder<int>(
          stream: Stream<int>.periodic(const Duration(seconds: 1), (x) => x),
          builder: (context, _) {
            return SafeArea(
              child: SizedBox(
                height: MediaQuery.of(sheetContext).size.height * 0.65,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Padding(
                      padding: EdgeInsets.fromLTRB(16, 16, 16, 8),
                      child: Text(
                        'Queued Mood Sync Items',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: Text(
                        'Only non-sensitive fields are shown. Items currently syncing cannot be deleted.',
                      ),
                    ),
                    const SizedBox(height: 10),
                    Expanded(
                      child: _pendingMoodQueue.isEmpty
                          ? const Center(child: Text('No queued items'))
                          : ListView.separated(
                              itemCount: _pendingMoodQueue.length,
                              separatorBuilder: (_, i) =>
                                  const Divider(height: 1),
                              itemBuilder: (context, index) {
                                final item = _pendingMoodQueue[index];
                                final isSyncing =
                                    item.localId == _currentlySyncingMoodId;
                                final isFailed = _failedMoodIds.contains(item.localId);
                                final status = isSyncing
                                    ? 'Syncing now'
                                    : isFailed
                                        ? 'Failed (will retry)'
                                        : 'Queued';

                                return ListTile(
                                  leading: CircleAvatar(
                                    child: Text('${index + 1}'),
                                  ),
                                  title: Text('Mood: ${item.label} (${item.score}/10)'),
                                  subtitle: Text(
                                    'Created: ${_formatQueueTimestamp(item.createdAt)} \nStatus: $status',
                                  ),
                                  isThreeLine: true,
                                  trailing: IconButton(
                                    icon: const Icon(Icons.delete_outline),
                                    onPressed: isSyncing
                                        ? null
                                        : () => _deleteQueuedMoodItem(item),
                                  ),
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _deleteQueuedMoodItem(MoodQueueItem item) async {
    if (_moodStorageService == null || _currentlySyncingMoodId == item.localId) {
      return;
    }
    final removed = await _moodStorageService!.deleteQueuedMoodItem(
      localId: item.localId,
    );
    if (!mounted || !removed) {
      return;
    }
    setState(() {
      _pendingMoodQueue = _pendingMoodQueue
          .where((queued) => queued.localId != item.localId)
          .toList();
      _failedMoodIds.remove(item.localId);
    });
  }

  String _formatQueueTimestamp(DateTime value) {
    final local = value.toLocal();
    final twoDigitMonth = local.month.toString().padLeft(2, '0');
    final twoDigitDay = local.day.toString().padLeft(2, '0');
    final twoDigitHour = local.hour.toString().padLeft(2, '0');
    final twoDigitMinute = local.minute.toString().padLeft(2, '0');
    return '$twoDigitMonth/$twoDigitDay ${local.year} $twoDigitHour:$twoDigitMinute';
  }
  /// Build the global offline mode warning banner
  Widget _buildGlobalOfflineBanner(BuildContext context) {
    
    return MaterialBanner(
      elevation: 0,
      backgroundColor: Colors.amber.shade50,
      leading: Icon(Icons.cloud_off, color: Colors.amber.shade900),
      content: Text(
        'Offline Mode Disabled',
        style: TextStyle(
          color: Colors.amber.shade900,
          fontSize: 13,
          fontWeight: FontWeight.bold,
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => _onItemTapped(_navItems.length - 1),
          child: Icon(
            Icons.settings, 
            color: Colors.amber.shade900,
            size: 24, // Matches the standard emoji scale
          ),
        ),
      ],

    );
  }

  /// Build the bottom navigation bar
  Widget _buildBottomNavigationBar() {
   final t = AppLocalizations.of(context)!;
    return Container(
      decoration: BoxDecoration(
        boxShadow: [
          BoxShadow(
            color: Theme.of(context).shadowColor.withOpacity(0.1),
            blurRadius: 10,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: BottomNavigationBar(
        currentIndex: _selectedIndex,
        onTap: _onItemTapped,
        type: BottomNavigationBarType.fixed,
        backgroundColor: Theme.of(context).scaffoldBackgroundColor,
        selectedItemColor: _config.primaryColor ?? Theme.of(context).primaryColor,
        unselectedItemColor: Colors.grey[600],
        selectedFontSize: 12,
        unselectedFontSize: 12,
        iconSize: 24,
        items: _navItems.map((item) {
          return BottomNavigationBarItem(
            icon: Icon(item.icon),
            activeIcon: Icon(item.activeIcon ?? item.icon),
           label: item.localizedLabel(t),
          );
        }).toList(),
      ),
    );
  }
}

/// Extension to provide easy navigation to specific tabs
extension MainScreenNavigation on BuildContext {
  void navigateToMainScreen({
    int? tabIndex,
    MainScreenConfig? config,
  }) {
    Navigator.of(this).pushReplacement(
      MaterialPageRoute(
        builder: (context) => MainScreen(
          initialTabIndex: tabIndex,
          config: config,
        ),
      ),
    );
  }

  void navigateToMainScreenWithConfig(MainScreenConfig config, {int? tabIndex}) {
    Navigator.of(this).pushReplacement(
      MaterialPageRoute(
        builder: (context) => MainScreen(
          initialTabIndex: tabIndex,
          config: config,
        ),
      ),
    );
  }
}
