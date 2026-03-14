import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/user_provider.dart';
import '../utils/role_helper.dart';
import '../utils/permission_helper.dart';

/// Widget that shows/hides content based on user role
/// Uses existing UserProvider for state management
class RoleWidget extends StatelessWidget {
  final Widget child;
  final bool Function(String role) shouldShow;
  final Widget? fallback;

  const RoleWidget({
    Key? key,
    required this.child,
    required this.shouldShow,
    this.fallback,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<UserProvider>(
      builder: (context, userProvider, _) {
        final user = userProvider.userSession;
        if (user == null) return fallback ?? const SizedBox.shrink();

        if (shouldShow(user.role)) {
          return child;
        }

        return fallback ?? const SizedBox.shrink();
      },
    );
  }
}

/// Shows content only to admins
class AdminOnly extends StatelessWidget {
  final Widget child;
  final Widget? fallback;

  const AdminOnly({Key? key, required this.child, this.fallback})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return RoleWidget(
      shouldShow: RoleHelper.isAdmin,
      fallback: fallback,
      child: child,
    );
  }
}

/// Shows content only to caregivers
class CaregiverOnly extends StatelessWidget {
  final Widget child;
  final Widget? fallback;

  const CaregiverOnly({Key? key, required this.child, this.fallback})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return RoleWidget(
      shouldShow: RoleHelper.isCaregiver,
      fallback: fallback,
      child: child,
    );
  }
}

/// Shows content to caregivers and admins
class CaregiverOrAdmin extends StatelessWidget {
  final Widget child;
  final Widget? fallback;

  const CaregiverOrAdmin({Key? key, required this.child, this.fallback})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return RoleWidget(
      shouldShow: RoleHelper.isCaregiverOrAdmin,
      fallback: fallback,
      child: child,
    );
  }
}

/// Shows content only to patients
class PatientOnly extends StatelessWidget {
  final Widget child;
  final Widget? fallback;

  const PatientOnly({Key? key, required this.child, this.fallback})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return RoleWidget(
      shouldShow: RoleHelper.isPatient,
      fallback: fallback,
      child: child,
    );
  }
}

/// Shows content to everyone except family members
class NotFamilyMember extends StatelessWidget {
  final Widget child;
  final Widget? fallback;

  const NotFamilyMember({Key? key, required this.child, this.fallback})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return RoleWidget(
      shouldShow: (role) => !RoleHelper.isFamilyMember(role),
      fallback: fallback,
      child: child,
    );
  }
}

/// Button that checks permission before showing
class PermissionButton extends StatelessWidget {
  final String permission;
  final VoidCallback onPressed;
  final Widget child;
  final ButtonStyle? style;

  const PermissionButton({
    Key? key,
    required this.permission,
    required this.onPressed,
    required this.child,
    this.style,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<UserProvider>(
      builder: (context, userProvider, _) {
        final user = userProvider.userSession;
        if (user == null) return const SizedBox.shrink();

        final hasPermission = PermissionHelper.hasPermission(
          user.role,
          permission,
        );

        if (!hasPermission) return const SizedBox.shrink();

        return ElevatedButton(onPressed: onPressed, style: style, child: child);
      },
    );
  }
}

/// Icon button that checks permission before showing
class PermissionIconButton extends StatelessWidget {
  final String permission;
  final VoidCallback onPressed;
  final Icon icon;
  final String? tooltip;

  const PermissionIconButton({
    Key? key,
    required this.permission,
    required this.onPressed,
    required this.icon,
    this.tooltip,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<UserProvider>(
      builder: (context, userProvider, _) {
        final user = userProvider.userSession;
        if (user == null) return const SizedBox.shrink();

        final hasPermission = PermissionHelper.hasPermission(
          user.role,
          permission,
        );

        if (!hasPermission) return const SizedBox.shrink();

        return IconButton(onPressed: onPressed, icon: icon, tooltip: tooltip);
      },
    );
  }
}

/// Menu item that checks permission before showing
class PermissionMenuItem extends StatelessWidget {
  final String permission;
  final Widget leading;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  const PermissionMenuItem({
    Key? key,
    required this.permission,
    required this.leading,
    required this.title,
    this.subtitle,
    required this.onTap,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Consumer<UserProvider>(
      builder: (context, userProvider, _) {
        final user = userProvider.userSession;
        if (user == null) return const SizedBox.shrink();

        final hasPermission = PermissionHelper.hasPermission(
          user.role,
          permission,
        );

        if (!hasPermission) return const SizedBox.shrink();

        return ListTile(
          leading: leading,
          title: Text(title),
          subtitle: subtitle != null ? Text(subtitle!) : null,
          onTap: onTap,
        );
      },
    );
  }
}
