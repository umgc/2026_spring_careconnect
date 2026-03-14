package com.careconnect.service.invoice;

import com.careconnect.dto.invoice.InvoiceDto;
import com.careconnect.dto.invoice.PaymentDto;
import com.careconnect.model.invoice.Invoice;
import com.careconnect.model.invoice.InvoicePayment;
import com.careconnect.model.invoice.PaymentStatus;
import com.careconnect.repository.InvoicePaymentRepository;
import com.careconnect.repository.InvoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository repo;

    @Mock
    private InvoicePaymentRepository paymentRepo;

    @InjectMocks
    private InvoiceService service;

    @AfterEach
    void clearSecurityContext() throws Exception {
        SecurityContextHolder.clearContext();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a minimal InvoiceDto with all required fields populated so that
     * InvoiceMapper.toEntity() / toDto() will not NPE.
     */
    private InvoiceDto minimalDto() throws Exception {
        final InvoiceDto dto = new InvoiceDto();
        dto.paymentStatus = "pending";

        final InvoiceDto.ProviderInfo provider = new InvoiceDto.ProviderInfo();
        provider.name = "Test Provider";
        provider.address = "123 Main St";
        provider.phone = "555-0000";
        dto.provider = provider;

        final InvoiceDto.PatientInfo patient = new InvoiceDto.PatientInfo();
        patient.name = "Test Patient";
        dto.patient = patient;

        final InvoiceDto.InvoiceDates dates = new InvoiceDto.InvoiceDates();
        dates.statementDate = "2024-01-01T00:00:00Z";
        dates.dueDate = "2024-01-31T00:00:00Z";
        dto.dates = dates;

        return dto;
    }

    /**
     * Builds a minimal Invoice entity that will not NPE when passed to
     * InvoiceMapper.toDto() — paymentStatus, statementDate, dueDate,
     * createdAt, and updatedAt are all set.
     */
    private Invoice minimalSavedInvoice(String id) {
        final Invoice inv = new Invoice();
        inv.setId(id);
        inv.setInvoiceNumber("");
        inv.setProviderName("");
        inv.setProviderAddress("");
        inv.setProviderPhone("");
        inv.setPatientName("");
        inv.setPaymentStatus(PaymentStatus.pending);
        inv.setStatementDate(OffsetDateTime.now(ZoneOffset.UTC));
        inv.setDueDate(OffsetDateTime.now(ZoneOffset.UTC));
        inv.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        inv.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return inv;
    }

    private void setAuthenticatedUser(String username) {
        final Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(username);
        final SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private void setUnauthenticated() throws Exception {
        final SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(ctx);
    }

    // ─── create ──────────────────────────────────────────────────────────────

    @Test
    void create_withNullId_generatesUuid() throws Exception {
        final InvoiceDto dto = minimalDto();
        dto.id = null;

        final Invoice saved = minimalSavedInvoice("generated-id");
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.create(dto);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();
    }

    @Test
    void create_withExistingId_keepsId() throws Exception {
        final InvoiceDto dto = minimalDto();
        dto.id = "existing-id";

        final Invoice saved = minimalSavedInvoice("existing-id");
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.create(dto);

        assertThat(result.id).isEqualTo("existing-id");
    }

    @Test
    void create_withAuthenticatedUser_setsCreatedBy() throws Exception {
        setAuthenticatedUser("nurse-bob");

        final InvoiceDto dto = minimalDto();
        dto.id = "auth-id";

        final Invoice saved = minimalSavedInvoice("auth-id");
        saved.setCreatedBy("nurse-bob");
        saved.setUpdatedBy("nurse-bob");
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.create(dto);

        assertThat(result).isNotNull();
        // Verify the entity passed to save had createdBy set to the authenticated user
        verify(repo).save(argThat(inv -> "nurse-bob".equals(inv.getCreatedBy())));
    }

    @Test
    void create_withNoAuthentication_usesSystem() throws Exception {
        setUnauthenticated();

        final InvoiceDto dto = minimalDto();
        dto.id = "system-id";

        final Invoice saved = minimalSavedInvoice("system-id");
        saved.setCreatedBy("system");
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.create(dto);

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> "system".equals(inv.getCreatedBy())));
    }

    // ─── update ──────────────────────────────────────────────────────────────

    @Test
    void update_found_updatesAndReturns() throws Exception {
        final Invoice existing = minimalSavedInvoice("upd-id");
        existing.setCreatedBy("original-user");

        final Invoice rebuilt = minimalSavedInvoice("upd-id");
        rebuilt.setPaymentStatus(PaymentStatus.sent);

        when(repo.findById("upd-id")).thenReturn(Optional.of(existing));
        when(repo.save(any(Invoice.class))).thenReturn(rebuilt);

        final InvoiceDto dto = minimalDto();
        dto.paymentStatus = "sent";

        final InvoiceDto result = service.update("upd-id", dto);

        assertThat(result).isNotNull();
        assertThat(result.id).isEqualTo("upd-id");
        verify(repo).findById("upd-id");
        verify(repo).save(any(Invoice.class));
    }

    @Test
    void update_notFound_throwsNoSuchElement() throws Exception {
        when(repo.findById("missing")).thenReturn(Optional.empty());

        final InvoiceDto dto = minimalDto();

        assertThatThrownBy(() -> service.update("missing", dto))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invoice not found");
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Test
    void delete_callsDeleteById() throws Exception {
        doNothing().when(repo).deleteById("del-id");

        service.delete("del-id");

        verify(repo).deleteById("del-id");
    }

    // ─── get ─────────────────────────────────────────────────────────────────

    @Test
    void get_found_returnsDto() throws Exception {
        final Invoice inv = minimalSavedInvoice("get-id");
        when(repo.findById("get-id")).thenReturn(Optional.of(inv));

        final Optional<InvoiceDto> result = service.get("get-id");

        assertThat(result).isPresent();
        assertThat(result.get().id).isEqualTo("get-id");
    }

    @Test
    void get_notFound_returnsEmpty() throws Exception {
        when(repo.findById("none")).thenReturn(Optional.empty());

        final Optional<InvoiceDto> result = service.get("none");

        assertThat(result).isEmpty();
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void list_returnsPage() throws Exception {
        final Invoice inv = minimalSavedInvoice("list-id");
        final Page<Invoice> page = new PageImpl<>(List.of(inv));
        when(repo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        final Pageable pageable = PageRequest.of(0, 10);
        final Page<InvoiceDto> result = service.list(null, null, null, null, null, null, null, null, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id).isEqualTo("list-id");
    }

    // ─── resolveSort ─────────────────────────────────────────────────────────

    @Test
    void resolveSort_null_returnsDefaultSort() throws Exception {
        final Sort sort = InvoiceService.resolveSort(null);
        assertThat(sort.getOrderFor("statementDate")).isNotNull();
        assertThat(sort.getOrderFor("statementDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void resolveSort_blank_returnsDefaultSort() throws Exception {
        final Sort sort = InvoiceService.resolveSort("   ");
        assertThat(sort.getOrderFor("statementDate")).isNotNull();
        assertThat(sort.getOrderFor("statementDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void resolveSort_dueDesc() throws Exception {
        final Sort sort = InvoiceService.resolveSort("due_desc");
        assertThat(sort.getOrderFor("dueDate")).isNotNull();
        assertThat(sort.getOrderFor("dueDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void resolveSort_dueAsc() throws Exception {
        final Sort sort = InvoiceService.resolveSort("due_asc");
        assertThat(sort.getOrderFor("dueDate")).isNotNull();
        assertThat(sort.getOrderFor("dueDate").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void resolveSort_amountDesc() throws Exception {
        final Sort sort = InvoiceService.resolveSort("amount_desc");
        assertThat(sort.getOrderFor("amountDue")).isNotNull();
        assertThat(sort.getOrderFor("amountDue").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void resolveSort_amountAsc() throws Exception {
        final Sort sort = InvoiceService.resolveSort("amount_asc");
        assertThat(sort.getOrderFor("amountDue")).isNotNull();
        assertThat(sort.getOrderFor("amountDue").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void resolveSort_unknown_returnsDefault() throws Exception {
        final Sort sort = InvoiceService.resolveSort("not_a_real_sort");
        assertThat(sort.getOrderFor("statementDate")).isNotNull();
        assertThat(sort.getOrderFor("statementDate").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // ─── parseStatuses ────────────────────────────────────────────────────────

    @Test
    void parseStatuses_null_returnsEmpty() throws Exception {
        final Set<PaymentStatus> result = InvoiceService.parseStatuses(null);
        assertThat(result).isEmpty();
    }

    @Test
    void parseStatuses_blank_returnsEmpty() throws Exception {
        final Set<PaymentStatus> result = InvoiceService.parseStatuses("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void parseStatuses_allStatuses() throws Exception {
        final String csv = "pending,overdue,pendingInsurance,sent,paid,partialPayment,rejectedInsurance";
        final Set<PaymentStatus> result = InvoiceService.parseStatuses(csv);
        assertThat(result).containsExactlyInAnyOrder(
                PaymentStatus.pending,
                PaymentStatus.overdue,
                PaymentStatus.pendingInsurance,
                PaymentStatus.sent,
                PaymentStatus.paid,
                PaymentStatus.partialPayment,
                PaymentStatus.rejectedInsurance
        );
    }

    @Test
    void parseStatuses_unknownStatus_mapsToPending() throws Exception {
        final Set<PaymentStatus> result = InvoiceService.parseStatuses("unknownXYZ");
        assertThat(result).containsExactly(PaymentStatus.pending);
    }

    // ─── recordPayment ────────────────────────────────────────────────────────

    @Test
    void recordPayment_invoiceNotFound_throws() throws Exception {
        when(repo.findById("no-inv")).thenReturn(Optional.empty());

        final PaymentDto pdto = new PaymentDto();
        pdto.date = "2024-01-15T00:00:00Z";
        pdto.methodKey = "card";
        pdto.amountPaid = BigDecimal.valueOf(100);

        assertThatThrownBy(() -> service.recordPayment("no-inv", pdto, "actor"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invoice not found");
    }

    @Test
    void recordPayment_fullyPaid_statusSetToPaid() throws Exception {
        final Invoice invoice = minimalSavedInvoice("pay-id");
        invoice.setTotal(BigDecimal.valueOf(100.00));
        invoice.setAmountDue(BigDecimal.valueOf(100.00));

        final Invoice saved = minimalSavedInvoice("pay-id");
        saved.setPaymentStatus(PaymentStatus.paid);
        saved.setPaidDate(OffsetDateTime.now(ZoneOffset.UTC));

        when(repo.findById("pay-id")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final PaymentDto pdto = new PaymentDto();
        pdto.date = "2024-01-15T00:00:00Z";
        pdto.methodKey = "card";
        pdto.amountPaid = BigDecimal.valueOf(100.00);
        pdto.createdBy = "admin";

        final InvoiceDto result = service.recordPayment("pay-id", pdto, "admin");

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> PaymentStatus.paid.equals(inv.getPaymentStatus())));
    }

    @Test
    void recordPayment_partialPaid_statusSetToPartialPayment() throws Exception {
        final Invoice invoice = minimalSavedInvoice("partial-id");
        invoice.setTotal(BigDecimal.valueOf(200.00));
        invoice.setAmountDue(BigDecimal.valueOf(200.00));

        final Invoice saved = minimalSavedInvoice("partial-id");
        saved.setPaymentStatus(PaymentStatus.partialPayment);

        when(repo.findById("partial-id")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final PaymentDto pdto = new PaymentDto();
        pdto.date = "2024-01-15T00:00:00Z";
        pdto.methodKey = "card";
        pdto.amountPaid = BigDecimal.valueOf(100.00);
        pdto.createdBy = "admin";

        final InvoiceDto result = service.recordPayment("partial-id", pdto, "admin");

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> PaymentStatus.partialPayment.equals(inv.getPaymentStatus())));
    }

    @Test
    void recordPayment_createdBySet_whenBlankInPayment() throws Exception {
        final Invoice invoice = minimalSavedInvoice("createdby-id");
        invoice.setTotal(BigDecimal.valueOf(50.00));
        invoice.setAmountDue(BigDecimal.valueOf(50.00));

        final Invoice saved = minimalSavedInvoice("createdby-id");
        saved.setPaymentStatus(PaymentStatus.paid);

        when(repo.findById("createdby-id")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final PaymentDto pdto = new PaymentDto();
        pdto.date = "2024-01-15T00:00:00Z";
        pdto.methodKey = "card";
        pdto.amountPaid = BigDecimal.valueOf(50.00);
        pdto.createdBy = null; // blank — should be set from actor

        final InvoiceDto result = service.recordPayment("createdby-id", pdto, "the-actor");

        assertThat(result).isNotNull();
        // Verify a payment was added that has createdBy = "the-actor"
        verify(repo).save(argThat(inv ->
                inv.getPayments().stream()
                        .anyMatch(p -> "the-actor".equals(p.getCreatedBy()))
        ));
    }

    // ─── deletePayment ────────────────────────────────────────────────────────

    @Test
    void deletePayment_invoiceNotFound_throws() throws Exception {
        when(repo.findById("no-inv")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePayment("no-inv", "pay-1"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Invoice not found");
    }

    @Test
    void deletePayment_paymentNotFound_throws() throws Exception {
        final Invoice invoice = minimalSavedInvoice("inv-1");
        // no payments on the invoice
        when(repo.findById("inv-1")).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.deletePayment("inv-1", "nonexistent-pay"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    void deletePayment_fullyPaidAfterDelete_statusPaid() throws Exception {
        // Invoice has total=100, one payment of 100, delete a *second* payment of 0
        // After deletion, paidSum is still 100 → fully paid
        final Invoice invoice = minimalSavedInvoice("dpay-full");
        invoice.setTotal(BigDecimal.valueOf(100.00));

        final InvoicePayment keep = new InvoicePayment();
        keep.setId("keep-pay");
        keep.setAmountPaid(BigDecimal.valueOf(100.00));
        keep.setPaymentDate(OffsetDateTime.now(ZoneOffset.UTC));
        keep.setMethodKey("card");
        invoice.addPayment(keep);

        final InvoicePayment toDelete = new InvoicePayment();
        toDelete.setId("del-pay");
        toDelete.setAmountPaid(BigDecimal.ZERO);
        toDelete.setPaymentDate(OffsetDateTime.now(ZoneOffset.UTC));
        toDelete.setMethodKey("card");
        invoice.addPayment(toDelete);

        final Invoice saved = minimalSavedInvoice("dpay-full");
        saved.setPaymentStatus(PaymentStatus.paid);

        when(repo.findById("dpay-full")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.deletePayment("dpay-full", "del-pay");

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> PaymentStatus.paid.equals(inv.getPaymentStatus())));
    }

    @Test
    void deletePayment_partialAfterDelete_statusPartialPayment() throws Exception {
        // Invoice total=200, two payments of 50 each; delete one → paidSum=50, due=150 → partial
        final Invoice invoice = minimalSavedInvoice("dpay-partial");
        invoice.setTotal(BigDecimal.valueOf(200.00));

        final InvoicePayment pay1 = new InvoicePayment();
        pay1.setId("pay-1");
        pay1.setAmountPaid(BigDecimal.valueOf(50.00));
        pay1.setPaymentDate(OffsetDateTime.now(ZoneOffset.UTC));
        pay1.setMethodKey("card");
        invoice.addPayment(pay1);

        final InvoicePayment pay2 = new InvoicePayment();
        pay2.setId("pay-2");
        pay2.setAmountPaid(BigDecimal.valueOf(50.00));
        pay2.setPaymentDate(OffsetDateTime.now(ZoneOffset.UTC));
        pay2.setMethodKey("card");
        invoice.addPayment(pay2);

        final Invoice saved = minimalSavedInvoice("dpay-partial");
        saved.setPaymentStatus(PaymentStatus.partialPayment);

        when(repo.findById("dpay-partial")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.deletePayment("dpay-partial", "pay-2");

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> PaymentStatus.partialPayment.equals(inv.getPaymentStatus())));
    }

    @Test
    void deletePayment_noPaymentsAfterDelete_statusPending() throws Exception {
        // Invoice total=100, one payment of 100; delete it → paidSum=0 → pending
        final Invoice invoice = minimalSavedInvoice("dpay-pending");
        invoice.setTotal(BigDecimal.valueOf(100.00));

        final InvoicePayment pay1 = new InvoicePayment();
        pay1.setId("only-pay");
        pay1.setAmountPaid(BigDecimal.valueOf(100.00));
        pay1.setPaymentDate(OffsetDateTime.now(ZoneOffset.UTC));
        pay1.setMethodKey("card");
        invoice.addPayment(pay1);

        final Invoice saved = minimalSavedInvoice("dpay-pending");
        saved.setPaymentStatus(PaymentStatus.pending);

        when(repo.findById("dpay-pending")).thenReturn(Optional.of(invoice));
        when(repo.save(any(Invoice.class))).thenReturn(saved);

        final InvoiceDto result = service.deletePayment("dpay-pending", "only-pay");

        assertThat(result).isNotNull();
        verify(repo).save(argThat(inv -> PaymentStatus.pending.equals(inv.getPaymentStatus())));
    }

    // ─── findDuplicateByProviderAndTotal ─────────────────────────────────────

    @Test
    void findDuplicateByProviderAndTotal_nullProvider_returnsEmpty() throws Exception {
        final Optional<Invoice> result = service.findDuplicateByProviderAndTotal(null, 100.0, "INV-001");
        assertThat(result).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void findDuplicateByProviderAndTotal_nullTotal_returnsEmpty() throws Exception {
        final Optional<Invoice> result = service.findDuplicateByProviderAndTotal("Provider A", null, "INV-001");
        assertThat(result).isEmpty();
        verifyNoInteractions(repo);
    }

    @Test
    void findDuplicateByProviderAndTotal_withInvoiceNumber_callsStrictMethod() throws Exception {
        final Invoice inv = minimalSavedInvoice("dup-id");
        when(repo.findTopByProviderNameIgnoreCaseAndTotalAndInvoiceNumberOrderByCreatedAtDesc(
                eq("Provider A"), any(BigDecimal.class), eq("INV-001")))
                .thenReturn(Optional.of(inv));

        final Optional<Invoice> result = service.findDuplicateByProviderAndTotal("Provider A", 100.0, "INV-001");

        assertThat(result).isPresent();
        verify(repo).findTopByProviderNameIgnoreCaseAndTotalAndInvoiceNumberOrderByCreatedAtDesc(
                eq("Provider A"), any(BigDecimal.class), eq("INV-001"));
    }

    @Test
    void findDuplicateByProviderAndTotal_withoutInvoiceNumber_callsRangeMethod() throws Exception {
        final Invoice inv = minimalSavedInvoice("range-id");
        when(repo.findTopByProviderNameIgnoreCaseAndTotalBetweenOrderByCreatedAtDesc(
                eq("Provider B"), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(Optional.of(inv));

        final Optional<Invoice> result = service.findDuplicateByProviderAndTotal("Provider B", 200.0, null);

        assertThat(result).isPresent();
        verify(repo).findTopByProviderNameIgnoreCaseAndTotalBetweenOrderByCreatedAtDesc(
                eq("Provider B"), any(BigDecimal.class), any(BigDecimal.class));
    }
}
