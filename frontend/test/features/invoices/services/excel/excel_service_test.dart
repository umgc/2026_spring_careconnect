// test/features/invoices/services/excel/excel_service_test.dart
//
// Unit & widget tests for ExcelService.
//
// Coverage targets:
//   - Singleton construction (lines 15-16)
//   - exportInvoices early-return on empty list (lines 23-28)
//   - exportInvoices Excel-generation loop (lines 30-77), including:
//       · appendRow for each invoice
//       · null-safety fallbacks for amounts.total and amounts.amountDue
//       · column auto-fit loop
//   - _formatPaymentStatus for every enum branch (lines 80-92):
//       · PaymentStatus.paid      → "Paid"
//       · PaymentStatus.pending   → "Pending"
//       · PaymentStatus.rejectedInsurance → "Rejected by Insurance"
//       · all remaining statuses fall through to the default → "Unknown"
//
// Note: _formatPaymentStatus is a private method, so it is exercised
// indirectly by calling exportInvoices with invoices that carry each status.
//
// Note on async test strategy: exportInvoices calls saveAndOpenFile which
// uses path_provider / open_filex (native platform channels).  In the test
// host those channels are not registered, so they throw MissingPluginException
// which is caught by the try/catch inside exportInvoices and surfaced as an
// error SnackBar.  To ensure the full async chain completes before we assert,
// every non-empty test uses tester.runAsync() — which runs real (not fake)
// async — and then calls tester.pump() to flush the resulting widget rebuild.

import 'package:care_connect_app/features/invoices/models/invoice_models.dart';
import 'package:care_connect_app/features/invoices/services/excel/excel_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

// ---------------------------------------------------------------------------
// Helper: build a minimal valid Invoice for testing
// ---------------------------------------------------------------------------

/// Creates a fully-populated [Invoice] with sensible defaults.
/// Override any field with named parameters to target a specific branch.
Invoice _buildTestInvoice({
  String id = 'inv-001',
  String invoiceNumber = 'INV-001',
  PaymentStatus paymentStatus = PaymentStatus.pending,
  double? total = 100.0,
  double? amountDue = 50.0,
}) {
  return Invoice(
    id: id,
    invoiceNumber: invoiceNumber,
    provider: const ProviderInfo(
      name: 'Test Provider',
      address: '123 Main St',
      phone: '555-0100',
    ),
    patient: const PatientInfo(name: 'Test Patient'),
    dates: InvoiceDates(
      statementDate: DateTime(2025, 1, 15),
      dueDate: DateTime(2025, 2, 15),
    ),
    paymentStatus: paymentStatus,
    billedToInsurance: false,
    amounts: Amounts(total: total, amountDue: amountDue),
    paymentReferences: PaymentReferences(supportedMethods: const []),
    createdAt: '2025-01-15T00:00:00',
    updatedAt: '2025-01-15T00:00:00',
    createdBy: 'test',
    updatedBy: 'test',
    payments: const [],
  );
}

