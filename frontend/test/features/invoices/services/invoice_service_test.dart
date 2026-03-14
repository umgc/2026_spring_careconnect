// test/features/invoices/services/invoice_service_test.dart
//
// Unit tests for InvoiceService.
//
// Coverage targets (>= 80 %):
//   - Singleton construction
//   - fetchInvoices: bare-array + {items:[]} response shapes, query-param
//     building (_nz, _cleanQueryParams, _paymentStatusToWire, _dateOnly),
//     non-200 / exception error-paths
//   - getById: success + non-200 + exception
//   - create:  success (201 & 200) + error + serialisation via _invoiceToJson
//   - update:  success + error + PUT URL
//   - delete:  204, 200, other, exception
//   - upsert:  local-id routing → create, server-id routing → update
//   - recordPayment  / deletePayment: success + error + exception
//   - pdfDownloadUrl: pure URL helper
//   - _invoiceFromJson field mapping: all sub-mappers
//   - _paymentStatusFromWire / _paymentStatusToWire: every enum branch
//   - _asDouble: num, String, null, unparseable String
//   - _parseDate: ISO-string, paidDate null/present
//   - _dateOnly: UTC formatting + zero-padding
//
// HTTP isolation strategy
// -----------------------
// Every test that calls a method which makes an HTTP request wraps it in
// http.runWithClient(() => ..., () => MockClient(...)). The Client() factory
// in the http package reads Zone.current[#_clientToken], so runWithClient
// intercepts every http.get / http.post / http.put / http.delete call inside
// InvoiceService without requiring any refactoring.
//
// AuthTokenManager.getAuthHeaders() calls FlutterSecureStorage.read(), which
// throws MissingPluginException in the test host. The surrounding try/catch in
// getJwtToken() returns null, so getAuthHeaders() safely returns the default
// headers map — no test setup is required.

import 'dart:convert';

import 'package:care_connect_app/features/invoices/models/invoice_models.dart';
import 'package:care_connect_app/features/invoices/services/invoice_service.dart';
import 'package:care_connect_app/services/api_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

/// Returns a minimal JSON map that [InvoiceService._invoiceFromJson] can parse
/// without errors. All required sub-maps (provider, patient, dates) are
/// included; optional fields are omitted unless explicitly overridden.
Map<String, dynamic> _invoiceJson({
  String id = 'inv-001',
  String invoiceNumber = 'INV-001',
  String paymentStatus = 'pending',
  bool billedToInsurance = false,
  double? total = 150.0,
  double? amountDue = 75.0,
  bool includeCheckPayable = false,
  List<Map<String, dynamic>>? services,
  List<Map<String, dynamic>>? history,
  List<Map<String, dynamic>>? payments,
  String? documentLink,
  String? aiSummary,
  List<String>? recommendedActions,
  String? paidDate,
}) =>
    {
      'id': id,
      'invoiceNumber': invoiceNumber,
      'provider': {
        'name': 'ACME Health',
        'address': '1 Clinic Rd',
        'phone': '800-555-0001',
        'email': 'billing@acme.com',
      },
      'patient': {
        'name': 'Jane Doe',
        'address': '42 Oak Ave',
        'accountNumber': 'P-100',
        'billingAddress': '42 Oak Ave',
      },
      'dates': {
        'statementDate': '2025-03-01T00:00:00.000Z',
        'dueDate': '2025-04-01T00:00:00.000Z',
        'paidDate': ?paidDate,
      },
      'paymentStatus': paymentStatus,
      'billedToInsurance': billedToInsurance,
      'amounts': {
        'totalCharges': 200.0,
        'totalAdjustments': 50.0,
        'total': total,
        'amountDue': amountDue,
      },
      'paymentReferences': {
        'paymentLink': 'http://pay.me/inv-001',
        'qrCodeUrl': 'http://qr.me/inv-001',
        'notes': 'Please pay promptly.',
        'supportedMethods': ['check', 'card'],
      },
      'services': services ?? [],
      'history': history ?? [],
      'payments': payments ?? [],
      if (includeCheckPayable)
        'checkPayableTo': {
          'name': 'ACME Health',
          'address': '1 Clinic Rd',
          'reference': 'INV-001',
        },
      'documentLink': ?documentLink,
      'aiSummary': ?aiSummary,
      'recommendedActions': ?recommendedActions,
    };

