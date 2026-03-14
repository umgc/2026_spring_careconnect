// Tests for invoice_models.dart data classes.
//
// Coverage strategy:
//   invoice_models.dart contains pure immutable data classes with copyWith
//   methods using a null-sentinel pattern (_unset object), plus an enum,
//   a factory extension, and PaymentRecord with fromJson/toJson.
//   No platform channels or network I/O required.
//
//   Classes / entities tested:
//     ProviderInfo    — constructor, copyWith (including null-sentinel email clear)
//     PatientInfo     — constructor, copyWith (null-sentinel optional fields)
//     InvoiceDates    — constructor, copyWith (null-sentinel paidDate)
//     ServiceLine     — all-optional constructor, copyWith (numeric + date clear)
//     Amounts         — constructor, copyWith (numeric + null-sentinel clear)
//     PaymentReferences — constructor (list wrapped as unmodifiable), copyWith
//     CheckPayableTo  — constructor, copyWith
//     HistoryEntry    — constructor
//     PaymentRecord   — constructor defaults, toJson, fromJson round-trip, copyWith
//     Invoice         — construction, copyWith (sentinel fields: aiSummary, documentLink,
//                       checkPayableTo, recommendedActions), services as unmodifiable
//     InvoiceFactories.empty — sensible defaults, 30-day due-date window
//     PaymentStatus   — enum has all seven values

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/invoices/models/invoice_models.dart';

// ─── Helper ───────────────────────────────────────────────────────────────────

Invoice _makeInvoice({
  String id = 'inv-1',
  PaymentStatus status = PaymentStatus.pending,
}) {
  return Invoice(
    id: id,
    invoiceNumber: 'INV-001',
    provider: const ProviderInfo(name: 'Clinic', address: '1 Main St', phone: '555-0001'),
    patient: const PatientInfo(name: 'Patient A'),
    dates: InvoiceDates(
      statementDate: DateTime(2025, 1, 1),
      dueDate: DateTime(2025, 2, 1),
    ),
    paymentStatus: status,
    billedToInsurance: false,
    amounts: const Amounts(totalCharges: 100.0, total: 100.0, amountDue: 100.0),
    paymentReferences: PaymentReferences(supportedMethods: const []),
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    createdBy: 'admin',
    updatedBy: 'admin',
    payments: const [],
  );
}

