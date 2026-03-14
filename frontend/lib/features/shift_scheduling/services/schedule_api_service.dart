import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';
import 'package:care_connect_app/services/api_client.dart';

class ScheduleApiService {
  final ApiClient _apiClient = ApiClient.instance;

  Future<List<ScheduledVisit>> getMonthSchedule(
    int caregiverId,
    int year,
    int month,
  ) async {
    try {
      final data = await _apiClient.getJson<Map<String, dynamic>>(
        '/v1/api/scheduled-visits/caregiver/$caregiverId/calendar/month',
        query: {'year': year, 'month': month},
        parser: (json) => json as Map<String, dynamic>,
      );

      final days = data['days'] as Map<String, dynamic>? ?? {};
      List<ScheduledVisit> visits = [];

      days.forEach((dateStr, dayData) {
        if (dayData is Map && dayData.containsKey('visits')) {
          final dayVisits = dayData['visits'] as List;
          visits.addAll(
            dayVisits.map(
              (v) => ScheduledVisit.fromJson(v as Map<String, dynamic>),
            ),
          );
        }
      });
      return visits;
    } catch (e) {
      print('Error fetching month schedule: $e');
      return [];
    }
  }

  Future<List<ScheduledVisit>> getWeekSchedule(
    int caregiverId,
    DateTime weekStart,
  ) async {
    try {
      final weekStartStr = weekStart.toIso8601String().split('T')[0];
      final data = await _apiClient.getJson<Map<String, dynamic>>(
        '/v1/api/scheduled-visits/caregiver/$caregiverId/calendar/week',
        query: {'weekStart': weekStartStr},
        parser: (json) => json as Map<String, dynamic>,
      );

      List<ScheduledVisit> visits = [];
      data.forEach((dateStr, dayData) {
        if (dayData is Map && dayData.containsKey('visits')) {
          final dayVisits = dayData['visits'] as List;
          visits.addAll(
            dayVisits.map(
              (v) => ScheduledVisit.fromJson(v as Map<String, dynamic>),
            ),
          );
        }
      });
      return visits;
    } catch (e) {
      print('Error fetching week schedule: $e');
      return [];
    }
  }

  Future<List<ScheduledVisit>> getDaySchedule(
    int caregiverId,
    DateTime date,
  ) async {
    try {
      final dateString =
          '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
      final visits = await _apiClient.getJson<List<dynamic>>(
        '/v1/api/scheduled-visits/caregiver/$caregiverId/date/$dateString',
        parser: (json) => json as List<dynamic>,
      );

      return visits
          .map((v) => ScheduledVisit.fromJson(v as Map<String, dynamic>))
          .toList();
    } catch (e) {
      print('Error fetching day schedule: $e');
      return [];
    }
  }

  Future<VisitConflict?> checkConflicts(
    int caregiverId,
    Map<String, dynamic> visitRequest,
  ) async {
    try {
      final data = await _apiClient.postJson<Map<String, dynamic>>(
        '/v1/api/scheduled-visits/caregiver/$caregiverId/check-conflicts',
        body: visitRequest,
        parser: (json) => json as Map<String, dynamic>,
      );

      if (data['hasConflicts'] == true) {
        return VisitConflict(
          conflictingVisits: (data['conflictingVisits'] as List? ?? [])
              .map((v) => ScheduledVisit.fromJson(v as Map<String, dynamic>))
              .toList(),
          conflictType: data['conflictType'] ?? 'unknown',
          message: data['conflictMessages']?.join(', ') ?? 'Conflict detected',
        );
      }
      return null;
    } catch (e) {
      print('Error checking conflicts: $e');
      return null;
    }
  }

  Future<List<ScheduledVisitAudit>> getAuditHistory(int visitId) async {
    try {
      final audits = await _apiClient.getJson<List<dynamic>>(
        '/v1/api/scheduled-visits/$visitId/audit-history',
        parser: (json) => json as List<dynamic>,
      );

      return audits
          .map((a) => ScheduledVisitAudit.fromJson(a as Map<String, dynamic>))
          .toList();
    } catch (e) {
      print('Error fetching audit history: $e');
      return [];
    }
  }
}
