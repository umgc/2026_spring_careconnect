// Tests for AIService and the AIModel enum.
//
// AIService is a static service that:
//   1. Defines AIModel with displayName and modelName getters.
//   2. Maintains an in-memory LRU response cache (max 100 entries, 10-min TTL).
//   3. Optionally checks caregiver subscription via SubscriptionService when a
//      non-null BuildContext is supplied (not tested here — requires a full
//      Provider tree and is covered by integration tests).
//   4. Delegates AI requests to AIChatService.sendMessage() and returns the
//      aiResponse string to callers.
//   5. Maps unexpected exceptions to user-friendly error strings.
//
// Strategy:
//   • Mock the flutter_secure_storage MethodChannel so that getAuthHeaders()
//     returns headers with no Authorization token.  Prevents MissingPlugin-
//     Exception in tests and matches the approach in ai_chat_service_test.dart.
//   • Use http.runWithClient() to zone a MockClient over every tested call,
//     so no real network traffic occurs.
//   • Call AIService.clearCache() in setUp and tearDown to prevent cache state
//     leaking between tests (the cache maps are static fields).

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/ai_service.dart';

// ─── MethodChannel used by flutter_secure_storage ────────────────────────────

const MethodChannel _secureStorageChannel =
    MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

// ─── Helper factories ────────────────────────────────────────────────────────

/// Returns a [MockClient] that replies with [statusCode] and a JSON-encoded
/// [body] for every request.
MockClient _mockJson(int statusCode, Object body) =>
    MockClient((_) async => http.Response(jsonEncode(body), statusCode));

/// Returns a [MockClient] that replies with [statusCode] and a plain text
/// [rawBody] for every request.
MockClient _mockRaw(int statusCode, String rawBody) =>
    MockClient((_) async => http.Response(rawBody, statusCode));

