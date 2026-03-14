import 'package:care_connect_app/utils/performance_monitor.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  setUp(() {
    // Ensure static state is reset between tests.
    PerformanceMonitor.disable();
  });

  test('getAverageTime returns 0.0 for unknown operation', () {
    expect(PerformanceMonitor.getAverageTime('missing'), 0.0);
  });

  test('start/stop does not record when monitor is disabled', () {
    PerformanceMonitor.startTimer('disabled-op');
    PerformanceMonitor.stopTimer('disabled-op');

    expect(PerformanceMonitor.getAverageTime('disabled-op'), 0.0);
    expect(PerformanceMonitor.getSummary(), isEmpty);
  });

  test('records metrics when enabled via startTimer/stopTimer', () async {
    PerformanceMonitor.enable();
    PerformanceMonitor.startTimer('op');
    await Future<void>.delayed(const Duration(milliseconds: 2));
    PerformanceMonitor.stopTimer('op');

    final summary = PerformanceMonitor.getSummary();
    expect(summary.containsKey('op'), true);

    final stats = summary['op'] as Map<String, dynamic>;
    expect(stats['count'], 1);
    expect(stats['min'], isA<int>());
    expect(stats['max'], isA<int>());
    expect(stats['total'], isA<int>());
    expect(stats['average'], isA<double>());
    expect(PerformanceMonitor.getAverageTime('op'), greaterThanOrEqualTo(0.0));
  });

  test('stopTimer for unknown operation is a no-op', () {
    PerformanceMonitor.enable();
    PerformanceMonitor.stopTimer('missing');

    expect(PerformanceMonitor.getSummary(), isEmpty);
  });

  test('clearMetrics removes accumulated metrics while enabled', () {
    PerformanceMonitor.enable();
    final value = PerformanceMonitor.measureSync<int>('sync-op', () => 7);
    expect(value, 7);
    expect(PerformanceMonitor.getSummary().containsKey('sync-op'), true);

    PerformanceMonitor.clearMetrics();
    expect(PerformanceMonitor.getSummary(), isEmpty);
    expect(PerformanceMonitor.getAverageTime('sync-op'), 0.0);
  });

  test('disable clears existing metrics', () {
    PerformanceMonitor.enable();
    PerformanceMonitor.measureSync<int>('sync-op', () => 1);
    expect(PerformanceMonitor.getSummary().containsKey('sync-op'), true);

    PerformanceMonitor.disable();
    expect(PerformanceMonitor.getSummary(), isEmpty);

    PerformanceMonitor.enable();
    expect(PerformanceMonitor.getSummary(), isEmpty);
  });

  test('measureSync returns value and records operation', () {
    PerformanceMonitor.enable();

    final result = PerformanceMonitor.measureSync<int>('sync-success', () => 42);

    expect(result, 42);
    final summary = PerformanceMonitor.getSummary();
    final stats = summary['sync-success'] as Map<String, dynamic>;
    expect(stats['count'], 1);
  });

  test('measureSync when disabled executes function without recording', () {
    final result = PerformanceMonitor.measureSync<int>('sync-disabled', () => 5);
    expect(result, 5);
    expect(PerformanceMonitor.getSummary(), isEmpty);
    expect(PerformanceMonitor.getAverageTime('sync-disabled'), 0.0);
  });

  test('measureSync records timing and rethrows errors', () {
    PerformanceMonitor.enable();

    expect(
      () => PerformanceMonitor.measureSync<void>(
        'sync-throws',
        () => throw StateError('boom'),
      ),
      throwsA(isA<StateError>()),
    );

    final summary = PerformanceMonitor.getSummary();
    final stats = summary['sync-throws'] as Map<String, dynamic>;
    expect(stats['count'], 1);
  });

  test('measureAsync returns value and records operation', () async {
    PerformanceMonitor.enable();

    final result = await PerformanceMonitor.measureAsync<int>('async-success', () async {
      await Future<void>.delayed(const Duration(milliseconds: 2));
      return 99;
    });

    expect(result, 99);
    final summary = PerformanceMonitor.getSummary();
    final stats = summary['async-success'] as Map<String, dynamic>;
    expect(stats['count'], 1);
  });

  test('measureAsync when disabled executes function without recording', () async {
    final result = await PerformanceMonitor.measureAsync<int>(
      'async-disabled',
      () async => 8,
    );
    expect(result, 8);
    expect(PerformanceMonitor.getSummary(), isEmpty);
    expect(PerformanceMonitor.getAverageTime('async-disabled'), 0.0);
  });

  test('measureAsync records timing and rethrows errors', () async {
    PerformanceMonitor.enable();

    await expectLater(
      () => PerformanceMonitor.measureAsync<void>(
        'async-throws',
        () async {
          await Future<void>.delayed(const Duration(milliseconds: 1));
          throw StateError('boom');
        },
      ),
      throwsA(isA<StateError>()),
    );

    final summary = PerformanceMonitor.getSummary();
    final stats = summary['async-throws'] as Map<String, dynamic>;
    expect(stats['count'], 1);
  });

  test('printReport does not throw (with and without metrics)', () {
    PerformanceMonitor.enable();
    expect(() => PerformanceMonitor.printReport(), returnsNormally);

    PerformanceMonitor.measureSync<int>('for-report', () => 3);
    expect(() => PerformanceMonitor.printReport(), returnsNormally);
  });
}
