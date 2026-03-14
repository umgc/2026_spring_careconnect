import 'package:care_connect_app/features/tasks/models/task_model.dart';
import 'package:care_connect_app/features/tasks/utils/recurrence_utils.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  // Shared base task for all buildTask tests.
  // Uses Jan 1, 2025 as the base date.
  final baseTask = Task(
    id: 1,
    name: "Test Task",
    description: "Base recurring task",
    date: DateTime(2025, 1, 1),
    taskType: "Vitals",
    assignedPatientId: 1,
  );

  // ═══════════════════════════════════════════════════════════════════════════
  // RecurrenceUtils.buildTask()
  // ═══════════════════════════════════════════════════════════════════════════
  group('RecurrenceUtils.buildTask()', () {
    // ── Daily ─────────────────────────────────────────────────────────────────

    // Jan 1–10 inclusive with step 1 = 10 occurrences.
    test('daily: correct frequency/interval/count/date with end date', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 1,
        startDate: DateTime(2025, 1, 1),
        endDate: DateTime(2025, 1, 10),
      );

      expect(task.frequency, "daily");
      expect(task.interval, 1);
      expect(task.count, 10);
      expect(task.date, DateTime(2025, 1, 1));
    });

    // interval=null → should be normalised to 1.
    test('daily: null interval defaults to 1', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: null,
        startDate: DateTime(2025, 1, 1),
        endDate: DateTime(2025, 1, 5),
      );

      expect(task.interval, 1);
    });

    // interval=0 (< 1) → should be normalised to 1.
    test('daily: zero interval defaults to 1', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 0,
        startDate: DateTime(2025, 1, 1),
        endDate: DateTime(2025, 1, 5),
      );

      expect(task.interval, 1);
    });

    // No endDate and no count supplied → falls back to the 30-occurrence default.
    test('daily: no end date falls back to count 30', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 1,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.count, 30);
    });

    // An explicit count provided by the caller should be preserved when there
    // is no end date (the ??= operator only assigns when null).
    test('daily: explicit count preserved when no end date', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 1,
        count: 7,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.count, 7);
    });

    // Every 2 days from Jan 1 to Jan 10: Jan 1, 3, 5, 7, 9 = 5 occurrences.
    test('daily: interval=2 produces correct count', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 2,
        startDate: DateTime(2025, 1, 1),
        endDate: DateTime(2025, 1, 10),
      );

      expect(task.count, 5);
    });

    // ── Weekly ────────────────────────────────────────────────────────────────

    // Sundays + Saturdays selected over January 2025. The exact count depends
    // on the internal weekday-mapping logic; we verify it is non-zero and that
    // frequency/interval are set correctly.
    test('weekly: correct frequency and non-zero count with daysOfWeek', () {
      // Index 0=Sun, 6=Sat
      final daysOfWeek = [true, false, false, false, false, false, true];
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Weekly",
        daysOfWeek: daysOfWeek,
        interval: 1,
        startDate: DateTime(2025, 1, 1), // Wednesday
        endDate: DateTime(2025, 1, 31),
      );

      expect(task.frequency, "weekly");
      expect(task.interval, 1);
      expect(task.count, isNonZero);
    });

    // No endDate → falls back to the 4-occurrence default.
    test('weekly: no end date falls back to count 4', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Weekly",
        interval: 1,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.count, 4);
    });

    // interval=null → normalised to 1.
    test('weekly: null interval defaults to 1', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Weekly",
        interval: null,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.interval, 1);
    });

    // The daysOfWeek list passed in must be attached to the returned task.
    test('weekly: daysOfWeek are preserved on the returned task', () {
      final days = [false, true, false, true, false, true, false]; // M/W/F
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Weekly",
        daysOfWeek: days,
        interval: 1,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.daysOfWeek, days);
    });

    // ── Monthly ───────────────────────────────────────────────────────────────

    // Jan 15 → May 15 on the 15th of every month = 5 occurrences.
    test('monthly: correct count with explicit dayOfMonth and end date', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: 1,
        startDate: DateTime(2025, 1, 15),
        endDate: DateTime(2025, 5, 15),
        dayOfMonth: 15,
      );

      expect(task.frequency, "monthly");
      expect(task.interval, 1);
      expect(task.count, 5);
      expect(task.date.day, 15);
    });

    // No endDate → falls back to the 12-occurrence default.
    test('monthly: no end date falls back to count 12', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: 1,
        startDate: DateTime(2025, 1, 15),
        dayOfMonth: 15,
      );

      expect(task.count, 12);
    });

    // When dayOfMonth is not supplied the day is taken from effectiveDate.day.
    test('monthly: no dayOfMonth uses effectiveDate.day', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: 1,
        startDate: DateTime(2025, 3, 20),
        // dayOfMonth omitted → uses day 20
      );

      expect(task.date.day, 20);
    });

    // dayOfMonth=15 falls before startDay=20, so the first occurrence is pushed
    // to Feb 15. With endDate=Apr 15, the series is Feb, Mar, Apr = 3 hits.
    test('monthly: firstCandidate before startDate is pushed to next month', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: 1,
        startDate: DateTime(2025, 1, 20),
        dayOfMonth: 15, // 15 < 20 → pushed to Feb 15
        endDate: DateTime(2025, 4, 15),
      );

      expect(task.date, DateTime(2025, 2, 15));
      expect(task.count, 3); // Feb, Mar, Apr
    });

    // February 2025 has 28 days, so day 31 must be clamped to 28.
    test('monthly: dayOfMonth=31 clamped to last day of February', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: 1,
        startDate: DateTime(2025, 2, 1),
        dayOfMonth: 31, // Feb 2025 has 28 days → clamp to 28
      );

      expect(task.date, DateTime(2025, 2, 28));
    });

    // interval=null → normalised to 1.
    test('monthly: null interval defaults to 1', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Monthly",
        interval: null,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.interval, 1);
    });

    // ── Yearly ────────────────────────────────────────────────────────────────

    // 2025, 2026, 2027, 2028, 2029, 2030 = 6 occurrences.
    test('yearly: correct count with end date', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Yearly",
        interval: 1,
        startDate: DateTime(2025, 1, 1),
        endDate: DateTime(2030, 1, 1),
      );

      expect(task.frequency, "yearly");
      expect(task.interval, 1);
      expect(task.count, 6);
    });

    // No endDate → falls back to the 5-occurrence default.
    test('yearly: no end date falls back to count 5', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Yearly",
        interval: 1,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.count, 5);
    });

    // interval=null → normalised to 1.
    test('yearly: null interval defaults to 1', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: "Yearly",
        interval: null,
        startDate: DateTime(2025, 1, 1),
      );

      expect(task.interval, 1);
    });

    // ── Non-recurring / edge cases ─────────────────────────────────────────────

    // isRecurring=false → no recurrence fields populated.
    test('non-recurring: frequency/interval/count are null when isRecurring=false', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: false,
      );

      expect(task.frequency, isNull);
      expect(task.interval, isNull);
      expect(task.count, isNull);
    });

    // isRecurring=null → same as not recurring.
    test('non-recurring: no frequency when isRecurring=null', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: null,
      );

      expect(task.frequency, isNull);
    });

    // isRecurring=true but recurrenceType=null → condition fails, no frequency.
    test('non-recurring: no frequency when recurrenceType=null', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask,
        isRecurring: true,
        recurrenceType: null,
      );

      expect(task.frequency, isNull);
    });

    // When startDate is omitted, buildTask must fall back to baseTask.date.
    test('uses baseTask.date when startDate is not provided', () {
      final task = RecurrenceUtils.buildTask(
        baseTask: baseTask, // date = Jan 1 2025
        isRecurring: true,
        recurrenceType: "Daily",
        interval: 1,
        // no startDate
      );

      expect(task.date, DateTime(2025, 1, 1));
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // RecurrenceUtils.calculateCount()
  // ═══════════════════════════════════════════════════════════════════════════
  group('RecurrenceUtils.calculateCount()', () {
    // ── Daily ─────────────────────────────────────────────────────────────────

    // diff=9 days; (9 ~/ 1) + 1 = 10.
    test('daily interval=1: 10 days Jan 1–10', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 1, 10),
          frequency: "daily",
          interval: 1,
        ),
        10,
      );
    });

    // diff=9 days; (9 ~/ 2) + 1 = 5. (Jan 1, 3, 5, 7, 9)
    test('daily interval=2: every other day gives 5 occurrences', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 1, 10),
          frequency: "daily",
          interval: 2,
        ),
        5,
      );
    });

    // ── Weekly ────────────────────────────────────────────────────────────────

    // No daysOfWeek supplied → falls back to whole-week arithmetic.
    // ~31 days / 7 = 4 complete weeks; (4 ~/ 1) + 1 = 5.
    test('weekly no daysOfWeek: count based on whole weeks', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 2, 1),
          frequency: "weekly",
          interval: 1,
        ),
        greaterThan(3),
      );
    });

    // Only Wednesday (index 3) selected over a single week Jan 1 (Wed) to Jan 7
    // (Tue). cursor.weekday % 7 maps Mon=1, …, Sat=6, Sun=0.
    // Jan 1 (Wed → weekday=3, 3%7=3) matches daysOfWeek[3]=true → count=1.
    test('weekly with daysOfWeek: counts only selected weekday (Wednesday)', () {
      final daysOfWeek = [false, false, false, true, false, false, false];
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1), // Wednesday
          endDate: DateTime(2025, 1, 7),   // Tuesday
          frequency: "weekly",
          interval: 1,
          daysOfWeek: daysOfWeek,
        ),
        1,
      );
    });

    // Mon–Fri selected over a full business week Jan 6 (Mon) to Jan 10 (Fri)
    // = 5 occurrences.
    test('weekly with daysOfWeek: Mon–Fri across one business week = 5', () {
      // index: 0=Sun,1=Mon,2=Tue,3=Wed,4=Thu,5=Fri,6=Sat
      final daysOfWeek = [false, true, true, true, true, true, false];
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 6),  // Monday
          endDate: DateTime(2025, 1, 10),   // Friday
          frequency: "weekly",
          interval: 1,
          daysOfWeek: daysOfWeek,
        ),
        5,
      );
    });

    // ── Monthly ───────────────────────────────────────────────────────────────

    // Jan–Dec 2025: (11 months ~/ 1) + 1 = 12.
    test('monthly interval=1: 12 months in one year', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 12, 31),
          frequency: "monthly",
          interval: 1,
        ),
        12,
      );
    });

    // Jan to Jul = 6 months; (6 ~/ 2) + 1 = 4.
    test('monthly interval=2: every other month gives 4 occurrences', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 7, 1),
          frequency: "monthly",
          interval: 2,
        ),
        4,
      );
    });

    // ── Yearly ────────────────────────────────────────────────────────────────

    // 2025–2030 = 5 years; (5 ~/ 1) + 1 = 6.
    test('yearly interval=1: 6 occurrences across 6 years', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2030, 1, 1),
          frequency: "yearly",
          interval: 1,
        ),
        6,
      );
    });

    // 2025–2033 = 8 years; (8 ~/ 2) + 1 = 5.
    test('yearly interval=2: every other year gives 5 occurrences', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2033, 1, 1),
          frequency: "yearly",
          interval: 2,
        ),
        5,
      );
    });

    // ── Unknown frequency ─────────────────────────────────────────────────────

    // The default branch returns 1 for any unrecognised frequency string.
    test('unknown frequency: returns 1', () {
      expect(
        RecurrenceUtils.calculateCount(
          startDate: DateTime(2025, 1, 1),
          endDate: DateTime(2025, 12, 31),
          frequency: "biweekly",
          interval: 1,
        ),
        1,
      );
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // RecurrenceUtils.calculateEndDate()
  // ═══════════════════════════════════════════════════════════════════════════
  group('RecurrenceUtils.calculateEndDate()', () {
    // ── Daily ─────────────────────────────────────────────────────────────────

    // (10-1)*1 = 9 days → Jan 10.
    test('daily interval=1, count=10: ends on Jan 10', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "daily",
          interval: 1,
          count: 10,
        ),
        DateTime(2025, 1, 10),
      );
    });

    // (5-1)*2 = 8 days → Jan 9.
    test('daily interval=2, count=5: ends on Jan 9', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "daily",
          interval: 2,
          count: 5,
        ),
        DateTime(2025, 1, 9),
      );
    });

    // count=1 means a single occurrence; end date == start date.
    test('daily count=1: end date equals start date', () {
      final start = DateTime(2025, 6, 15);
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: start,
          frequency: "daily",
          interval: 1,
          count: 1,
        ),
        start,
      );
    });

    // ── Weekly ────────────────────────────────────────────────────────────────

    // (4-1)*7*1 = 21 days → Jan 22.
    test('weekly interval=1, count=4: ends on Jan 22', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "weekly",
          interval: 1,
          count: 4,
        ),
        DateTime(2025, 1, 22),
      );
    });

    // (3-1)*7*2 = 28 days → Jan 29.
    test('weekly interval=2, count=3: ends on Jan 29', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "weekly",
          interval: 2,
          count: 3,
        ),
        DateTime(2025, 1, 29),
      );
    });

    // ── Monthly ───────────────────────────────────────────────────────────────

    // month + (3-1)*1 = month + 2 → March 15.
    test('monthly interval=1, count=3: ends on March 15', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 15),
          frequency: "monthly",
          interval: 1,
          count: 3,
        ),
        DateTime(2025, 3, 15),
      );
    });

    // month + (3-1)*2 = month + 4 → May 1.
    test('monthly interval=2, count=3: ends 4 months later', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "monthly",
          interval: 2,
          count: 3,
        ),
        DateTime(2025, 5, 1),
      );
    });

    // ── Yearly ────────────────────────────────────────────────────────────────

    // year + (3-1)*2 = year + 4 → 2029.
    test('yearly interval=2, count=3: ends in 2029', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 1, 1),
          frequency: "yearly",
          interval: 2,
          count: 3,
        ),
        DateTime(2029, 1, 1),
      );
    });

    // year + (5-1)*1 = year + 4 → 2029.
    test('yearly interval=1, count=5: ends 4 years later', () {
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: DateTime(2025, 3, 15),
          frequency: "yearly",
          interval: 1,
          count: 5,
        ),
        DateTime(2029, 3, 15),
      );
    });

    // ── Unknown frequency ─────────────────────────────────────────────────────

    // The default branch returns startDate unchanged.
    test('unknown frequency: returns startDate unchanged', () {
      final start = DateTime(2025, 6, 1);
      expect(
        RecurrenceUtils.calculateEndDate(
          startDate: start,
          frequency: "biweekly",
          interval: 1,
          count: 5,
        ),
        start,
      );
    });
  });
}