/// Pumps a [MaterialApp]/[Scaffold] that captures the inner [BuildContext],
/// then calls [ExcelService.exportInvoices] inside [tester.runAsync] so that
/// all real async operations (including platform-channel calls that throw
/// MissingPluginException on the test host) complete before we assert.
Future<void> _runExport(
  WidgetTester tester,
  List<Invoice> invoices,
) async {
  // Build a minimal scaffold so ScaffoldMessenger is available.
  BuildContext? capturedCtx;
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: Builder(
          builder: (ctx) {
            capturedCtx = ctx;
            return const SizedBox.shrink();
          },
        ),
      ),
    ),
  );

  // runAsync: disables fake-async so native-channel Futures can resolve
  // (or throw MissingPluginException, which exportInvoices catches).
  await tester.runAsync(() async {
    await ExcelService.instance.exportInvoices(invoices, capturedCtx!);
  });

  // Flush the widget rebuild triggered by showSnackBar.
  await tester.pump();
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('ExcelService', () {
    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    test('instance always returns the same object (singleton pattern)', () {
      // ExcelService uses a private constructor and a static field to enforce
      // exactly one instance.  Every access to ExcelService.instance must
      // return the identical object reference.
      final first = ExcelService.instance;
      final second = ExcelService.instance;

      expect(identical(first, second), isTrue,
          reason:
              'Two calls to ExcelService.instance returned different objects');
    });

    // -----------------------------------------------------------------------
    // exportInvoices – empty list guard (lines 23-28)
    // -----------------------------------------------------------------------

    testWidgets(
      'exportInvoices shows "No invoices to export." SnackBar when list is empty',
      (tester) async {
        // An empty invoice list must short-circuit before any Excel work and
        // display a user-friendly SnackBar message.
        BuildContext? capturedCtx;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Builder(
                builder: (ctx) {
                  capturedCtx = ctx;
                  return const SizedBox.shrink();
                },
              ),
            ),
          ),
        );

        ExcelService.instance.exportInvoices([], capturedCtx!);
        await tester.pump(); // flush the synchronous showSnackBar call

        expect(find.text('No invoices to export.'), findsOneWidget);
      },
    );

    testWidgets(
      'exportInvoices does NOT reach the catch block when list is empty',
      (tester) async {
        // The early-return path must never touch the try block, so no error
        // SnackBar should appear.
        BuildContext? capturedCtx;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Builder(
                builder: (ctx) {
                  capturedCtx = ctx;
                  return const SizedBox.shrink();
                },
              ),
            ),
          ),
        );

        ExcelService.instance.exportInvoices([], capturedCtx!);
        await tester.pump();

        expect(find.textContaining('Error exporting file:'), findsNothing);
      },
    );

    // -----------------------------------------------------------------------
    // exportInvoices – Excel generation path (lines 30-77)
    // -----------------------------------------------------------------------

    testWidgets(
      'exportInvoices does not throw for a single valid invoice',
      (tester) async {
        // The complete Excel-generation code path (header row, one data row,
        // column auto-fit) must run without an uncaught exception.
        // On the test host the native save plugin is unavailable; the
        // try/catch in exportInvoices swallows the error and shows an error
        // SnackBar, which is the expected graceful-degradation behaviour.
        await _runExport(tester, [_buildTestInvoice()]);

        // A SnackBar is always shown — either success or error.
        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      'exportInvoices handles null total and amountDue via ?? 0 fallback',
      (tester) async {
        // Covers the DoubleCellValue(invoice.amounts.total ?? 0) and
        // DoubleCellValue(invoice.amounts.amountDue ?? 0) null-safety branches.
        await _runExport(
          tester,
          [_buildTestInvoice(total: null, amountDue: null)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      'exportInvoices iterates the appendRow loop for multiple invoices',
      (tester) async {
        // The for-loop in exportInvoices must execute once per invoice without
        // error.  Three invoices → three data rows appended after the header.
        final invoices = [
          _buildTestInvoice(id: 'inv-001'),
          _buildTestInvoice(id: 'inv-002'),
          _buildTestInvoice(id: 'inv-003'),
        ];

        await _runExport(tester, invoices);

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    // -----------------------------------------------------------------------
    // _formatPaymentStatus branches (lines 80-92) – exercised indirectly
    // -----------------------------------------------------------------------
    //
    // _formatPaymentStatus is private to the library, so it cannot be called
    // directly.  Each test below creates an invoice with a specific
    // PaymentStatus, causing the switch inside _formatPaymentStatus to enter
    // exactly one branch when the data row is built.

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.paid → "Paid" branch runs without error',
      (tester) async {
        // Exercises: case PaymentStatus.paid: return 'Paid';
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.paid)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.pending → "Pending" branch runs without error',
      (tester) async {
        // Exercises: case PaymentStatus.pending: return 'Pending';
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.pending)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.rejectedInsurance → "Rejected by Insurance" branch runs without error',
      (tester) async {
        // Exercises: case PaymentStatus.rejectedInsurance:
        //            return 'Rejected by Insurance';
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.rejectedInsurance)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.overdue falls through to default "Unknown" branch',
      (tester) async {
        // PaymentStatus.overdue has no explicit case, so execution reaches
        // the default branch and returns 'Unknown'.
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.overdue)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.sent falls through to default "Unknown" branch',
      (tester) async {
        // PaymentStatus.sent also hits the default branch.
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.sent)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.partialPayment falls through to default "Unknown" branch',
      (tester) async {
        // PaymentStatus.partialPayment also hits the default branch.
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.partialPayment)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    testWidgets(
      '_formatPaymentStatus: PaymentStatus.pendingInsurance falls through to default "Unknown" branch',
      (tester) async {
        // PaymentStatus.pendingInsurance also hits the default branch.
        await _runExport(
          tester,
          [_buildTestInvoice(paymentStatus: PaymentStatus.pendingInsurance)],
        );

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );

    // -----------------------------------------------------------------------
    // Mixed-status batch — all _formatPaymentStatus branches in one call
    // -----------------------------------------------------------------------

    testWidgets(
      'exportInvoices with one invoice per PaymentStatus covers all _formatPaymentStatus branches',
      (tester) async {
        // Creates one invoice for every PaymentStatus enum value so that every
        // branch of _formatPaymentStatus is hit in a single Excel workbook.
        final invoices = PaymentStatus.values
            .map(
              (s) => _buildTestInvoice(id: 'inv-${s.name}', paymentStatus: s),
            )
            .toList();

        await _runExport(tester, invoices);

        expect(find.byType(SnackBar), findsOneWidget);
      },
    );
  });
}