/// Returns an [Invoice] domain object suitable as input to service write
/// methods (create, update, upsert, recordPayment).
Invoice _invoice({
  String id = 'inv-001',
  String invoiceNumber = 'INV-001',
  PaymentStatus paymentStatus = PaymentStatus.pending,
  bool billedToInsurance = false,
}) =>
    Invoice(
      id: id,
      invoiceNumber: invoiceNumber,
      provider: const ProviderInfo(
        name: 'ACME Health',
        address: '1 Clinic Rd',
        phone: '800-555-0001',
        email: 'billing@acme.com',
      ),
      patient: const PatientInfo(
        name: 'Jane Doe',
        address: '42 Oak Ave',
        accountNumber: 'P-100',
      ),
      dates: InvoiceDates(
        statementDate: DateTime.utc(2025, 3, 1),
        dueDate: DateTime.utc(2025, 4, 1),
      ),
      paymentStatus: paymentStatus,
      billedToInsurance: billedToInsurance,
      amounts: const Amounts(totalCharges: 200, total: 150, amountDue: 75),
      paymentReferences: PaymentReferences(supportedMethods: const ['check']),
      createdAt: '2025-03-01T00:00:00.000Z',
      updatedAt: '2025-03-01T00:00:00.000Z',
      createdBy: 'system',
      updatedBy: 'system',
      payments: const [],
    );

// ---------------------------------------------------------------------------
// Test helper – wraps any service call with a zone-local MockClient
// ---------------------------------------------------------------------------

/// Runs [body] inside a zone where every http.Client() call returns a
/// [MockClient] that responds with [statusCode] and [responseJson].
///
/// This is the primary mechanism for isolating all HTTP calls made by
/// InvoiceService from the real network.
Future<T> _withMock<T>(
  Future<T> Function() body, {
  int statusCode = 200,
  String? responseJson,
}) =>
    http.runWithClient(
      body,
      () => MockClient(
        (_) async => http.Response(responseJson ?? '{}', statusCode),
      ),
    );

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

