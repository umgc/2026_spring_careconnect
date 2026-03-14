import 'package:flutter/material.dart';

class ScheduledVisit {
  final int id;
  final int caregiverId;
  final int patientId;
  final String patientName;
  final String serviceType;
  final DateTime scheduledDate;
  final TimeOfDay scheduledTime;
  final int durationMinutes;
  final String priority;
  final String? notes;
  final String status;
  final DateTime createdAt;
  final DateTime updatedAt;

  ScheduledVisit({
    required this.id,
    required this.caregiverId,
    required this.patientId,
    required this.patientName,
    required this.serviceType,
    required this.scheduledDate,
    required this.scheduledTime,
    required this.durationMinutes,
    required this.priority,
    this.notes,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
  });

  factory ScheduledVisit.fromJson(Map<String, dynamic> json) {
    return ScheduledVisit(
      id: json['id'] ?? 0,
      caregiverId: json['caregiverId'] ?? 0,
      patientId: json['patientId'] ?? 0,
      patientName: json['patientName'] ?? '',
      serviceType: json['serviceType'] ?? '',
      scheduledDate: DateTime.parse(
        json['scheduledDate'] ?? DateTime.now().toString(),
      ),
      scheduledTime: _parseTimeOfDay(json['scheduledTime'] ?? '09:00'),
      durationMinutes: json['durationMinutes'] ?? 60,
      priority: json['priority'] ?? 'Normal',
      notes: json['notes'],
      status: json['status'] ?? 'Scheduled',
      createdAt: DateTime.parse(json['createdAt'] ?? DateTime.now().toString()),
      updatedAt: DateTime.parse(json['updatedAt'] ?? DateTime.now().toString()),
    );
  }

  static TimeOfDay _parseTimeOfDay(String timeString) {
    try {
      final parts = timeString.split(':');
      return TimeOfDay(hour: int.parse(parts[0]), minute: int.parse(parts[1]));
    } catch (e) {
      return TimeOfDay.now();
    }
  }

  String getEndTime() {
    final startMinutes = scheduledTime.hour * 60 + scheduledTime.minute;
    final endMinutes = startMinutes + durationMinutes;
    final endHour = endMinutes ~/ 60;
    final endMinute = endMinutes % 60;
    return '${endHour.toString().padLeft(2, '0')}:${endMinute.toString().padLeft(2, '0')}';
  }

  Color getPriorityColor() {
    switch (priority.toLowerCase()) {
      case 'high':
        return Colors.red;
      case 'medium':
        return Colors.orange;
      case 'low':
        return Colors.green;
      default:
        return Colors.blue;
    }
  }

  Color getStatusColor() {
    switch (status.toLowerCase()) {
      case 'completed':
        return Colors.green;
      case 'cancelled':
        return Colors.grey;
      case 'scheduled':
        return Colors.blue;
      default:
        return Colors.amber;
    }
  }
}

class VisitConflict {
  final List<ScheduledVisit> conflictingVisits;
  final String conflictType; // 'caregiver' or 'patient'
  final String message;

  VisitConflict({
    required this.conflictingVisits,
    required this.conflictType,
    required this.message,
  });
}

class ScheduledVisitAudit {
  final int id;
  final int visitId;
  final String action;
  final String? changedField;
  final String? oldValue;
  final String? newValue;
  final DateTime changedAt;
  final String changedBy;

  ScheduledVisitAudit({
    required this.id,
    required this.visitId,
    required this.action,
    this.changedField,
    this.oldValue,
    this.newValue,
    required this.changedAt,
    required this.changedBy,
  });

  factory ScheduledVisitAudit.fromJson(Map<String, dynamic> json) {
    return ScheduledVisitAudit(
      id: json['id'] ?? 0,
      visitId: json['visitId'] ?? 0,
      action: json['action'] ?? '',
      changedField: json['changedField'],
      oldValue: json['oldValue'],
      newValue: json['newValue'],
      changedAt: DateTime.parse(json['changedAt'] ?? DateTime.now().toString()),
      changedBy: json['changedBy'] ?? 'Unknown',
    );
  }
}