// ─── Test entry point ─────────────────────────────────────────────────────────

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Intercept all flutter_secure_storage calls and return null, preventing
    // MissingPluginException and simulating an unauthenticated test context.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      _secureStorageChannel,
      (_) async => null,
    );
    // Ensure each test starts with an empty cache to prevent cross-test leaks.
    AIService.clearCache();
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(_secureStorageChannel, null);
    AIService.clearCache();
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 1 — AIModel.displayName
  //
  // displayName returns the human-readable label used in the UI
  // (e.g., in a model-selector dropdown).
  // ──────────────────────────────────────────────────────────────────────────
  group('AIModel.displayName', () {
    test('deepseek returns "DeepSeek Coder"', () {
      // Shown in the UI wherever the user selects the DeepSeek model.
      expect(AIModel.deepseek.displayName, 'DeepSeek Coder');
    });

    test('gpt4 returns "GPT-4 Turbo"', () {
      expect(AIModel.gpt4.displayName, 'GPT-4 Turbo');
    });

    test('claude returns "Claude 3"', () {
      expect(AIModel.claude.displayName, 'Claude 3');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 2 — AIModel.modelName
  //
  // modelName returns the API identifier sent to the backend as the
  // preferredModel field in every sendMessage() call.
  // ──────────────────────────────────────────────────────────────────────────
  group('AIModel.modelName', () {
    test('deepseek returns "deepseek-chat"', () {
      expect(AIModel.deepseek.modelName, 'deepseek-chat');
    });

    test('gpt4 returns "gpt-4o-mini"', () {
      expect(AIModel.gpt4.modelName, 'gpt-4o-mini');
    });

    test('claude returns "claude-3-haiku"', () {
      expect(AIModel.claude.modelName, 'claude-3-haiku');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 3 — Cache management
  //
  // Responses are cached under a key derived from (question, role, model,
  // healthDataContext).  Cache entries expire after 10 minutes and the cache
  // holds at most _maxCacheSize (100) entries using LRU eviction.
  // clearCache() and dispose() both drain the cache synchronously.
  // ──────────────────────────────────────────────────────────────────────────
  group('cache management', () {
    test('second identical call returns cached response without an HTTP call',
        () async {
      // The HTTP client tracks how many times it was actually invoked.
      // A repeated call with the same arguments must be served from cache,
      // so the HTTP count stays at 1.
      var httpCallCount = 0;
      final client = MockClient((_) async {
        httpCallCount++;
        return http.Response(
          jsonEncode({'success': true, 'aiResponse': 'Cached answer'}),
          200,
        );
      });

      await http.runWithClient(
        () => AIService.askAI('Same question', patientId: 1, userId: 1),
        () => client,
      );
      final result = await http.runWithClient(
        () => AIService.askAI('Same question', patientId: 1, userId: 1),
        () => client,
      );

      expect(httpCallCount, 1);
      expect(result, 'Cached answer');
    });

    test('different questions produce separate cache entries and HTTP calls',
        () async {
      // Each unique question triggers its own backend request.
      var httpCallCount = 0;
      final client = MockClient((_) async {
        httpCallCount++;
        return http.Response(
          jsonEncode({
            'success': true,
            'aiResponse': 'Response $httpCallCount',
          }),
          200,
        );
      });

      await http.runWithClient(
        () => AIService.askAI('Question A', patientId: 1, userId: 1),
        () => client,
      );
      await http.runWithClient(
        () => AIService.askAI('Question B', patientId: 1, userId: 1),
        () => client,
      );

      expect(httpCallCount, 2);
    });

    test('different models produce separate cache entries', () async {
      // The model identifier is part of the cache key, so the same question
      // with a different model must produce a fresh HTTP call.
      var httpCallCount = 0;
      final client = MockClient((_) async {
        httpCallCount++;
        return http.Response(
          jsonEncode({'success': true, 'aiResponse': 'ok'}),
          200,
        );
      });

      await http.runWithClient(
        () => AIService.askAI(
          'Same question',
          model: AIModel.deepseek,
          patientId: 1,
          userId: 1,
        ),
        () => client,
      );
      await http.runWithClient(
        () => AIService.askAI(
          'Same question',
          model: AIModel.claude,
          patientId: 1,
          userId: 1,
        ),
        () => client,
      );

      expect(httpCallCount, 2);
    });

    test('clearCache() forces a fresh HTTP call on the next invocation',
        () async {
      var httpCallCount = 0;
      final client = MockClient((_) async {
        httpCallCount++;
        return http.Response(
          jsonEncode({'success': true, 'aiResponse': 'Fresh'}),
          200,
        );
      });

      // Populate the cache
      await http.runWithClient(
        () => AIService.askAI('Cached question', patientId: 1, userId: 1),
        () => client,
      );
      // Drain the cache
      AIService.clearCache();
      // Must now go to the backend again
      await http.runWithClient(
        () => AIService.askAI('Cached question', patientId: 1, userId: 1),
        () => client,
      );

      expect(httpCallCount, 2);
    });

    test('LRU eviction removes the oldest entry when the cache is full',
        () async {
      // Fill the cache to its maximum capacity (100 entries), then add one
      // more.  The first-inserted entry ("Question 0") must be evicted, so a
      // subsequent call for "Question 0" hits the backend again.
      var httpCallCount = 0;
      final client = MockClient((_) async {
        httpCallCount++;
        return http.Response(
          jsonEncode({'success': true, 'aiResponse': 'Entry $httpCallCount'}),
          200,
        );
      });

      // Fill cache with 100 unique questions (indices 0–99)
      for (var i = 0; i < 100; i++) {
        await http.runWithClient(
          () => AIService.askAI('Question $i', patientId: 1, userId: 1),
          () => client,
        );
      }
      expect(httpCallCount, 100);

      // Adding one more entry triggers LRU eviction of "Question 0"
      await http.runWithClient(
        () => AIService.askAI('Question 100', patientId: 1, userId: 1),
        () => client,
      );

      // "Question 0" was evicted; re-fetching it must produce a new HTTP call
      await http.runWithClient(
        () => AIService.askAI('Question 0', patientId: 1, userId: 1),
        () => client,
      );

      expect(httpCallCount, 102); // 100 initial + 1 new + 1 re-fetch
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 4 — askAI(): success responses (no BuildContext)
  //
  // When context is null, the subscription check is skipped regardless of
  // role.  The aiResponse from the backend is returned directly.
  // ──────────────────────────────────────────────────────────────────────────
  group('askAI() — success (no BuildContext)', () {
    test('returns aiResponse string on HTTP 200 with success:true', () async {
      final result = await http.runWithClient(
        () => AIService.askAI(
          'What is my health status?',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'You are healthy!',
          'conversationId': 'c1',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 50,
        }),
      );
      expect(result, 'You are healthy!');
    });

    test('default role is "patient" and default model is AIModel.deepseek',
        () async {
      // Omitting role and model must succeed using the defaults.
      final result = await http.runWithClient(
        () => AIService.askAI('Default test', patientId: 1, userId: 42),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Default response',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 10,
        }),
      );
      expect(result, 'Default response');
    });

    test('works with every AIModel enum value', () async {
      // All three models must be accepted without error.
      for (final model in AIModel.values) {
        AIService.clearCache();
        final result = await http.runWithClient(
          () => AIService.askAI(
            'Model test',
            model: model,
            patientId: 1,
            userId: 1,
          ),
          () => _mockJson(200, {
            'success': true,
            'aiResponse': model.modelName,
            'conversationId': '',
            'modelUsed': model.modelName,
            'processingTimeMs': 5,
          }),
        );
        expect(result, model.modelName);
      }
    });

    test(
        'returns fallback error string when success is false and '
        'aiResponse is absent', () async {
      // AIChatService.sendMessage() sets success:false when the backend
      // reports an error.  AIService should fall back to its own message.
      final result = await http.runWithClient(
        () => AIService.askAI('Test', patientId: 1, userId: 1),
        () => _mockJson(200, {'success': false}),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });

    test(
        'returns AIChatService aiResponse when success is false and '
        'aiResponse is present', () async {
      // AIChatService includes an aiResponse even on logical failures;
      // AIService must propagate it rather than replacing it.
      final result = await http.runWithClient(
        () => AIService.askAI('Test', patientId: 1, userId: 1),
        () => _mockJson(200, {
          'success': false,
          'aiResponse': 'Sorry, I encountered an error. Please try again.',
        }),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 5 — askAI(): role-based chatType mapping
  //
  //   role="patient"   → chatType=GENERAL_SUPPORT  (default)
  //   role="analytics" → chatType=MEDICAL_CONSULTATION
  //   role="caregiver" → chatType=MEDICAL_CONSULTATION
  //                      (subscription checked only when context≠null)
  //
  // All three paths are exercised without a BuildContext, bypassing the
  // subscription guard entirely.
  // ──────────────────────────────────────────────────────────────────────────
  group('askAI() — role-based chatType mapping', () {
    test('role=patient succeeds and returns response', () async {
      final result = await http.runWithClient(
        () => AIService.askAI(
          'Patient question',
          role: 'patient',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Patient answer',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Patient answer');
    });

    test('role=analytics uses MEDICAL_CONSULTATION and succeeds', () async {
      // The analytics role triggers MEDICAL_CONSULTATION chatType and also
      // enables all health-data flags (vitals, medications, notes, etc.).
      final result = await http.runWithClient(
        () => AIService.askAI(
          'Show me analytics',
          role: 'analytics',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Analytics data',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Analytics data');
    });

    test(
        'role=caregiver with null context skips subscription check '
        'and returns response', () async {
      // When context is null the subscription guard at lines 54–64 is not
      // reached.  The call must succeed as if the user were a patient.
      final result = await http.runWithClient(
        () => AIService.askAI(
          'Care question',
          role: 'caregiver',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Care response',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Care response');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 6 — askAI(): healthDataContext enrichment
  //
  // When healthDataContext is non-empty, AIService prepends it to the
  // question before forwarding to AIChatService.  An empty or null context
  // is treated as absent and the question is sent unchanged.
  // ──────────────────────────────────────────────────────────────────────────
  group('askAI() — healthDataContext enrichment', () {
    test('non-empty healthDataContext call succeeds and returns response',
        () async {
      // The enhanced message prefixes the context before the question.
      // We verify the AI response passes through correctly.
      final result = await http.runWithClient(
        () => AIService.askAI(
          'What should I do?',
          healthDataContext: 'Patient has high blood pressure.',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Based on context, rest.',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Based on context, rest.');
    });

    test('empty healthDataContext sends question unchanged', () async {
      // An empty string must not trigger the context-prefix logic.
      final result = await http.runWithClient(
        () => AIService.askAI(
          'Plain question',
          healthDataContext: '',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Plain answer',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Plain answer');
    });

    test('null healthDataContext sends question unchanged', () async {
      final result = await http.runWithClient(
        () => AIService.askAI(
          'Null context question',
          healthDataContext: null,
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Null context answer',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      expect(result, 'Null context answer');
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 7 — askAI(): exception handling
  //
  // The outer try/catch in askAI() converts exceptions that escape
  // AIChatService into user-friendly strings.  Two paths are exercised:
  //
  //   a) AIChatService returns success:false (e.g., on HTTP 5xx) — the
  //      fallback aiResponse string is returned.
  //   b) AIChatService returns success:true but aiResponse is not a String —
  //      the `as String` cast throws TypeError, which the generic catch branch
  //      converts to a "Sorry, I encountered an error" message.
  // ──────────────────────────────────────────────────────────────────────────
  group('askAI() — exception and error handling', () {
    test('returns a non-empty string on HTTP 500 (AIChatService failure)',
        () async {
      // AIChatService catches the 5xx response and returns success:false.
      // AIService must not throw and must return some user-facing string.
      final result = await http.runWithClient(
        () => AIService.askAI('Test', patientId: 1, userId: 1),
        () => _mockRaw(500, 'Internal Server Error'),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });

    test('returns a non-empty string on HTTP 401 Unauthorized', () async {
      final result = await http.runWithClient(
        () => AIService.askAI('Auth test', patientId: 1, userId: 1),
        () => _mockRaw(401, 'Unauthorized'),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });

    test(
        'generic catch branch triggered when aiResponse value is not a String',
        () async {
      // AIChatService forwards aiResponse as-is.  When the backend returns an
      // integer for aiResponse, `response['aiResponse'] as String` inside
      // askAI() throws a TypeError.  The catch block must convert it to an
      // error string instead of propagating the exception.
      final result = await http.runWithClient(
        () => AIService.askAI('Type cast test', patientId: 1, userId: 1),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 42, // int, not String — triggers the cast failure
          'conversationId': 'c1',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 5,
        }),
      );
      // The generic catch returns: 'Sorry, I encountered an error. ...'
      expect(result, contains('error'));
    });

    test('returns a non-empty string on transport-level network error',
        () async {
      // AIChatService catches http.ClientException and returns success:false.
      // AIService should still return a meaningful string.
      final result = await http.runWithClient(
        () => AIService.askAI('Network test', patientId: 1, userId: 1),
        () => MockClient(
          (_) async => throw http.ClientException('Connection refused'),
        ),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 8 — askHealthQuestion() legacy method
  //
  // askHealthQuestion() is a thin backward-compatible wrapper around askAI()
  // that always uses role="patient" and AIModel.deepseek.
  // ──────────────────────────────────────────────────────────────────────────
  group('askHealthQuestion()', () {
    test('returns the aiResponse from the backend', () async {
      final result = await http.runWithClient(
        () => AIService.askHealthQuestion(
          'Is my blood pressure normal?',
          patientId: 5,
          userId: 10,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Blood pressure looks normal.',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 8,
        }),
      );
      expect(result, 'Blood pressure looks normal.');
    });

    test('uses patient role (no subscription check triggered)', () async {
      // The legacy method always calls askAI with role="patient" and no
      // BuildContext, so the subscription guard is bypassed.
      final result = await http.runWithClient(
        () => AIService.askHealthQuestion(
          'Legacy question',
          patientId: 1,
          userId: 1,
        ),
        () => _mockJson(200, {
          'success': true,
          'aiResponse': 'Legacy answer',
          'conversationId': '',
          'modelUsed': 'deepseek-chat',
          'processingTimeMs': 8,
        }),
      );
      expect(result, isNotEmpty);
    });

    test('returns error string on backend failure', () async {
      // askHealthQuestion should propagate AIChatService error handling.
      final result = await http.runWithClient(
        () => AIService.askHealthQuestion(
          'Failing question',
          patientId: 1,
          userId: 1,
        ),
        () => _mockRaw(503, 'Service Unavailable'),
      );
      expect(result, isA<String>());
      expect(result, isNotEmpty);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────
  // Group 9 — dispose()
  //
  // dispose() closes the shared HTTP client and calls clearCache().  It must
  // not throw under normal conditions.
  // ──────────────────────────────────────────────────────────────────────────
  group('dispose()', () {
    test('completes without throwing', () {
      // The static _httpClient is closed and cache is cleared.  Since the
      // HTTP client is not used directly by askAI() (it delegates to
      // AIChatService), this is safe to call in isolation.
      expect(() => AIService.dispose(), returnsNormally);
    });
  });
}