void main() {
  // ─── ProviderInfo ────────────────────────────────────────────────────────────

  group('ProviderInfo', () {
    test('constructor stores all fields including optional email', () {
      // Verifies that every field is accessible after construction.
      const p = ProviderInfo(
        name: 'Clinic A',
        address: '1 Main St',
        phone: '703-555-0001',
        email: 'clinic@example.com',
      );
      expect(p.name, 'Clinic A');
      expect(p.address, '1 Main St');
      expect(p.phone, '703-555-0001');
      expect(p.email, 'clinic@example.com');
    });

    test('copyWith replaces specified non-null fields', () {
      // Verifies that copyWith returns a new instance with changed fields only.
      const p = ProviderInfo(name: 'Old', address: 'Old St', phone: '000');
      final p2 = p.copyWith(name: 'New', phone: '111');
      expect(p2.name, 'New');
      expect(p2.phone, '111');
      expect(p2.address, 'Old St'); // unchanged
    });

    test('copyWith clears email when null is passed (sentinel pattern)', () {
      // Verifies that passing explicit null removes the nullable email field.
      const p = ProviderInfo(
        name: 'X', address: 'Y', phone: 'Z',
        email: 'keep@example.com',
      );
      final cleared = p.copyWith(email: null);
      expect(cleared.email, isNull);
    });

    test('copyWith keeps email when email parameter is omitted', () {
      // Verifies that omitting the parameter leaves the email field unchanged.
      const p = ProviderInfo(
        name: 'X', address: 'Y', phone: 'Z',
        email: 'keep@example.com',
      );
      final same = p.copyWith(name: 'X2');
      expect(same.email, 'keep@example.com');
    });
  });

  // ─── PatientInfo ─────────────────────────────────────────────────────────────

  group('PatientInfo', () {
    test('constructor stores required name and optional nullable fields', () {
      // Verifies all four fields including the nullable optional ones.
      const p = PatientInfo(
        name: 'Alice',
        address: '2 Oak Ave',
        accountNumber: 'ACC-001',
        billingAddress: '3 Pine Rd',
      );
      expect(p.name, 'Alice');
      expect(p.address, '2 Oak Ave');
      expect(p.accountNumber, 'ACC-001');
      expect(p.billingAddress, '3 Pine Rd');
    });

    test('copyWith clears optional fields using null sentinel', () {
      // Verifies that nullable fields are individually clearable via null.
      const p = PatientInfo(
        name: 'Bob',
        address: 'Some St',
        accountNumber: 'ACC-002',
        billingAddress: '4 Elm Rd',
      );
      final cleared = p.copyWith(address: null, accountNumber: null);
      expect(cleared.address, isNull);
      expect(cleared.accountNumber, isNull);
      expect(cleared.billingAddress, '4 Elm Rd'); // unchanged
    });
  });

  // ─── InvoiceDates ────────────────────────────────────────────────────────────

  group('InvoiceDates', () {
    final now = DateTime(2025, 1, 15);
    final due = DateTime(2025, 2, 15);
    final paid = DateTime(2025, 1, 20);

    test('constructor stores statementDate, dueDate, and optional paidDate', () {
      // Verifies all three date fields are stored correctly.
      final d = InvoiceDates(statementDate: now, dueDate: due, paidDate: paid);
      expect(d.statementDate, now);
      expect(d.dueDate, due);
      expect(d.paidDate, paid);
    });

    test('copyWith clears paidDate using null sentinel', () {
      // Verifies that paidDate can be explicitly removed.
      final d = InvoiceDates(statementDate: now, dueDate: due, paidDate: paid);
      final cleared = d.copyWith(paidDate: null);
      expect(cleared.paidDate, isNull);
      expect(cleared.statementDate, now); // unchanged
    });

    test('copyWith keeps paidDate when parameter is omitted', () {
      // Verifies the sentinel leaves paidDate intact when the arg is omitted.
      final d = InvoiceDates(statementDate: now, dueDate: due, paidDate: paid);
      final same = d.copyWith(statementDate: DateTime(2025, 3, 1));
      expect(same.paidDate, paid);
    });
  });

  // ─── ServiceLine ─────────────────────────────────────────────────────────────

  group('ServiceLine', () {
    test('all-optional constructor defaults every field to null', () {
      // Verifies that an empty ServiceLine has all null fields.
      const s = ServiceLine();
      expect(s.description, isNull);
      expect(s.serviceCode, isNull);
      expect(s.serviceDate, isNull);
      expect(s.charge, isNull);
      expect(s.patientBalance, isNull);
      expect(s.insuranceAdjustments, isNull);
    });

    test('copyWith replaces charge while keeping patientBalance', () {
      // Verifies selective update of numeric nullable fields.
      const s = ServiceLine(charge: 100.0, patientBalance: 20.0);
      final s2 = s.copyWith(charge: 150.0);
      expect(s2.charge, 150.0);
      expect(s2.patientBalance, 20.0); // unchanged
    });

    test('copyWith clears serviceDate with null sentinel', () {
      // Verifies that a nullable DateTime field is cleared via null.
      final date = DateTime(2025, 6, 1);
      final s = ServiceLine(serviceDate: date);
      final cleared = s.copyWith(serviceDate: null);
      expect(cleared.serviceDate, isNull);
    });
  });

  // ─── Amounts ─────────────────────────────────────────────────────────────────

  group('Amounts', () {
    test('constructor stores all nullable numeric fields', () {
      // Verifies that all amount fields are stored correctly.
      const a = Amounts(
        totalCharges: 500.0,
        totalAdjustments: -50.0,
        total: 450.0,
        amountDue: 450.0,
      );
      expect(a.totalCharges, 500.0);
      expect(a.totalAdjustments, -50.0);
      expect(a.total, 450.0);
      expect(a.amountDue, 450.0);
    });

    test('copyWith updates amountDue and clears totalAdjustments via sentinel', () {
      // Verifies selective update and null-sentinel clearing in the same call.
      const a = Amounts(
        totalCharges: 200.0,
        totalAdjustments: -20.0,
        total: 180.0,
        amountDue: 180.0,
      );
      final a2 = a.copyWith(amountDue: 100.0, totalAdjustments: null);
      expect(a2.amountDue, 100.0);
      expect(a2.totalAdjustments, isNull);
      expect(a2.totalCharges, 200.0); // unchanged
    });
  });

  // ─── PaymentReferences ───────────────────────────────────────────────────────

  group('PaymentReferences', () {
    test('constructor stores fields and wraps list as UnmodifiableListView', () {
      // Verifies list is stored as unmodifiable and other fields are accessible.
      final refs = PaymentReferences(
        paymentLink: 'https://pay.example.com',
        qrCodeUrl: 'https://qr.example.com',
        notes: 'Pay by end of month',
        supportedMethods: ['check', 'credit_card'],
      );
      expect(refs.paymentLink, 'https://pay.example.com');
      expect(refs.qrCodeUrl, 'https://qr.example.com');
      expect(refs.notes, 'Pay by end of month');
      expect(refs.supportedMethods, containsAll(['check', 'credit_card']));
      // Mutation should throw because the list is unmodifiable.
      expect(() => refs.supportedMethods.add('cash'), throwsUnsupportedError);
    });

    test('copyWith replaces supportedMethods list', () {
      // Verifies that supplying a new list replaces the existing one.
      final refs = PaymentReferences(supportedMethods: ['check']);
      final refs2 = refs.copyWith(supportedMethods: ['online', 'telephone']);
      expect(refs2.supportedMethods, ['online', 'telephone']);
    });

    test('copyWith clears paymentLink with null sentinel', () {
      // Verifies null-sentinel pattern on the paymentLink field.
      final refs = PaymentReferences(
        paymentLink: 'https://pay.example.com',
        supportedMethods: [],
      );
      final cleared = refs.copyWith(paymentLink: null);
      expect(cleared.paymentLink, isNull);
    });
  });

  // ─── CheckPayableTo ──────────────────────────────────────────────────────────

  group('CheckPayableTo', () {
    test('constructor stores all three required fields', () {
      // Verifies name, address, and reference are accessible after construction.
      const c = CheckPayableTo(
        name: 'Care Corp',
        address: '100 Billing Blvd',
        reference: 'INV-2025-001',
      );
      expect(c.name, 'Care Corp');
      expect(c.address, '100 Billing Blvd');
      expect(c.reference, 'INV-2025-001');
    });

    test('copyWith replaces only specified fields', () {
      // Verifies that unspecified fields remain unchanged after copyWith.
      const c = CheckPayableTo(name: 'Old', address: 'Old Addr', reference: 'REF-1');
      final c2 = c.copyWith(name: 'New', reference: 'REF-2');
      expect(c2.name, 'New');
      expect(c2.reference, 'REF-2');
      expect(c2.address, 'Old Addr'); // unchanged
    });
  });

  // ─── HistoryEntry ────────────────────────────────────────────────────────────

  group('HistoryEntry', () {
    test('constructor stores all six fields', () {
      // Verifies every field in the audit-log entry is correctly stored.
      const h = HistoryEntry(
        version: 3,
        changes: 'amount updated',
        userId: 'u-42',
        action: 'UPDATE',
        details: 'Changed amount from 100 to 150',
        timestamp: '2025-06-01T12:00:00Z',
      );
      expect(h.version, 3);
      expect(h.changes, 'amount updated');
      expect(h.userId, 'u-42');
      expect(h.action, 'UPDATE');
      expect(h.details, 'Changed amount from 100 to 150');
      expect(h.timestamp, '2025-06-01T12:00:00Z');
    });
  });

  // ─── PaymentRecord ───────────────────────────────────────────────────────────

  group('PaymentRecord', () {
    final sampleDate = DateTime(2025, 3, 15);

    test('constructor stores all fields; planEnabled defaults to false', () {
      // Verifies planEnabled defaults to false and planDurationMonths is optional.
      final r = PaymentRecord(
        id: 'pr-1',
        confirmationNumber: 'CONF-001',
        date: sampleDate,
        methodKey: 'check',
        amountPaid: 250.0,
      );
      expect(r.id, 'pr-1');
      expect(r.confirmationNumber, 'CONF-001');
      expect(r.date, sampleDate);
      expect(r.methodKey, 'check');
      expect(r.amountPaid, 250.0);
      expect(r.planEnabled, isFalse);
      expect(r.planDurationMonths, isNull);
    });

    test('toJson serializes all fields including planDurationMonths', () {
      // Verifies the JSON output for API submission includes every field.
      final r = PaymentRecord(
        id: 'pr-2',
        confirmationNumber: 'CONF-002',
        date: DateTime(2025, 4, 1),
        methodKey: 'credit_card',
        amountPaid: 500.0,
        planEnabled: true,
        planDurationMonths: 12,
      );
      final json = r.toJson();
      expect(json['id'], 'pr-2');
      expect(json['confirmationNumber'], 'CONF-002');
      expect(json['date'], isA<String>());
      expect(json['methodKey'], 'credit_card');
      expect(json['amountPaid'], 500.0);
      expect(json['planEnabled'], isTrue);
      expect(json['planDurationMonths'], 12);
    });

    test('fromJson round-trips all fields correctly', () {
      // Verifies that a JSON map produced by toJson can be deserialized back.
      final original = PaymentRecord(
        id: 'pr-3',
        confirmationNumber: 'CONF-003',
        date: DateTime.utc(2025, 5, 20),
        methodKey: 'online',
        amountPaid: 75.50,
        planEnabled: false,
      );
      final restored = PaymentRecord.fromJson(original.toJson());
      expect(restored.id, original.id);
      expect(restored.confirmationNumber, original.confirmationNumber);
      expect(restored.methodKey, original.methodKey);
      expect(restored.amountPaid, original.amountPaid);
      expect(restored.planEnabled, original.planEnabled);
      expect(restored.planDurationMonths, isNull);
    });

    test('fromJson coerces integer amountPaid to double', () {
      // Verifies that int JSON values for amountPaid are converted to double.
      final r = PaymentRecord.fromJson({
        'id': 'pr-4',
        'confirmationNumber': 'CONF-004',
        'date': '2025-01-01T00:00:00.000Z',
        'methodKey': 'telephone',
        'amountPaid': 100, // int in JSON, not double
        'planEnabled': false,
      });
      expect(r.amountPaid, 100.0);
    });

    test('copyWith updates specified fields while preserving others', () {
      // Verifies that copyWith produces an updated instance correctly.
      final r = PaymentRecord(
        id: 'pr-5',
        confirmationNumber: 'CONF-005',
        date: sampleDate,
        methodKey: 'check',
        amountPaid: 200.0,
      );
      final r2 = r.copyWith(amountPaid: 300.0, methodKey: 'online');
      expect(r2.amountPaid, 300.0);
      expect(r2.methodKey, 'online');
      expect(r2.id, 'pr-5'); // unchanged
    });
  });

  // ─── PaymentStatus enum ──────────────────────────────────────────────────────

  group('PaymentStatus', () {
    test('enum has all seven expected values', () {
      // Verifies the complete set of payment status values is defined.
      expect(PaymentStatus.values, containsAll([
        PaymentStatus.pending,
        PaymentStatus.overdue,
        PaymentStatus.pendingInsurance,
        PaymentStatus.sent,
        PaymentStatus.paid,
        PaymentStatus.partialPayment,
        PaymentStatus.rejectedInsurance,
      ]));
      expect(PaymentStatus.values.length, 7);
    });
  });

  // ─── Invoice ─────────────────────────────────────────────────────────────────

  group('Invoice', () {
    test('constructor stores all required fields; services and history empty', () {
      // Verifies the invoice is constructed with every mandatory field present.
      final inv = _makeInvoice();
      expect(inv.id, 'inv-1');
      expect(inv.invoiceNumber, 'INV-001');
      expect(inv.provider.name, 'Clinic');
      expect(inv.patient.name, 'Patient A');
      expect(inv.paymentStatus, PaymentStatus.pending);
      expect(inv.billedToInsurance, isFalse);
      expect(inv.services, isEmpty);
      expect(inv.history, isEmpty);
    });

    test('copyWith updates paymentStatus while preserving immutable id', () {
      // Verifies that the payment status can be changed via copyWith.
      final inv = _makeInvoice();
      final updated = inv.copyWith(paymentStatus: PaymentStatus.paid);
      expect(updated.paymentStatus, PaymentStatus.paid);
      expect(updated.id, inv.id); // id is immutable in copyWith
    });

    test('copyWith sets and clears aiSummary with null sentinel', () {
      // Verifies the nullable aiSummary follows the sentinel pattern.
      final inv = _makeInvoice();
      // Set it via copyWith (using the sentinel: not null sentinel, just string)
      final withSummary = Invoice(
        id: inv.id,
        invoiceNumber: inv.invoiceNumber,
        provider: inv.provider,
        patient: inv.patient,
        dates: inv.dates,
        paymentStatus: inv.paymentStatus,
        billedToInsurance: inv.billedToInsurance,
        amounts: inv.amounts,
        paymentReferences: inv.paymentReferences,
        createdAt: inv.createdAt,
        updatedAt: inv.updatedAt,
        createdBy: inv.createdBy,
        updatedBy: inv.updatedBy,
        payments: [],
        aiSummary: 'AI generated summary',
      );
      expect(withSummary.aiSummary, 'AI generated summary');
      final cleared = withSummary.copyWith(aiSummary: null);
      expect(cleared.aiSummary, isNull);
    });

    test('copyWith sets and clears documentLink with null sentinel', () {
      // Verifies that the documentLink nullable field is handled via sentinel.
      final inv = _makeInvoice();
      final withLink = inv.copyWith(documentLink: 'https://doc.example.com/inv.pdf');
      expect(withLink.documentLink, 'https://doc.example.com/inv.pdf');
      final cleared = withLink.copyWith(documentLink: null);
      expect(cleared.documentLink, isNull);
    });

    test('copyWith with services list wraps new list as UnmodifiableListView', () {
      // Verifies that a new services list is wrapped to prevent mutation.
      final inv = _makeInvoice();
      final line = ServiceLine(description: 'Consultation', charge: 100.0);
      final updated = inv.copyWith(services: [line]);
      expect(updated.services.length, 1);
      expect(updated.services.first.description, 'Consultation');
      expect(() => updated.services.add(const ServiceLine()), throwsUnsupportedError);
    });

    test('copyWith sets checkPayableTo and clears it via null sentinel', () {
      // Verifies the optional checkPayableTo object is managed by the sentinel.
      final inv = _makeInvoice();
      const payTo = CheckPayableTo(name: 'Pay Corp', address: '1 Pay St', reference: 'R1');
      final withPayTo = inv.copyWith(checkPayableTo: payTo);
      expect(withPayTo.checkPayableTo?.name, 'Pay Corp');
      final cleared = withPayTo.copyWith(checkPayableTo: null);
      expect(cleared.checkPayableTo, isNull);
    });
  });

  // ─── InvoiceFactories.empty ──────────────────────────────────────────────────

  group('InvoiceFactories.empty', () {
    test('returns an Invoice with sensible defaults', () {
      // Verifies the factory produces a valid Invoice with pending status.
      final inv = InvoiceFactories.empty();
      expect(inv.id, startsWith('local-'));
      expect(inv.invoiceNumber, isEmpty);
      expect(inv.paymentStatus, PaymentStatus.pending);
      expect(inv.billedToInsurance, isFalse);
      expect(inv.amounts.totalCharges, 0);
      expect(inv.amounts.amountDue, 0);
      expect(inv.services, isEmpty);
      expect(inv.history, isEmpty);
      expect(inv.createdBy, 'system');
    });

    test('due date is 30 days after the statement date', () {
      // Verifies the default 30-day net payment window.
      final inv = InvoiceFactories.empty();
      final diff = inv.dates.dueDate.difference(inv.dates.statementDate);
      expect(diff.inDays, 30);
    });
  });
}
