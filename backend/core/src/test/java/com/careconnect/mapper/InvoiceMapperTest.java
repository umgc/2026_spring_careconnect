package com.careconnect.mapper;

import com.careconnect.dto.invoice.InvoiceDto;
import com.careconnect.dto.invoice.PaymentDto;
import com.careconnect.model.invoice.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class InvoiceMapperTest {

    // ─── Private constructor ──────────────────────────────────────────────────

    @Test
    void privateConstructor_canBeInstantiatedViaReflection() throws Exception {
        final Constructor<InvoiceMapper> ctor = InvoiceMapper.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertThatCode(ctor::newInstance).doesNotThrowAnyException();
    }

    // ─── toEntity(InvoiceDto) – null sections ────────────────────────────────

    @Test
    void toEntity_nullProvider_providerFieldsRetainDefaults() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.provider = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        // Invoice initialises providerName to "" by default; setter is never called
        assertThat(e.getProviderName()).isEqualTo("");
        assertThat(e.getProviderEmail()).isNull();
    }

    @Test
    void toEntity_nullPatient_patientFieldsRetainDefaults() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.patient = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        // Invoice initialises patientName to "" by default; setter is never called
        assertThat(e.getPatientName()).isEqualTo("");
    }

    @Test
    void toEntity_nullDates_dateFieldsNotSet() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.dates = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getStatementDate()).isNull();
        assertThat(e.getDueDate()).isNull();
    }

    @Test
    void toEntity_nullAmounts_amountFieldsNotSet() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.amounts = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getTotalCharges()).isNull();
        assertThat(e.getAmountDue()).isNull();
    }

    @Test
    void toEntity_nullPaymentReferences_referenceFieldsNotSet() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.paymentReferences = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getPaymentLink()).isNull();
        assertThat(e.getSupportedMethodsCsv()).isNull();
    }

    @Test
    void toEntity_nullCheckPayableTo_checkFieldsNotSet() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.checkPayableTo = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getCheckName()).isNull();
        assertThat(e.getCheckAddress()).isNull();
        assertThat(e.getCheckReference()).isNull();
    }

    @Test
    void toEntity_nullServices_resultIsEmptyList() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.services = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getServices()).isEmpty();
    }

    @Test
    void toEntity_nullHistory_resultIsEmptyList() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.history = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getHistory()).isEmpty();
    }

    @Test
    void toEntity_nullRecommendedActions_resultIsEmptyList() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.recommendedActions = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getRecommendedActions()).isEmpty();
    }

    @Test
    void toEntity_nullPayments_paymentsListUnchanged() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.payments = null;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getPayments()).isEmpty();
    }

    @Test
    void toEntity_emptyPaymentsList_paymentsCleared() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        dto.payments = List.of();

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getPayments()).isEmpty();
    }

    // ─── parseStatus – all 9 branches ────────────────────────────────────────

    @Test
    void parseStatus_pending_returnsPending() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("pending")).getPaymentStatus())
                .isEqualTo(PaymentStatus.pending);
    }

    @Test
    void parseStatus_overdue_returnsOverdue() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("overdue")).getPaymentStatus())
                .isEqualTo(PaymentStatus.overdue);
    }

    @Test
    void parseStatus_pendingInsurance_returnsPendingInsurance() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("pendingInsurance")).getPaymentStatus())
                .isEqualTo(PaymentStatus.pendingInsurance);
    }

    @Test
    void parseStatus_sent_returnsSent() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("sent")).getPaymentStatus())
                .isEqualTo(PaymentStatus.sent);
    }

    @Test
    void parseStatus_paid_returnsPaid() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("paid")).getPaymentStatus())
                .isEqualTo(PaymentStatus.paid);
    }

    @Test
    void parseStatus_partialPayment_returnsPartialPayment() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("partialPayment")).getPaymentStatus())
                .isEqualTo(PaymentStatus.partialPayment);
    }

    @Test
    void parseStatus_rejectedInsurance_returnsRejectedInsurance() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("rejectedInsurance")).getPaymentStatus())
                .isEqualTo(PaymentStatus.rejectedInsurance);
    }

    @Test
    void parseStatus_null_defaultsToPending() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto(null)).getPaymentStatus())
                .isEqualTo(PaymentStatus.pending);
    }

    @Test
    void parseStatus_unknown_defaultsToPending() throws Exception {
        assertThat(InvoiceMapper.toEntity(minimalDto("UNKNOWN_STATUS")).getPaymentStatus())
                .isEqualTo(PaymentStatus.pending);
    }

    // ─── joinCsv branches ────────────────────────────────────────────────────

    @Test
    void joinCsv_nullList_returnsNullCsv() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.PaymentReferences refs = new InvoiceDto.PaymentReferences();
        refs.supportedMethods = null;
        dto.paymentReferences = refs;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getSupportedMethodsCsv()).isNull();
    }

    @Test
    void joinCsv_emptyList_returnsNullCsv() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.PaymentReferences refs = new InvoiceDto.PaymentReferences();
        refs.supportedMethods = List.of();
        dto.paymentReferences = refs;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getSupportedMethodsCsv()).isNull();
    }

    @Test
    void joinCsv_validElements_joinedWithComma() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.PaymentReferences refs = new InvoiceDto.PaymentReferences();
        refs.supportedMethods = List.of("card", "check");
        dto.paymentReferences = refs;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getSupportedMethodsCsv()).isEqualTo("card,check");
    }

    @Test
    void joinCsv_blankEntriesFiltered_onlyNonBlankJoined() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.PaymentReferences refs = new InvoiceDto.PaymentReferences();
        refs.supportedMethods = Arrays.asList("card", "  ", "check");
        dto.paymentReferences = refs;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getSupportedMethodsCsv()).isEqualTo("card,check");
    }

    // ─── nz() branches ───────────────────────────────────────────────────────

    @Test
    void nz_nullProviderFields_mappedToEmptyString() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.ProviderInfo prov = new InvoiceDto.ProviderInfo();
        prov.name = null;
        prov.address = null;
        prov.phone = null;
        prov.email = null;
        dto.provider = prov;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getProviderName()).isEqualTo("");
        assertThat(e.getProviderAddress()).isEqualTo("");
        assertThat(e.getProviderPhone()).isEqualTo("");
        assertThat(e.getProviderEmail()).isNull();
    }

    @Test
    void nz_nonNullProviderField_passedThrough() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.ProviderInfo prov = new InvoiceDto.ProviderInfo();
        prov.name = "Provider A";
        prov.address = "123 St";
        prov.phone = "555-1234";
        dto.provider = prov;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getProviderName()).isEqualTo("Provider A");
    }

    // ─── toBD() branches ─────────────────────────────────────────────────────

    @Test
    void toBD_nullDouble_returnsNull() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.Amounts amounts = new InvoiceDto.Amounts();
        amounts.totalCharges = null;
        amounts.totalAdjustments = null;
        amounts.total = null;
        amounts.amountDue = null;
        dto.amounts = amounts;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getTotalCharges()).isNull();
        assertThat(e.getAmountDue()).isNull();
    }

    @Test
    void toBD_validDouble_returnsBigDecimal() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.Amounts amounts = new InvoiceDto.Amounts();
        amounts.totalCharges = 100.0;
        amounts.totalAdjustments = 5.0;
        amounts.total = 95.0;
        amounts.amountDue = 95.0;
        dto.amounts = amounts;

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getTotalCharges()).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        assertThat(e.getAmountDue()).isEqualByComparingTo(BigDecimal.valueOf(95.0));
    }

    // ─── History – version null → 1 ─────────────────────────────────────────

    @Test
    void history_nullVersion_defaultsToOne() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.HistoryEntry h = new InvoiceDto.HistoryEntry();
        h.version = null;
        h.changes = null;
        h.userId = null;
        h.action = null;
        h.details = null;
        h.timestamp = "2024-01-01T00:00:00Z";
        dto.history = List.of(h);

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getHistory()).hasSize(1);
        assertThat(e.getHistory().get(0).getVersion()).isEqualTo(1);
    }

    @Test
    void history_nonNullVersion_usedAsIs() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.HistoryEntry h = new InvoiceDto.HistoryEntry();
        h.version = 3;
        h.timestamp = "2024-01-01T00:00:00Z";
        dto.history = List.of(h);

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getHistory().get(0).getVersion()).isEqualTo(3);
    }

    // ─── ServiceLine – null serviceDate ──────────────────────────────────────

    @Test
    void serviceLine_nullServiceDate_parsedAsNull() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.ServiceLine sl = new InvoiceDto.ServiceLine();
        sl.serviceDate = null;
        sl.charge = 50.0;
        sl.patientBalance = 10.0;
        sl.insuranceAdjustments = 0.0;
        dto.services = List.of(sl);

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getServices()).hasSize(1);
        assertThat(e.getServices().get(0).getServiceDate()).isNull();
    }

    @Test
    void serviceLine_nonNullServiceDate_parsed() throws Exception {
        final InvoiceDto dto = minimalDto("pending");
        final InvoiceDto.ServiceLine sl = new InvoiceDto.ServiceLine();
        sl.serviceDate = "2024-01-10";
        sl.charge = 100.0;
        sl.patientBalance = 20.0;
        sl.insuranceAdjustments = 0.0;
        dto.services = List.of(sl);

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getServices().get(0).getServiceDate()).isNotNull();
    }

    // ─── toEntity(InvoiceDto) – full happy path ───────────────────────────────

    @Test
    void toEntity_allSectionsPopulated_mapsAllFields() throws Exception {
        final InvoiceDto dto = new InvoiceDto();
        dto.id = "INV-001";
        dto.invoiceNumber = "2024-001";
        dto.paymentStatus = "paid";
        dto.billedToInsurance = true;
        dto.aiSummary = "AI summary";
        dto.createdBy = "admin";
        dto.updatedBy = "admin";
        dto.documentLink = "http://docs/invoice.pdf";
        dto.createdAt = null;
        dto.updatedAt = null;

        final InvoiceDto.ProviderInfo prov = new InvoiceDto.ProviderInfo();
        prov.name = "Provider A";
        prov.address = "123 St";
        prov.phone = "555-1234";
        prov.email = "prov@example.com";
        dto.provider = prov;

        final InvoiceDto.PatientInfo patient = new InvoiceDto.PatientInfo();
        patient.name = "John Doe";
        patient.address = "456 Ave";
        patient.accountNumber = "ACC123";
        patient.billingAddress = "456 Ave";
        dto.patient = patient;

        final InvoiceDto.InvoiceDates dates = new InvoiceDto.InvoiceDates();
        dates.statementDate = "2024-01-01T00:00:00Z";
        dates.dueDate = "2024-01-31T00:00:00Z";
        dates.paidDate = "2024-01-15T00:00:00Z";
        dto.dates = dates;

        final InvoiceDto.Amounts amounts = new InvoiceDto.Amounts();
        amounts.totalCharges = 200.0;
        amounts.totalAdjustments = 20.0;
        amounts.total = 180.0;
        amounts.amountDue = 180.0;
        dto.amounts = amounts;

        final InvoiceDto.PaymentReferences refs = new InvoiceDto.PaymentReferences();
        refs.paymentLink = "http://pay.link";
        refs.qrCodeUrl = "http://qr.url";
        refs.notes = "Pay ASAP";
        refs.supportedMethods = List.of("card", "check");
        dto.paymentReferences = refs;

        final InvoiceDto.CheckPayableTo check = new InvoiceDto.CheckPayableTo();
        check.name = "Provider A";
        check.address = "123 St";
        check.reference = "REF001";
        dto.checkPayableTo = check;

        final InvoiceDto.ServiceLine sl = new InvoiceDto.ServiceLine();
        sl.description = "Nursing visit";
        sl.serviceCode = "T1019";
        sl.serviceDate = "2024-01-10";
        sl.charge = 100.0;
        sl.patientBalance = 20.0;
        sl.insuranceAdjustments = 0.0;
        dto.services = List.of(sl);

        final InvoiceDto.HistoryEntry he = new InvoiceDto.HistoryEntry();
        he.version = 1;
        he.changes = "initial";
        he.userId = "admin";
        he.action = "CREATE";
        he.details = "Created invoice";
        he.timestamp = "2024-01-01T00:00:00Z";
        dto.history = List.of(he);

        dto.recommendedActions = List.of("Pay now", "Call office");

        final PaymentDto pd = new PaymentDto();
        pd.id = "PAY-001";
        pd.confirmationNumber = "CONF123";
        pd.date = "2024-01-15T00:00:00Z";
        pd.methodKey = "card";
        pd.amountPaid = BigDecimal.valueOf(180.0);
        pd.planEnabled = false;
        pd.createdBy = "admin";
        dto.payments = List.of(pd);

        final Invoice e = InvoiceMapper.toEntity(dto);

        assertThat(e.getId()).isEqualTo("INV-001");
        assertThat(e.getInvoiceNumber()).isEqualTo("2024-001");
        assertThat(e.getProviderName()).isEqualTo("Provider A");
        assertThat(e.getPatientName()).isEqualTo("John Doe");
        assertThat(e.getPaymentStatus()).isEqualTo(PaymentStatus.paid);
        assertThat(e.isBilledToInsurance()).isTrue();
        assertThat(e.getTotalCharges()).isEqualByComparingTo(BigDecimal.valueOf(200.0));
        assertThat(e.getSupportedMethodsCsv()).isEqualTo("card,check");
        assertThat(e.getCheckName()).isEqualTo("Provider A");
        assertThat(e.getAiSummary()).isEqualTo("AI summary");
        assertThat(e.getCreatedBy()).isEqualTo("admin");
        assertThat(e.getDocumentLink()).isEqualTo("http://docs/invoice.pdf");
        assertThat(e.getServices()).hasSize(1);
        assertThat(e.getHistory()).hasSize(1);
        assertThat(e.getRecommendedActions()).hasSize(2);
        assertThat(e.getPayments()).hasSize(1);
    }

    // ─── toDto(Invoice) ──────────────────────────────────────────────────────

    @Test
    void toDto_fullEntity_mapsAllFields() throws Exception {
        final Invoice e = fullEntity();

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.id).isEqualTo("INV-001");
        assertThat(dto.invoiceNumber).isEqualTo("2024-001");
        assertThat(dto.provider).isNotNull();
        assertThat(dto.provider.name).isEqualTo("Provider A");
        assertThat(dto.patient).isNotNull();
        assertThat(dto.patient.name).isEqualTo("John Doe");
        assertThat(dto.paymentStatus).isEqualTo(PaymentStatus.paid.name());
        assertThat(dto.billedToInsurance).isTrue();
        assertThat(dto.amounts).isNotNull();
        assertThat(dto.amounts.totalCharges).isEqualTo(200.0);
        assertThat(dto.paymentReferences).isNotNull();
        assertThat(dto.aiSummary).isEqualTo("AI summary");
        assertThat(dto.services).hasSize(1);
        assertThat(dto.history).hasSize(1);
        assertThat(dto.recommendedActions).containsExactly("Do something");
        assertThat(dto.payments).hasSize(1);
    }

    // ─── toDto – checkPayableTo branches ─────────────────────────────────────

    @Test
    void toDto_checkPayableTo_allNull_notIncluded() throws Exception {
        final Invoice e = fullEntity();
        e.setCheckName(null);
        e.setCheckAddress(null);
        e.setCheckReference(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.checkPayableTo).isNull();
    }

    @Test
    void toDto_checkPayableTo_nameSet_isIncluded() throws Exception {
        final Invoice e = fullEntity();
        e.setCheckName("Provider A");
        e.setCheckAddress(null);
        e.setCheckReference(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.checkPayableTo).isNotNull();
        assertThat(dto.checkPayableTo.name).isEqualTo("Provider A");
    }

    @Test
    void toDto_checkPayableTo_addressSet_isIncluded() throws Exception {
        final Invoice e = fullEntity();
        e.setCheckName(null);
        e.setCheckAddress("123 Check St");
        e.setCheckReference(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.checkPayableTo).isNotNull();
        assertThat(dto.checkPayableTo.address).isEqualTo("123 Check St");
    }

    @Test
    void toDto_checkPayableTo_referenceSet_isIncluded() throws Exception {
        final Invoice e = fullEntity();
        e.setCheckName(null);
        e.setCheckAddress(null);
        e.setCheckReference("REF-999");

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.checkPayableTo).isNotNull();
        assertThat(dto.checkPayableTo.reference).isEqualTo("REF-999");
    }

    // ─── splitCsv branches ───────────────────────────────────────────────────

    @Test
    void splitCsv_null_returnsEmptyList() throws Exception {
        final Invoice e = fullEntity();
        e.setSupportedMethodsCsv(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.paymentReferences.supportedMethods).isEmpty();
    }

    @Test
    void splitCsv_blank_returnsEmptyList() throws Exception {
        final Invoice e = fullEntity();
        e.setSupportedMethodsCsv("   ");

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.paymentReferences.supportedMethods).isEmpty();
    }

    @Test
    void splitCsv_validCsv_returnsList() throws Exception {
        final Invoice e = fullEntity();
        e.setSupportedMethodsCsv("card,check");

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.paymentReferences.supportedMethods).containsExactly("card", "check");
    }

    // ─── toD() branches ──────────────────────────────────────────────────────

    @Test
    void toD_nullBigDecimal_returnsNull() throws Exception {
        final Invoice e = fullEntity();
        e.setTotalCharges(null);
        e.setTotalAdjustments(null);
        e.setTotal(null);
        e.setAmountDue(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.amounts.totalCharges).isNull();
        assertThat(dto.amounts.amountDue).isNull();
    }

    @Test
    void toD_nonNullBigDecimal_returnsDouble() throws Exception {
        final Invoice e = fullEntity();

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.amounts.totalCharges).isEqualTo(200.0);
    }

    // ─── toDto – empty collections ───────────────────────────────────────────

    @Test
    void toDto_emptyServicesList_emptyInDto() throws Exception {
        final Invoice e = fullEntity();
        e.setServices(new ArrayList<>());

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.services).isEmpty();
    }

    @Test
    void toDto_emptyHistoryList_emptyInDto() throws Exception {
        final Invoice e = fullEntity();
        e.setHistory(new ArrayList<>());

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.history).isEmpty();
    }

    @Test
    void toDto_emptyRecommendedActions_emptyInDto() throws Exception {
        final Invoice e = fullEntity();
        e.setRecommendedActions(new ArrayList<>());

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.recommendedActions).isEmpty();
    }

    @Test
    void toDto_emptyPaymentsList_emptyInDto() throws Exception {
        final Invoice e = fullEntity();
        e.getPayments().clear();

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.payments).isEmpty();
    }

    // ─── toDto – nullable timestamps ─────────────────────────────────────────

    @Test
    void toDto_nullCreatedAndUpdatedAt_formatsAsNull() throws Exception {
        final Invoice e = fullEntity();
        e.setCreatedAt(null);
        e.setUpdatedAt(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.createdAt).isNull();
        assertThat(dto.updatedAt).isNull();
    }

    @Test
    void toDto_serviceLine_nullServiceDate_formatsAsNull() throws Exception {
        final Invoice e = fullEntity();
        e.getServices().get(0).setServiceDate(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.services.get(0).serviceDate).isNull();
    }

    @Test
    void toDto_nullPaidDate_formatsAsNull() throws Exception {
        final Invoice e = fullEntity();
        e.setPaidDate(null);

        final InvoiceDto dto = InvoiceMapper.toDto(e);

        assertThat(dto.dates.paidDate).isNull();
    }

    // ─── toDto(InvoicePayment) ────────────────────────────────────────────────

    @Test
    void toDto_invoicePayment_allFields_mapped() throws Exception {
        final InvoicePayment p = new InvoicePayment();
        p.setId("PAY-001");
        p.setConfirmationNumber("CONF123");
        p.setPaymentDate(OffsetDateTime.parse("2024-01-15T00:00:00Z"));
        p.setMethodKey("card");
        p.setAmountPaid(BigDecimal.valueOf(180.0));
        p.setPlanEnabled(true);
        p.setPlanMonths(12);
        p.setCreatedBy("admin");

        final PaymentDto d = InvoiceMapper.toDto(p);

        assertThat(d.id).isEqualTo("PAY-001");
        assertThat(d.confirmationNumber).isEqualTo("CONF123");
        assertThat(d.date).isNotNull();
        assertThat(d.methodKey).isEqualTo("card");
        assertThat(d.amountPaid).isEqualByComparingTo(BigDecimal.valueOf(180.0));
        assertThat(d.planEnabled).isTrue();
        assertThat(d.planDurationMonths).isEqualTo(12);
        assertThat(d.createdBy).isEqualTo("admin");
    }

    @Test
    void toDto_invoicePayment_nullPaymentDate_dateIsNull() throws Exception {
        final InvoicePayment p = new InvoicePayment();
        p.setPaymentDate(null);

        final PaymentDto d = InvoiceMapper.toDto(p);

        assertThat(d.date).isNull();
    }

    @Test
    void toDto_invoicePayment_planNotEnabled_planEnabledFalse() throws Exception {
        final InvoicePayment p = new InvoicePayment();
        p.setPlanEnabled(false);
        p.setPaymentDate(OffsetDateTime.parse("2024-01-15T00:00:00Z"));

        final PaymentDto d = InvoiceMapper.toDto(p);

        assertThat(d.planEnabled).isFalse();
    }

    // ─── toEntity(Invoice, PaymentDto) ───────────────────────────────────────

    @Test
    void toEntity_paymentDto_nullId_idIsUuidDefault() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = null;
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = "card";
        d.planEnabled = false;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getInvoice()).isSameAs(invoice);
    }

    @Test
    void toEntity_paymentDto_blankId_idIsNotOverwritten() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = "   ";
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = "card";
        d.planEnabled = false;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.getId()).isNotEqualTo("   ");
    }

    @Test
    void toEntity_paymentDto_validId_idIsSet() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = "EXPLICIT-ID";
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = "check";
        d.planEnabled = true;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.getId()).isEqualTo("EXPLICIT-ID");
        assertThat(p.isPlanEnabled()).isTrue();
    }

    @Test
    void toEntity_paymentDto_nullMethodKey_defaultsToOnline() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = null;
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = null;
        d.planEnabled = false;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.getMethodKey()).isEqualTo("online");
    }

    @Test
    void toEntity_paymentDto_nullPlanEnabled_isFalse() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = null;
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = "card";
        d.planEnabled = null;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.isPlanEnabled()).isFalse();
    }

    @Test
    void toEntity_paymentDto_truePlanEnabled_isTrue() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = null;
        d.date = "2024-01-15T00:00:00Z";
        d.methodKey = "card";
        d.planEnabled = true;

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.isPlanEnabled()).isTrue();
    }

    @Test
    void toEntity_paymentDto_allFields_mapped() throws Exception {
        final Invoice invoice = new Invoice();
        final PaymentDto d = new PaymentDto();
        d.id = "PAY-XYZ";
        d.confirmationNumber = "CONF456";
        d.date = "2024-06-01T12:00:00Z";
        d.methodKey = "telephone";
        d.amountPaid = BigDecimal.valueOf(50.0);
        d.planEnabled = false;
        d.planDurationMonths = 6;
        d.createdBy = "nurse";

        final InvoicePayment p = InvoiceMapper.toEntity(invoice, d);

        assertThat(p.getId()).isEqualTo("PAY-XYZ");
        assertThat(p.getConfirmationNumber()).isEqualTo("CONF456");
        assertThat(p.getPaymentDate()).isNotNull();
        assertThat(p.getMethodKey()).isEqualTo("telephone");
        assertThat(p.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
        assertThat(p.isPlanEnabled()).isFalse();
        assertThat(p.getPlanMonths()).isEqualTo(6);
        assertThat(p.getCreatedBy()).isEqualTo("nurse");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private InvoiceDto minimalDto(String status) {
        final InvoiceDto dto = new InvoiceDto();
        dto.paymentStatus = status;
        return dto;
    }

    private Invoice fullEntity() throws Exception {
        final Invoice e = new Invoice();
        e.setId("INV-001");
        e.setInvoiceNumber("2024-001");
        e.setProviderName("Provider A");
        e.setProviderAddress("123 St");
        e.setProviderPhone("555-1234");
        e.setProviderEmail("prov@example.com");
        e.setPatientName("John Doe");
        e.setPatientAddress("456 Ave");
        e.setPatientAccountNumber("ACC123");
        e.setPatientBillingAddress("456 Ave");
        e.setStatementDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        e.setDueDate(OffsetDateTime.parse("2024-01-31T00:00:00Z"));
        e.setPaidDate(OffsetDateTime.parse("2024-01-15T00:00:00Z"));
        e.setPaymentStatus(PaymentStatus.paid);
        e.setBilledToInsurance(true);
        e.setTotalCharges(BigDecimal.valueOf(200.0));
        e.setTotalAdjustments(BigDecimal.valueOf(20.0));
        e.setTotal(BigDecimal.valueOf(180.0));
        e.setAmountDue(BigDecimal.valueOf(180.0));
        e.setPaymentLink("http://pay.link");
        e.setQrCodeUrl("http://qr.url");
        e.setPaymentNotes("Pay ASAP");
        e.setSupportedMethodsCsv("card,check");
        e.setCheckName("Provider A");
        e.setCheckAddress("123 St");
        e.setCheckReference("REF001");
        e.setAiSummary("AI summary");
        e.setCreatedBy("admin");
        e.setUpdatedBy("admin");
        e.setDocumentLink("http://docs/invoice.pdf");
        e.setCreatedAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        e.setUpdatedAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"));

        final ServiceLine sl = new ServiceLine();
        sl.setDescription("Nursing visit");
        sl.setServiceCode("T1019");
        sl.setServiceDate(OffsetDateTime.parse("2024-01-10T00:00:00Z"));
        sl.setCharge(BigDecimal.valueOf(100.0));
        sl.setPatientBalance(BigDecimal.valueOf(20.0));
        sl.setInsuranceAdjustments(BigDecimal.ZERO);
        e.setServices(new ArrayList<>(List.of(sl)));

        final HistoryEntry he = new HistoryEntry();
        he.setVersion(1);
        he.setChanges("initial");
        he.setUserId("admin");
        he.setAction("CREATE");
        he.setDetails("Created");
        he.setTimestamp(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        e.setHistory(new ArrayList<>(List.of(he)));

        final RecommendedAction ra = new RecommendedAction();
        ra.setActionText("Do something");
        e.setRecommendedActions(new ArrayList<>(List.of(ra)));

        final InvoicePayment p = new InvoicePayment();
        p.setId("PAY-001");
        p.setConfirmationNumber("CONF123");
        p.setPaymentDate(OffsetDateTime.parse("2024-01-15T00:00:00Z"));
        p.setMethodKey("card");
        p.setAmountPaid(BigDecimal.valueOf(180.0));
        p.setPlanEnabled(false);
        p.setCreatedBy("admin");
        e.getPayments().add(p);

        return e;
    }
}
