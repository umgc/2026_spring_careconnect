import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/activities/models/client_activity_model.dart';

void main() {
  // =========================================================
  // ClientActivity.fromJson
  // =========================================================
  group('ClientActivity.fromJson', () {
    test('parses all fields from JSON', () {
      final json = {
        'id': 1,
        'name': 'Bathing',
        'category': 'ADL',
        'customIconUrl': 'https://example.com/icon.png',
        'defaultIconUrl': 'https://example.com/default.png',
        'enabled': true,
      };
      final activity = ClientActivity.fromJson(json);
      expect(activity.id, 1);
      expect(activity.name, 'Bathing');
      expect(activity.category, 'ADL');
      expect(activity.customIconUrl, 'https://example.com/icon.png');
      expect(activity.defaultIconUrl, 'https://example.com/default.png');
      expect(activity.enabled, true);
    });

    test('category is uppercased', () {
      final json = {'id': 2, 'name': 'Meal Prep', 'category': 'iadl', 'enabled': true};
      final activity = ClientActivity.fromJson(json);
      expect(activity.category, 'IADL');
    });

    test('defaults enabled to false when absent', () {
      final json = {'id': 3, 'name': 'Dressing', 'category': 'ADL'};
      final activity = ClientActivity.fromJson(json);
      expect(activity.enabled, false);
    });

    test('id parsed from string', () {
      final json = {'id': '42', 'name': 'Toileting', 'category': 'ADL', 'enabled': true};
      final activity = ClientActivity.fromJson(json);
      expect(activity.id, 42);
    });

    test('customIconUrl and defaultIconUrl are null when absent', () {
      final json = {'id': 5, 'name': 'Eating', 'category': 'ADL', 'enabled': true};
      final activity = ClientActivity.fromJson(json);
      expect(activity.customIconUrl, isNull);
      expect(activity.defaultIconUrl, isNull);
    });

    test('defaults category to ADL when absent', () {
      final json = {'id': 6, 'name': 'Test', 'enabled': true};
      final activity = ClientActivity.fromJson(json);
      expect(activity.category, 'ADL');
    });
  });

  // =========================================================
  // CompetencyScaleItem.fromJson
  // =========================================================
  group('CompetencyScaleItem.fromJson', () {
    test('parses value and label', () {
      final json = {'value': 3, 'label': 'Independent with minimal support'};
      final item = CompetencyScaleItem.fromJson(json);
      expect(item.value, 3);
      expect(item.label, 'Independent with minimal support');
    });

    test('parses value from string', () {
      final json = {'value': '5', 'label': 'Fully independent'};
      final item = CompetencyScaleItem.fromJson(json);
      expect(item.value, 5);
    });

    test('label defaults to empty string when null', () {
      final json = {'value': 1, 'label': null};
      final item = CompetencyScaleItem.fromJson(json);
      expect(item.label, '');
    });
  });

  // =========================================================
  // ActivityLogEntry.fromJson
  // =========================================================
  group('ActivityLogEntry.fromJson', () {
    test('parses all fields with ISO string createdAt', () {
      final json = {
        'id': 10,
        'clientId': 5,
        'activityId': 100,
        'activityName': 'Eating',
        'competencyScore': 4,
        'satisfactionRating': 3,
        'notes': 'Client did well',
        'createdAt': '2026-03-10T09:00:00',
      };
      final entry = ActivityLogEntry.fromJson(json);
      expect(entry.id, 10);
      expect(entry.clientId, 5);
      expect(entry.activityId, 100);
      expect(entry.activityName, 'Eating');
      expect(entry.competencyScore, 4);
      expect(entry.satisfactionRating, 3);
      expect(entry.notes, 'Client did well');
      expect(entry.createdAt, DateTime(2026, 3, 10, 9, 0, 0));
    });

    test('parses createdAt from array format (Java LocalDateTime serialization)', () {
      final json = {
        'id': 11,
        'clientId': 5,
        'activityId': 101,
        'competencyScore': 3,
        'createdAt': [2026, 3, 10, 9, 30, 0],
      };
      final entry = ActivityLogEntry.fromJson(json);
      expect(entry.createdAt.year, 2026);
      expect(entry.createdAt.month, 3);
      expect(entry.createdAt.day, 10);
      expect(entry.createdAt.hour, 9);
      expect(entry.createdAt.minute, 30);
    });

    test('activityName is null when absent', () {
      final json = {
        'id': 12,
        'clientId': 5,
        'activityId': 102,
        'competencyScore': 2,
        'createdAt': '2026-03-10T10:00:00',
      };
      final entry = ActivityLogEntry.fromJson(json);
      expect(entry.activityName, isNull);
    });

    test('satisfactionRating is null when absent', () {
      final json = {
        'id': 13,
        'clientId': 5,
        'activityId': 103,
        'competencyScore': 5,
        'createdAt': '2026-03-10T10:00:00',
      };
      final entry = ActivityLogEntry.fromJson(json);
      expect(entry.satisfactionRating, isNull);
    });

    test('competencyScore parsed from string', () {
      final json = {
        'id': 14,
        'clientId': 5,
        'activityId': 104,
        'competencyScore': '3',
        'createdAt': '2026-03-10T10:00:00',
      };
      final entry = ActivityLogEntry.fromJson(json);
      expect(entry.competencyScore, 3);
    });
  });

  // =========================================================
  // BehavioralIncidentEntry.fromJson
  // =========================================================
  group('BehavioralIncidentEntry.fromJson', () {
    test('parses all fields with ISO string occurredAt', () {
      final json = {
        'id': 20,
        'clientId': 5,
        'caregiverId': 99,
        'observedBehavior': 'Client was hitting walls',
        'occurredAt': '2026-03-10T14:00:00',
        'triggerNotes': 'Before lunch',
      };
      final entry = BehavioralIncidentEntry.fromJson(json);
      expect(entry.id, 20);
      expect(entry.clientId, 5);
      expect(entry.caregiverId, 99);
      expect(entry.observedBehavior, 'Client was hitting walls');
      expect(entry.occurredAt, DateTime(2026, 3, 10, 14, 0, 0));
      expect(entry.triggerNotes, 'Before lunch');
    });

    test('parses occurredAt from array format', () {
      final json = {
        'id': 21,
        'clientId': 5,
        'caregiverId': 99,
        'observedBehavior': 'Screaming',
        'occurredAt': [2026, 3, 11, 8, 0, 0],
      };
      final entry = BehavioralIncidentEntry.fromJson(json);
      expect(entry.occurredAt.year, 2026);
      expect(entry.occurredAt.month, 3);
      expect(entry.occurredAt.day, 11);
    });

    test('accepts snake_case keys (occurred_at, observed_behavior)', () {
      final json = {
        'id': 22,
        'clientId': 5,
        'caregiverId': 99,
        'observed_behavior': 'Pacing',
        'occurred_at': '2026-03-10T15:00:00',
        'trigger_notes': 'None',
      };
      final entry = BehavioralIncidentEntry.fromJson(json);
      expect(entry.observedBehavior, 'Pacing');
      expect(entry.triggerNotes, 'None');
    });

    test('triggerNotes is null when absent', () {
      final json = {
        'id': 23,
        'clientId': 5,
        'caregiverId': 99,
        'observedBehavior': 'Yelling',
        'occurredAt': '2026-03-10T14:00:00',
      };
      final entry = BehavioralIncidentEntry.fromJson(json);
      expect(entry.triggerNotes, isNull);
    });
  });

  // =========================================================
  // IncidentReportEntry.fromJson
  // =========================================================
  group('IncidentReportEntry.fromJson', () {
    test('parses all fields including actions from actionTaken objects', () {
      final json = {
        'id': 30,
        'clientId': 5,
        'caregiverId': 99,
        'incidentType': 'FALL',
        'occurredAt': '2026-03-10T10:00:00',
        'location': 'Bathroom',
        'triggerNotes': 'Slippery floor',
        'outcome': 'No injury',
        'createdAt': '2026-03-10T10:05:00',
        'actions': [
          {'actionTaken': 'Called supervisor'},
          {'actionTaken': 'Applied first aid'},
        ],
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.id, 30);
      expect(entry.incidentType, 'FALL');
      expect(entry.location, 'Bathroom');
      expect(entry.triggerNotes, 'Slippery floor');
      expect(entry.outcome, 'No injury');
      expect(entry.actions, ['Called supervisor', 'Applied first aid']);
    });

    test('parses actions from plain string list', () {
      final json = {
        'id': 31,
        'clientId': 5,
        'caregiverId': 99,
        'incidentType': 'ELOPEMENT',
        'occurredAt': '2026-03-10T11:00:00',
        'location': 'Front door',
        'outcome': 'Client returned safely',
        'createdAt': '2026-03-10T11:05:00',
        'actions': ['Notified family', 'Completed safety check'],
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.actions, ['Notified family', 'Completed safety check']);
    });

    test('actions is empty list when absent', () {
      final json = {
        'id': 32,
        'clientId': 5,
        'caregiverId': 99,
        'incidentType': 'SELF_HARM',
        'occurredAt': '2026-03-10T12:00:00',
        'location': 'Bedroom',
        'outcome': 'Resolved',
        'createdAt': '2026-03-10T12:05:00',
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.actions, isEmpty);
    });

    test('accepts snake_case incidentType key', () {
      final json = {
        'id': 33,
        'clientId': 5,
        'caregiverId': 99,
        'incident_type': 'MEDICAL_EVENT',
        'occurredAt': '2026-03-10T13:00:00',
        'location': 'Living room',
        'outcome': 'EMS called',
        'createdAt': '2026-03-10T13:05:00',
        'actions': [],
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.incidentType, 'MEDICAL_EVENT');
    });

    test('parses all IncidentType enum values', () {
      final types = ['FALL', 'BEHAVIORAL_CRISIS', 'MEDICAL_EVENT', 'ELOPEMENT', 'SELF_HARM', 'PROPERTY_DAMAGE', 'OTHER'];
      for (final type in types) {
        final json = {
          'id': 1,
          'clientId': 5,
          'caregiverId': 99,
          'incidentType': type,
          'occurredAt': '2026-03-10T09:00:00',
          'location': 'Room',
          'outcome': 'Resolved',
          'createdAt': '2026-03-10T09:05:00',
          'actions': [],
        };
        final entry = IncidentReportEntry.fromJson(json);
        expect(entry.incidentType, type);
      }
    });

    test('triggerNotes is null when absent', () {
      final json = {
        'id': 34,
        'clientId': 5,
        'caregiverId': 99,
        'incidentType': 'OTHER',
        'occurredAt': '2026-03-10T14:00:00',
        'location': 'Hallway',
        'outcome': 'Documented',
        'createdAt': '2026-03-10T14:05:00',
        'actions': [],
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.triggerNotes, isNull);
    });

    test('createdAt parsed from array format', () {
      final json = {
        'id': 35,
        'clientId': 5,
        'caregiverId': 99,
        'incidentType': 'FALL',
        'occurredAt': '2026-03-10T09:00:00',
        'location': 'Stairs',
        'outcome': 'Minor bruising',
        'createdAt': [2026, 3, 10, 9, 5, 0],
        'actions': [],
      };
      final entry = IncidentReportEntry.fromJson(json);
      expect(entry.createdAt.year, 2026);
      expect(entry.createdAt.month, 3);
      expect(entry.createdAt.day, 10);
    });
  });

  // =========================================================
  // CompetencyTrendsResponse.fromJson
  // =========================================================
  group('CompetencyTrendsResponse.fromJson', () {
    test('parses STABLE status with empty data', () {
      final json = {'status': 'STABLE', 'weekLabels': [], 'activityTrends': []};
      final response = CompetencyTrendsResponse.fromJson(json);
      expect(response.status, 'STABLE');
      expect(response.weekLabels, isEmpty);
      expect(response.activityTrends, isEmpty);
    });

    test('parses IMPROVING status with activity trends', () {
      final json = {
        'status': 'IMPROVING',
        'weekLabels': ['2026-02-23', '2026-03-02'],
        'activityTrends': [
          {
            'activityId': 1,
            'activityName': 'Bathing',
            'dataPoints': [
              {'weekStartDate': '2026-02-23', 'averageCompetencyScore': 3.0, 'logCount': 2},
              {'weekStartDate': '2026-03-02', 'averageCompetencyScore': 4.5, 'logCount': 3},
            ],
          }
        ],
      };
      final response = CompetencyTrendsResponse.fromJson(json);
      expect(response.status, 'IMPROVING');
      expect(response.weekLabels, ['2026-02-23', '2026-03-02']);
      expect(response.activityTrends, hasLength(1));
      expect(response.activityTrends[0].activityName, 'Bathing');
      expect(response.activityTrends[0].dataPoints, hasLength(2));
      expect(response.activityTrends[0].dataPoints[0].averageCompetencyScore, 3.0);
      expect(response.activityTrends[0].dataPoints[1].logCount, 3);
    });

    test('parses DECLINING status', () {
      final json = {'status': 'DECLINING', 'weekLabels': [], 'activityTrends': []};
      final response = CompetencyTrendsResponse.fromJson(json);
      expect(response.status, 'DECLINING');
    });

    test('defaults status to STABLE when null', () {
      final json = {'status': null, 'weekLabels': [], 'activityTrends': []};
      final response = CompetencyTrendsResponse.fromJson(json);
      expect(response.status, 'STABLE');
    });
  });

  // =========================================================
  // CompetencyWeekDataPoint.fromJson
  // =========================================================
  group('CompetencyWeekDataPoint.fromJson', () {
    test('parses all fields', () {
      final json = {'weekStartDate': '2026-03-02', 'averageCompetencyScore': 3.75, 'logCount': 4};
      final point = CompetencyWeekDataPoint.fromJson(json);
      expect(point.weekStartDate, '2026-03-02');
      expect(point.averageCompetencyScore, 3.75);
      expect(point.logCount, 4);
    });

    test('averageCompetencyScore defaults to 0.0 when null', () {
      final json = {'weekStartDate': '2026-03-02', 'averageCompetencyScore': null, 'logCount': 0};
      final point = CompetencyWeekDataPoint.fromJson(json);
      expect(point.averageCompetencyScore, 0.0);
    });
  });

  // =========================================================
  // BehavioralTrendsResponse.fromJson
  // =========================================================
  group('BehavioralTrendsResponse.fromJson', () {
    test('parses STABLE with empty data', () {
      final json = {'trend': 'STABLE', 'weeklyCounts': [], 'topKeywords': []};
      final response = BehavioralTrendsResponse.fromJson(json);
      expect(response.trend, 'STABLE');
      expect(response.weeklyCounts, isEmpty);
      expect(response.topKeywords, isEmpty);
    });

    test('parses UP trend with weekly counts and keywords', () {
      final json = {
        'trend': 'UP',
        'weeklyCounts': [
          {'weekStartDate': '2026-02-23', 'incidentCount': 2},
          {'weekStartDate': '2026-03-02', 'incidentCount': 5},
        ],
        'topKeywords': ['agitation', 'yelling', 'hitting'],
      };
      final response = BehavioralTrendsResponse.fromJson(json);
      expect(response.trend, 'UP');
      expect(response.weeklyCounts, hasLength(2));
      expect(response.weeklyCounts[0].weekStartDate, '2026-02-23');
      expect(response.weeklyCounts[1].incidentCount, 5);
      expect(response.topKeywords, ['agitation', 'yelling', 'hitting']);
    });

    test('parses DOWN trend', () {
      final json = {'trend': 'DOWN', 'weeklyCounts': [], 'topKeywords': []};
      final response = BehavioralTrendsResponse.fromJson(json);
      expect(response.trend, 'DOWN');
    });

    test('defaults trend to STABLE when null', () {
      final json = {'trend': null, 'weeklyCounts': [], 'topKeywords': []};
      final response = BehavioralTrendsResponse.fromJson(json);
      expect(response.trend, 'STABLE');
    });
  });

  // =========================================================
  // BehavioralWeekCount.fromJson
  // =========================================================
  group('BehavioralWeekCount.fromJson', () {
    test('parses weekStartDate and incidentCount', () {
      final json = {'weekStartDate': '2026-03-02', 'incidentCount': 3};
      final wc = BehavioralWeekCount.fromJson(json);
      expect(wc.weekStartDate, '2026-03-02');
      expect(wc.incidentCount, 3);
    });

    test('incidentCount from string', () {
      final json = {'weekStartDate': '2026-03-02', 'incidentCount': '7'};
      final wc = BehavioralWeekCount.fromJson(json);
      expect(wc.incidentCount, 7);
    });
  });

  // =========================================================
  // ParticipationResponse.fromJson
  // =========================================================
  group('ParticipationResponse.fromJson', () {
    test('parses status and empty lists', () {
      final json = {'status': 'STABLE', 'weeklyCounts': [], 'activities': []};
      final response = ParticipationResponse.fromJson(json);
      expect(response.status, 'STABLE');
      expect(response.weeklyCounts, isEmpty);
      expect(response.activities, isEmpty);
    });

    test('parses IMPROVING status with activity breakdown', () {
      final json = {
        'status': 'IMPROVING',
        'weeklyCounts': [
          {'weekStartDate': '2026-03-02', 'totalLogs': 10},
        ],
        'activities': [
          {
            'activityId': 1,
            'activityName': 'Bathing',
            'category': 'ADL',
            'totalLogsInPeriod': 5,
            'lastLoggedAt': '2026-03-10T09:00:00',
            'noRecentActivity': false,
          },
          {
            'activityId': 2,
            'activityName': 'Meal Preparation',
            'category': 'IADL',
            'totalLogsInPeriod': 5,
            'lastLoggedAt': null,
            'noRecentActivity': true,
          },
        ],
      };
      final response = ParticipationResponse.fromJson(json);
      expect(response.status, 'IMPROVING');
      expect(response.activities, hasLength(2));
      expect(response.activities[0].activityName, 'Bathing');
      expect(response.activities[0].category, 'ADL');
      expect(response.activities[0].noRecentActivity, false);
      expect(response.activities[1].category, 'IADL');
      expect(response.activities[1].noRecentActivity, true);
      expect(response.activities[1].lastLoggedAt, isNull);
    });
  });

  // =========================================================
  // ActivityParticipation.fromJson
  // =========================================================
  group('ActivityParticipation.fromJson', () {
    test('parses lastLoggedAt from ISO string', () {
      final json = {
        'activityId': 1,
        'activityName': 'Dressing',
        'category': 'ADL',
        'totalLogsInPeriod': 3,
        'lastLoggedAt': '2026-03-09T08:30:00',
        'noRecentActivity': false,
      };
      final ap = ActivityParticipation.fromJson(json);
      expect(ap.lastLoggedAt, DateTime(2026, 3, 9, 8, 30, 0));
      expect(ap.noRecentActivity, false);
    });

    test('lastLoggedAt is null when absent', () {
      final json = {
        'activityId': 2,
        'activityName': 'Shopping',
        'category': 'IADL',
        'totalLogsInPeriod': 0,
        'lastLoggedAt': null,
        'noRecentActivity': true,
      };
      final ap = ActivityParticipation.fromJson(json);
      expect(ap.lastLoggedAt, isNull);
      expect(ap.noRecentActivity, true);
    });

    test('category uppercased', () {
      final json = {
        'activityId': 3,
        'activityName': 'Eating',
        'category': 'adl',
        'totalLogsInPeriod': 2,
        'noRecentActivity': false,
      };
      final ap = ActivityParticipation.fromJson(json);
      expect(ap.category, 'ADL');
    });

    test('lastLoggedAt from array format', () {
      final json = {
        'activityId': 4,
        'activityName': 'Mobility/Ambulation',
        'category': 'ADL',
        'totalLogsInPeriod': 4,
        'lastLoggedAt': [2026, 3, 8, 10, 0, 0],
        'noRecentActivity': false,
      };
      final ap = ActivityParticipation.fromJson(json);
      expect(ap.lastLoggedAt?.year, 2026);
      expect(ap.lastLoggedAt?.month, 3);
      expect(ap.lastLoggedAt?.day, 8);
    });
  });
}
