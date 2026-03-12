import 'dart:convert';

import 'package:care_connect_app/services/api_service.dart';
import 'package:care_connect_app/widgets/post_call_telemetry_summary_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

Widget _wrap(Widget child) => MaterialApp(home: child);

Future<void> _pumpLoaded(WidgetTester tester) async {
  await tester.pump();
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const secureStorageChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  group('PostCallTelemetrySummaryScreen', () {
    setUpAll(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secureStorageChannel, (call) async {
        switch (call.method) {
          case 'read':
            return null;
          case 'write':
          case 'delete':
          case 'deleteAll':
            return null;
          case 'containsKey':
            return false;
          case 'readAll':
            return <String, String>{};
          default:
            return null;
        }
      });
    });

    tearDownAll(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(secureStorageChannel, null);
    });

    tearDown(() {
      ApiService.debugResetHttpClient();
    });

    testWidgets('renders empty telemetry state when backend has no call data', (
      tester,
    ) async {
      ApiService.debugSetHttpClient(
        MockClient((request) async {
          if (request.url.path.endsWith('/telemetry')) {
            return http.Response(jsonEncode(<Map<String, dynamic>>[]), 200);
          }
          if (request.url.path.endsWith('/summary')) {
            return http.Response('', 404);
          }
          if (request.url.path.endsWith('/transcript/segments')) {
            return http.Response(jsonEncode(<Map<String, dynamic>>[]), 200);
          }
          if (request.url.path.endsWith('/recording')) {
            return http.Response('', 404);
          }
          return http.Response('', 404);
        }),
      );

      await tester.pumpWidget(
        _wrap(
          const PostCallTelemetrySummaryScreen(
            callId: 'call-empty',
            recipientName: 'Pat Doe',
          ),
        ),
      );
      await _pumpLoaded(tester);

      expect(find.text('Call Summary'), findsOneWidget);
      expect(
          find.text('No telemetry saved for this call yet.'), findsOneWidget);
      expect(
        find.text('No sentiment data available for this call.'),
        findsOneWidget,
      );
      expect(find.text('Call Transcript'), findsNothing);
    });

    testWidgets('renders summary, recording, transcript, and action callbacks',
        (
      tester,
    ) async {
      var callAgainTapped = 0;
      var sendMessageTapped = 0;

      ApiService.debugSetHttpClient(
        MockClient((request) async {
          final path = request.url.path;
          if (path.endsWith('/telemetry')) {
            return http.Response(
              jsonEncode([
                {
                  'eventType': 'CALL_STARTED',
                  'occurredAt': '2026-03-12T15:00:00Z',
                },
                {
                  'eventType': 'SENTIMENT_VOICE',
                  'channel': 'VOICE',
                  'sentimentScore': 0.72,
                  'sentimentLabel': 'CALM',
                  'sentimentNotes': 'steady voice',
                  'occurredAt': '2026-03-12T15:01:00Z',
                },
                {
                  'eventType': 'SENTIMENT_VOICE',
                  'channel': 'VOICE',
                  'sentimentScore': 0.42,
                  'sentimentLabel': 'ANXIOUS',
                  'sentimentNotes': 'mild tension',
                  'occurredAt': '2026-03-12T15:02:00Z',
                },
                {
                  'eventType': 'SENTIMENT_FINAL',
                  'channel': 'COMBINED',
                  'sentimentScore': 0.68,
                  'sentimentLabel': 'CALM',
                  'sentimentNotes': 'Recovered by end of call',
                  'occurredAt': '2026-03-12T15:04:00Z',
                },
                {
                  'eventType': 'CALL_ENDED',
                  'occurredAt': '2026-03-12T15:05:00Z',
                  'metadata': {'reason': 'completed'},
                },
              ]),
              200,
            );
          }
          if (path.endsWith('/summary')) {
            return http.Response(
              jsonEncode({
                'summary': {
                  'headline': 'Patient stabilized during the check-in.',
                  'overallAssessment':
                      'Mood improved steadily after reassurance and breathing prompts.',
                  'keyConcerns': ['Initial anxiety', 'Shortness of breath'],
                  'recommendedActions': [
                    'Repeat breathing exercise tonight',
                    'Follow up tomorrow morning',
                  ],
                },
              }),
              200,
            );
          }
          if (path.endsWith('/transcript/segments')) {
            return http.Response(
              jsonEncode([
                {
                  'speakerLabel': 'CAREGIVER',
                  'text': 'Let us slow down your breathing together.',
                  'startMs': 0,
                  'endMs': 4000,
                  'occurredAt': '2026-03-12T15:00:30Z',
                },
                {
                  'speakerLabel': 'PATIENT',
                  'text': 'Feeling much calmer now.',
                  'startMs': 5000,
                  'endMs': 8000,
                  'occurredAt': '2026-03-12T15:00:35Z',
                },
              ]),
              200,
            );
          }
          if (path.endsWith('/recording')) {
            return http.Response(
              jsonEncode({
                'status': 'STOPPED',
                'concatenationStatus': 'READY',
                'durationSeconds': 305,
                'startedAt': '2026-03-12T15:00:00Z',
                'playbackReady': true,
              }),
              200,
            );
          }
          return http.Response('', 404);
        }),
      );

      await tester.pumpWidget(
        _wrap(
          PostCallTelemetrySummaryScreen(
            callId: 'call-rich',
            recipientName: 'Sam Patient',
            onCallAgain: () => callAgainTapped += 1,
            onSendMessage: () => sendMessageTapped += 1,
          ),
        ),
      );
      await _pumpLoaded(tester);

      expect(find.text('Call summary'), findsOneWidget);
      expect(
        find.text('Patient stabilized during the check-in.'),
        findsOneWidget,
      );
      expect(
          find.textContaining('Key concerns: Initial anxiety'), findsOneWidget);
      expect(
        find.textContaining('Actions: Repeat breathing exercise tonight'),
        findsOneWidget,
      );
      expect(find.text('Call Again'), findsOneWidget);
      expect(find.text('Send Message'), findsOneWidget);

      await tester.tap(find.text('Call Again'));
      await tester.pump();
      await tester.tap(find.text('Send Message'));
      await tester.pump();

      expect(callAgainTapped, 1);
      expect(sendMessageTapped, 1);
    });
  });
}