void main() {
  group('InvoiceService', () {
    // -----------------------------------------------------------------------
    // Singleton
    // -----------------------------------------------------------------------

    test('instance always returns the same object (singleton pattern)', () {
      // InvoiceService._() is private, so only InvoiceService.instance can be
      // used. Two accesses must return the same object reference.
      expect(
        identical(InvoiceService.instance, InvoiceService.instance),
        isTrue,
      );
    });

    // -----------------------------------------------------------------------
    // pdfDownloadUrl — pure string computation, no HTTP
    // -----------------------------------------------------------------------

    test('pdfDownloadUrl appends /pdf to the correct invoice endpoint', () {
      // This method is a pure string operation; it just constructs the URL
      // for downloading an invoice as a PDF.
      final url = InvoiceService.instance.pdfDownloadUrl('inv-42');
      expect(url, equals('${ApiConstants.invoices}/inv-42/pdf'));
    });

    // -----------------------------------------------------------------------
    // upsert — routing logic
    // -----------------------------------------------------------------------

    group('upsert', () {
      test('routes to create (POST) when id starts with "local-"', () async {
        // Invoices created on the client before they reach the server are given
        // temporary "local-" ids. upsert must POST these as new resources.
        final draft = _invoice(id: 'local-123');
        String? capturedMethod;

        await http.runWithClient(
          () => InvoiceService.instance.upsert(draft),
          () => MockClient((req) async {
            capturedMethod = req.method;
            return http.Response(jsonEncode(_invoiceJson(id: 'server-1')), 201);
          }),
        );

        expect(capturedMethod, equals('POST'));
      });

      test('routes to create (POST) when id is empty', () async {
        // An empty id is also treated as an unsaved draft.
        final draft = _invoice(id: '');
        String? capturedMethod;

        await http.runWithClient(
          () => InvoiceService.instance.upsert(draft),
          () => MockClient((req) async {
            capturedMethod = req.method;
            return http.Response(jsonEncode(_invoiceJson(id: 'server-2')), 201);
          }),
        );

        expect(capturedMethod, equals('POST'));
      });

      test('routes to update (PUT) for a real server id', () async {
        // A non-local id means the invoice already exists; upsert must PUT.
        final existing = _invoice(id: 'server-id-99');
        String? capturedMethod;

        await http.runWithClient(
          () => InvoiceService.instance.upsert(existing),
          () => MockClient((req) async {
            capturedMethod = req.method;
            return http.Response(
              jsonEncode(_invoiceJson(id: 'server-id-99')),
              200,
            );
          }),
        );

        expect(capturedMethod, equals('PUT'));
      });
    });

    // -----------------------------------------------------------------------
    // fetchInvoices
    // -----------------------------------------------------------------------

    group('fetchInvoices', () {
      test('returns empty list on a non-200 status code', () async {
        // Server errors must be handled gracefully — no exception is thrown and
        // an empty list is returned.
        final result = await _withMock(
          () => InvoiceService.instance.fetchInvoices(),
          statusCode: 500,
          responseJson: 'Internal server error',
        );

        expect(result, isEmpty);
      });

      test('parses a bare JSON array response', () async {
        // The backend may return a plain list of invoice objects at the top
        // level of the response body.
        final json = jsonEncode([
          _invoiceJson(id: 'inv-A'),
          _invoiceJson(id: 'inv-B'),
        ]);

        final result = await _withMock(
          () => InvoiceService.instance.fetchInvoices(),
          responseJson: json,
        );

        expect(result, hasLength(2));
        expect(result.first.id, equals('inv-A'));
        expect(result.last.id, equals('inv-B'));
      });

      test('parses a paginated {items:[...]} response envelope', () async {
        // The backend may wrap the list in an envelope map with an "items" key.
        final json = jsonEncode({
          'items': [_invoiceJson(id: 'inv-C')],
          'total': 1,
          'page': 1,
        });

        final result = await _withMock(
          () => InvoiceService.instance.fetchInvoices(),
          responseJson: json,
        );

        expect(result, hasLength(1));
        expect(result.first.id, equals('inv-C'));
      });

      test('returns empty list for an unexpected response shape', () async {
        // A response that is neither a List nor a {items:[...]} Map is logged
        // and an empty list is returned — no exception propagates to the caller.
        final result = await _withMock(
          () => InvoiceService.instance.fetchInvoices(),
          responseJson: '"just a string"',
        );

        expect(result, isEmpty);
      });

      test('returns empty list when an exception is thrown (e.g. network error)',
          () async {
        // If the HTTP call itself throws (timeout, DNS failure, etc.) the catch
        // block inside fetchInvoices must swallow it and return [].
        final result = await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(),
          () => MockClient((_) async => throw Exception('Network unreachable')),
        );

        expect(result, isEmpty);
      });

      test('includes a non-blank search value as a query parameter', () async {
        // _nz trims non-empty strings and adds them to the query.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(search: 'cancer'),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['search'], equals('cancer'));
      });

      test('omits whitespace-only search from query parameters', () async {
        // _nz returns null for blank strings, so no "search" key is added.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(search: '   '),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters.containsKey('search'), isFalse);
      });

      test('serialises a status filter using _paymentStatusToWire', () async {
        // Multiple statuses are joined with commas in the query string.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            status: {PaymentStatus.paid, PaymentStatus.overdue},
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        final statusParam = capturedUri?.queryParameters['status'] ?? '';
        // Set iteration order is unspecified, so check both values individually.
        expect(statusParam, contains('paid'));
        expect(statusParam, contains('overdue'));
      });

      test('omits the status filter when the Set is empty', () async {
        // An empty status set means "no filter"; the param must be absent.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(status: {}),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters.containsKey('status'), isFalse);
      });

      test('adds dueStart and dueEnd from a DateTimeRange', () async {
        // _dateOnly formats dates as UTC yyyy-MM-dd strings.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            dueRange: DateTimeRange(
              start: DateTime.utc(2025, 1, 5),
              end: DateTime.utc(2025, 3, 31),
            ),
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['dueStart'], equals('2025-01-05'));
        expect(capturedUri?.queryParameters['dueEnd'], equals('2025-03-31'));
      });

      test('adds amountMin and amountMax from a RangeValues', () async {
        // The RangeValues endpoints are serialised as plain strings.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            amountRange: const RangeValues(10.0, 500.0),
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['amountMin'], equals('10.0'));
        expect(capturedUri?.queryParameters['amountMax'], equals('500.0'));
      });

      test('passes page and pageSize as query parameters', () async {
        // Pagination params are simply toString()-ed and added to the URI.
        Uri? capturedUri;

        await http.runWithClient(
          () =>
              InvoiceService.instance.fetchInvoices(page: 2, pageSize: 25),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['page'], equals('2'));
        expect(capturedUri?.queryParameters['pageSize'], equals('25'));
      });

      test('passes providerName and patientName as query parameters', () async {
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            providerName: 'ACME',
            patientName: 'Doe',
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['providerName'], equals('ACME'));
        expect(capturedUri?.queryParameters['patientName'], equals('Doe'));
      });
    });

    // -----------------------------------------------------------------------
    // getById
    // -----------------------------------------------------------------------

    group('getById', () {
      test('returns a parsed Invoice on a 200 response', () async {
        // The response body is a single invoice JSON object.
        final json = jsonEncode(_invoiceJson(id: 'inv-42'));

        final result = await _withMock(
          () => InvoiceService.instance.getById('inv-42'),
          responseJson: json,
        );

        expect(result, isNotNull);
        expect(result!.id, equals('inv-42'));
      });

      test('returns null on a non-200 status code', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('missing'),
          statusCode: 404,
          responseJson: '{"error":"not found"}',
        );

        expect(result, isNull);
      });

      test('returns null when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.getById('any'),
          () => MockClient((_) async => throw Exception('timeout')),
        );

        expect(result, isNull);
      });
    });

    // -----------------------------------------------------------------------
    // create
    // -----------------------------------------------------------------------

    group('create', () {
      test('returns parsed Invoice on 201 Created', () async {
        final result = await _withMock(
          () => InvoiceService.instance.create(_invoice()),
          statusCode: 201,
          responseJson: jsonEncode(_invoiceJson(id: 'server-new')),
        );

        expect(result?.id, equals('server-new'));
      });

      test('returns parsed Invoice on 200 OK (some servers respond with 200)',
          () async {
        final result = await _withMock(
          () => InvoiceService.instance.create(_invoice()),
          statusCode: 200,
          responseJson: jsonEncode(_invoiceJson(id: 'server-new-2')),
        );

        expect(result?.id, equals('server-new-2'));
      });

      test('returns null on a client / server error status', () async {
        final result = await _withMock(
          () => InvoiceService.instance.create(_invoice()),
          statusCode: 400,
          responseJson: '{"error":"bad request"}',
        );

        expect(result, isNull);
      });

      test('returns null when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.create(_invoice()),
          () => MockClient((_) async => throw Exception('network')),
        );

        expect(result, isNull);
      });

      test('serialises invoice fields into the POST body (_invoiceToJson)',
          () async {
        // Verify that _invoiceToJson encodes the domain object correctly.
        final draft = _invoice(
          id: 'local-draft',
          invoiceNumber: 'INV-DRAFT',
          paymentStatus: PaymentStatus.pending,
          billedToInsurance: true,
        );
        Map<String, dynamic>? capturedBody;

        await http.runWithClient(
          () => InvoiceService.instance.create(draft),
          () => MockClient((req) async {
            capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
            return http.Response(
              jsonEncode(_invoiceJson(id: 'srv-x')),
              201,
            );
          }),
        );

        expect(capturedBody?['invoiceNumber'], equals('INV-DRAFT'));
        expect(capturedBody?['billedToInsurance'], isTrue);
        expect(capturedBody?['paymentStatus'], equals('pending'));
        expect(capturedBody?['provider'], isA<Map>());
        expect(capturedBody?['patient'], isA<Map>());
        expect(capturedBody?['dates'], isA<Map>());
        expect(capturedBody?['amounts'], isA<Map>());
      });

      test('null fields are stripped from the serialised payload', () async {
        // _invoiceToJson calls map.removeWhere((_, v) => v == null).
        // documentLink is null on _invoice() and must not appear in the body.
        Map<String, dynamic>? capturedBody;

        await http.runWithClient(
          () => InvoiceService.instance.create(_invoice()),
          () => MockClient((req) async {
            capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
            return http.Response(jsonEncode(_invoiceJson()), 201);
          }),
        );

        expect(capturedBody?.containsKey('documentLink'), isFalse);
      });
    });

    // -----------------------------------------------------------------------
    // update
    // -----------------------------------------------------------------------

    group('update', () {
      test('returns updated Invoice on 200', () async {
        final inv = _invoice(id: 'srv-99');
        final result = await _withMock(
          () => InvoiceService.instance.update(inv),
          responseJson: jsonEncode(_invoiceJson(id: 'srv-99')),
        );

        expect(result?.id, equals('srv-99'));
      });

      test('returns null on a non-200 status code', () async {
        final result = await _withMock(
          () => InvoiceService.instance.update(_invoice(id: 'srv-99')),
          statusCode: 422,
          responseJson: '{"error":"validation failed"}',
        );

        expect(result, isNull);
      });

      test('sends a PUT request to the correct resource URL', () async {
        // The URL must be <invoices_base>/<id>.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.update(_invoice(id: 'srv-77')),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response(
              jsonEncode(_invoiceJson(id: 'srv-77')),
              200,
            );
          }),
        );

        expect(capturedUri?.path, endsWith('/srv-77'));
        expect(capturedUri?.toString(), contains(ApiConstants.invoices));
      });

      test('returns null when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.update(_invoice(id: 'srv-1')),
          () => MockClient((_) async => throw Exception('timeout')),
        );

        expect(result, isNull);
      });
    });

    // -----------------------------------------------------------------------
    // delete
    // -----------------------------------------------------------------------

    group('delete', () {
      test('returns true on 204 No Content', () async {
        final result = await _withMock(
          () => InvoiceService.instance.delete('inv-del'),
          statusCode: 204,
          responseJson: '',
        );

        expect(result, isTrue);
      });

      test('returns true on 200 OK (some servers return 200 for delete)', () async {
        final result = await _withMock(
          () => InvoiceService.instance.delete('inv-del'),
          statusCode: 200,
          responseJson: '{}',
        );

        expect(result, isTrue);
      });

      test('returns false for any other status code', () async {
        final result = await _withMock(
          () => InvoiceService.instance.delete('inv-del'),
          statusCode: 403,
          responseJson: '{"error":"forbidden"}',
        );

        expect(result, isFalse);
      });

      test('returns false when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.delete('inv-del'),
          () => MockClient((_) async => throw Exception('network')),
        );

        expect(result, isFalse);
      });
    });

    // -----------------------------------------------------------------------
    // recordPayment
    // -----------------------------------------------------------------------

    group('recordPayment', () {
      // Shared test record
      final record = PaymentRecord(
        id: 'pay-1',
        confirmationNumber: 'CONF-001',
        date: DateTime.utc(2025, 3, 15),
        methodKey: 'check',
        amountPaid: 75.0,
      );

      test('returns the updated Invoice on 200', () async {
        final result = await _withMock(
          () => InvoiceService.instance.recordPayment(
            invoiceId: 'inv-001',
            record: record,
          ),
          responseJson: jsonEncode(_invoiceJson(id: 'inv-001')),
        );

        expect(result, isNotNull);
        expect(result!.id, equals('inv-001'));
      });

      test('sends the PaymentRecord JSON in the POST body', () async {
        // Verifies PaymentRecord.toJson() is forwarded verbatim.
        Map<String, dynamic>? capturedBody;

        await http.runWithClient(
          () => InvoiceService.instance.recordPayment(
            invoiceId: 'inv-001',
            record: record,
          ),
          () => MockClient((req) async {
            capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
            return http.Response(jsonEncode(_invoiceJson(id: 'inv-001')), 200);
          }),
        );

        expect(capturedBody?['id'], equals('pay-1'));
        expect(capturedBody?['confirmationNumber'], equals('CONF-001'));
        expect(capturedBody?['amountPaid'], equals(75.0));
        expect(capturedBody?['methodKey'], equals('check'));
      });

      test('sends POST to the /payments sub-resource URL', () async {
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.recordPayment(
            invoiceId: 'inv-55',
            record: record,
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response(jsonEncode(_invoiceJson(id: 'inv-55')), 200);
          }),
        );

        expect(capturedUri?.path, endsWith('/inv-55/payments'));
      });

      test('returns null on non-200 status', () async {
        final result = await _withMock(
          () => InvoiceService.instance.recordPayment(
            invoiceId: 'inv-001',
            record: record,
          ),
          statusCode: 409,
          responseJson: '{"error":"conflict"}',
        );

        expect(result, isNull);
      });

      test('returns null when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.recordPayment(
            invoiceId: 'inv-001',
            record: record,
          ),
          () => MockClient((_) async => throw Exception('timeout')),
        );

        expect(result, isNull);
      });
    });

    // -----------------------------------------------------------------------
    // deletePayment
    // -----------------------------------------------------------------------

    group('deletePayment', () {
      test('returns the updated Invoice on 200', () async {
        final result = await _withMock(
          () => InvoiceService.instance.deletePayment(
            invoiceId: 'inv-001',
            paymentId: 'pay-1',
          ),
          responseJson: jsonEncode(_invoiceJson(id: 'inv-001')),
        );

        expect(result, isNotNull);
      });

      test('sends DELETE to the correct payment sub-resource URL', () async {
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.deletePayment(
            invoiceId: 'inv-99',
            paymentId: 'pay-42',
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response(jsonEncode(_invoiceJson(id: 'inv-99')), 200);
          }),
        );

        expect(capturedUri?.path, endsWith('/inv-99/payments/pay-42'));
      });

      test('returns null on non-200 status', () async {
        final result = await _withMock(
          () => InvoiceService.instance.deletePayment(
            invoiceId: 'inv-001',
            paymentId: 'pay-1',
          ),
          statusCode: 404,
          responseJson: '{"error":"not found"}',
        );

        expect(result, isNull);
      });

      test('returns null when an exception is thrown', () async {
        final result = await http.runWithClient(
          () => InvoiceService.instance.deletePayment(
            invoiceId: 'inv-001',
            paymentId: 'pay-1',
          ),
          () => MockClient((_) async => throw Exception('network')),
        );

        expect(result, isNull);
      });
    });

    // -----------------------------------------------------------------------
    // _invoiceFromJson — field mapping
    // -----------------------------------------------------------------------

    group('_invoiceFromJson field mapping', () {
      test('maps all top-level fields correctly', () async {
        // A fully populated invoice JSON must round-trip to a matching domain
        // object with every field set.
        final json = jsonEncode(
          _invoiceJson(
            id: 'inv-full',
            invoiceNumber: 'INV-999',
            paymentStatus: 'paid',
            billedToInsurance: true,
            total: 300.0,
            amountDue: 0.0,
            documentLink: 'http://docs.example.com/inv-full.pdf',
            aiSummary: 'Balance cleared.',
            recommendedActions: ['Send receipt'],
          ),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('inv-full'),
          responseJson: json,
        );

        expect(result!.id, equals('inv-full'));
        expect(result.invoiceNumber, equals('INV-999'));
        expect(result.paymentStatus, equals(PaymentStatus.paid));
        expect(result.billedToInsurance, isTrue);
        expect(result.amounts.total, equals(300.0));
        expect(result.amounts.amountDue, equals(0.0));
        expect(result.documentLink, equals('http://docs.example.com/inv-full.pdf'));
        expect(result.aiSummary, equals('Balance cleared.'));
        expect(result.recommendedActions, equals(['Send receipt']));
      });

      test('maps provider sub-object fields including optional email', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson()),
        );

        expect(result!.provider.name, equals('ACME Health'));
        expect(result.provider.address, equals('1 Clinic Rd'));
        expect(result.provider.phone, equals('800-555-0001'));
        expect(result.provider.email, equals('billing@acme.com'));
      });

      test('maps patient sub-object fields', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson()),
        );

        expect(result!.patient.name, equals('Jane Doe'));
        expect(result.patient.address, equals('42 Oak Ave'));
        expect(result.patient.accountNumber, equals('P-100'));
      });

      test('maps paymentReferences including supportedMethods list', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson()),
        );

        expect(result!.paymentReferences.paymentLink,
            equals('http://pay.me/inv-001'));
        expect(result.paymentReferences.supportedMethods,
            containsAll(['check', 'card']));
      });

      test('maps services list with all service line fields', () async {
        final json = jsonEncode(
          _invoiceJson(
            services: [
              {
                'description': 'Office visit',
                'serviceCode': '99213',
                'serviceDate': '2025-03-01T00:00:00.000Z',
                'charge': 150.0,
                'patientBalance': 50.0,
                'insuranceAdjustments': 100.0,
              },
            ],
          ),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services, hasLength(1));
        final svc = result.services.first;
        expect(svc.description, equals('Office visit'));
        expect(svc.serviceCode, equals('99213'));
        expect(svc.charge, equals(150.0));
        expect(svc.patientBalance, equals(50.0));
        expect(svc.insuranceAdjustments, equals(100.0));
      });

      test('maps history list with all HistoryEntry fields', () async {
        final json = jsonEncode(
          _invoiceJson(
            history: [
              {
                'version': 3,
                'changes': 'amount updated',
                'userId': 'user-1',
                'action': 'UPDATE',
                'details': 'total changed to 300',
                'timestamp': '2025-03-05T10:00:00.000Z',
              },
            ],
          ),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.history, hasLength(1));
        final h = result.history.first;
        expect(h.version, equals(3));
        expect(h.action, equals('UPDATE'));
        expect(h.userId, equals('user-1'));
        expect(h.changes, equals('amount updated'));
      });

      test('maps payments list using PaymentRecord.fromJson', () async {
        final json = jsonEncode(
          _invoiceJson(
            payments: [
              {
                'id': 'pay-1',
                'confirmationNumber': 'CONF-123',
                'date': '2025-03-15T00:00:00.000Z',
                'methodKey': 'check',
                'amountPaid': 75.0,
                'planEnabled': false,
              },
            ],
          ),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.payments, hasLength(1));
        expect(result.payments!.first.amountPaid, equals(75.0));
        expect(result.payments!.first.methodKey, equals('check'));
        expect(result.payments!.first.confirmationNumber, equals('CONF-123'));
      });

      test('maps checkPayableTo when present in JSON', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson(includeCheckPayable: true)),
        );

        expect(result!.checkPayableTo, isNotNull);
        expect(result.checkPayableTo!.name, equals('ACME Health'));
        expect(result.checkPayableTo!.reference, equals('INV-001'));
      });

      test('leaves checkPayableTo as null when absent from JSON', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson()),
        );

        expect(result!.checkPayableTo, isNull);
      });

      test('parses paidDate when present', () async {
        // _datesFromJson calls _parseDate for paidDate only when it's non-null.
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(
            _invoiceJson(paidDate: '2025-03-20T00:00:00.000Z'),
          ),
        );

        expect(result!.dates.paidDate, isNotNull);
        expect(result.dates.paidDate!.year, equals(2025));
        expect(result.dates.paidDate!.month, equals(3));
        expect(result.dates.paidDate!.day, equals(20));
      });

      test('paidDate is null when absent from dates JSON', () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson()),
        );

        expect(result!.dates.paidDate, isNull);
      });
    });

    // -----------------------------------------------------------------------
    // _paymentStatusToWire / _paymentStatusFromWire — full enum coverage
    // -----------------------------------------------------------------------

    group('payment status serialisation and deserialisation', () {
      // All seven PaymentStatus values paired with their wire strings.
      const pairs = [
        (PaymentStatus.pending, 'pending'),
        (PaymentStatus.overdue, 'overdue'),
        (PaymentStatus.pendingInsurance, 'pendingInsurance'),
        (PaymentStatus.sent, 'sent'),
        (PaymentStatus.paid, 'paid'),
        (PaymentStatus.partialPayment, 'partialPayment'),
        (PaymentStatus.rejectedInsurance, 'rejectedInsurance'),
      ];

      test(
          '_paymentStatusToWire serialises every PaymentStatus to its wire string',
          () async {
        // The status filter in fetchInvoices uses _paymentStatusToWire.
        // Capture the query parameter to verify each mapping.
        for (final (status, wire) in pairs) {
          Uri? capturedUri;

          await http.runWithClient(
            () => InvoiceService.instance.fetchInvoices(status: {status}),
            () => MockClient((req) async {
              capturedUri = req.url;
              return http.Response('[]', 200);
            }),
          );

          expect(
            capturedUri?.queryParameters['status'],
            equals(wire),
            reason: 'Expected $status → "$wire"',
          );
        }
      });

      test(
          '_paymentStatusFromWire deserialises every wire string to the correct enum',
          () async {
        // The response JSON paymentStatus string is parsed via
        // _paymentStatusFromWire inside _invoiceFromJson.
        for (final (status, wire) in pairs) {
          final result = await _withMock(
            () => InvoiceService.instance.getById('x'),
            responseJson: jsonEncode(_invoiceJson(paymentStatus: wire)),
          );

          expect(
            result?.paymentStatus,
            equals(status),
            reason: 'Expected "$wire" → $status',
          );
        }
      });

      test('_paymentStatusFromWire is case-insensitive', () async {
        // The switch uses .toLowerCase(), so "PAID" must map to paid.
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson(paymentStatus: 'PAID')),
        );

        expect(result?.paymentStatus, equals(PaymentStatus.paid));
      });

      test('_paymentStatusFromWire falls back to pending for unknown strings',
          () async {
        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(_invoiceJson(paymentStatus: 'unknown-xyz')),
        );

        expect(result?.paymentStatus, equals(PaymentStatus.pending));
      });

      test('_paymentStatusFromWire falls back to pending for a null wire value',
          () async {
        // _paymentStatusFromWire(null) → (null ?? '').toLowerCase() → default.
        final json = {..._invoiceJson(), 'paymentStatus': null};

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: jsonEncode(json),
        );

        expect(result?.paymentStatus, equals(PaymentStatus.pending));
      });
    });

    // -----------------------------------------------------------------------
    // _dateOnly — UTC date formatting
    // -----------------------------------------------------------------------

    group('_dateOnly UTC date formatting', () {
      test('formats a standard date as yyyy-MM-dd', () async {
        // _dateOnly is invoked via the dueRange parameter of fetchInvoices.
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            dueRange: DateTimeRange(
              start: DateTime.utc(2025, 7, 4),
              end: DateTime.utc(2025, 12, 31),
            ),
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['dueStart'], equals('2025-07-04'));
        expect(capturedUri?.queryParameters['dueEnd'], equals('2025-12-31'));
      });

      test('zero-pads single-digit month and day', () async {
        // Month 1 → "01", day 9 → "09".
        Uri? capturedUri;

        await http.runWithClient(
          () => InvoiceService.instance.fetchInvoices(
            dueRange: DateTimeRange(
              start: DateTime.utc(2025, 1, 1),
              end: DateTime.utc(2025, 9, 9),
            ),
          ),
          () => MockClient((req) async {
            capturedUri = req.url;
            return http.Response('[]', 200);
          }),
        );

        expect(capturedUri?.queryParameters['dueStart'], equals('2025-01-01'));
        expect(capturedUri?.queryParameters['dueEnd'], equals('2025-09-09'));
      });
    });

    // -----------------------------------------------------------------------
    // _asDouble — numeric coercion (via service line charge field)
    // -----------------------------------------------------------------------

    group('_asDouble numeric coercion', () {
      // _asDouble is a private instance method exercised by _serviceFromJson,
      // _amountsFromJson, etc. All branches are reachable via the public API.

      test('converts a double JSON value to double', () async {
        final json = jsonEncode(
          _invoiceJson(services: [
            {'description': 'A', 'charge': 150.0},
          ]),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services.first.charge, equals(150.0));
      });

      test('converts an integer JSON value to double', () async {
        // _asDouble(v is num) → v.toDouble()
        final json = jsonEncode(
          _invoiceJson(services: [
            {'description': 'A', 'charge': 120},
          ]),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services.first.charge, equals(120.0));
      });

      test('parses a numeric String value to double', () async {
        // _asDouble(v is String) → double.tryParse(v)
        final json = jsonEncode(
          _invoiceJson(services: [
            {'description': 'A', 'charge': '99.50'},
          ]),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services.first.charge, equals(99.5));
      });

      test('returns null for a null charge value', () async {
        // _asDouble(null) → null
        final json = jsonEncode(
          _invoiceJson(services: [
            {'description': 'A', 'charge': null},
          ]),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services.first.charge, isNull);
      });

      test('returns null for an unparseable String', () async {
        // double.tryParse("not-a-number") returns null.
        final json = jsonEncode(
          _invoiceJson(services: [
            {'description': 'A', 'charge': 'not-a-number'},
          ]),
        );

        final result = await _withMock(
          () => InvoiceService.instance.getById('x'),
          responseJson: json,
        );

        expect(result!.services.first.charge, isNull);
      });
    });
  });
}
