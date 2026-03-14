package com.careconnect.model.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceResultTest {

    // ─── Builder: InvoiceResult ───────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final Map<String, String> extra = Map.of("poNumber", "PO-001");
        final List<InvoiceResult.InvoiceItem> items = List.of(
                InvoiceResult.InvoiceItem.builder()
                        .description("Office Visit")
                        .productCode("99213")
                        .unit("each")
                        .quantity(new BigDecimal("1"))
                        .unitPrice(new BigDecimal("200.00"))
                        .amount(new BigDecimal("200.00"))
                        .build()
        );

        final InvoiceResult result = InvoiceResult.builder()
                .vendorName("ABC Clinic")
                .invoiceId("INV-001")
                .invoiceDate("2025-01-15")
                .dueDate("2025-02-15")
                .currency("USD")
                .subtotal(new BigDecimal("200.00"))
                .tax(new BigDecimal("10.00"))
                .total(new BigDecimal("210.00"))
                .purchaseOrder("PO-001")
                .otherFields(extra)
                .items(items)
                .build();

        assertThat(result.getVendorName()).isEqualTo("ABC Clinic");
        assertThat(result.getInvoiceId()).isEqualTo("INV-001");
        assertThat(result.getInvoiceDate()).isEqualTo("2025-01-15");
        assertThat(result.getDueDate()).isEqualTo("2025-02-15");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getSubtotal()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getTax()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(result.getTotal()).isEqualByComparingTo(new BigDecimal("210.00"));
        assertThat(result.getPurchaseOrder()).isEqualTo("PO-001");
        assertThat(result.getOtherFields()).containsEntry("poNumber", "PO-001");
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    void builder_defaults_nullFields() throws Exception {
        final InvoiceResult result = InvoiceResult.builder().build();

        assertThat(result.getVendorName()).isNull();
        assertThat(result.getInvoiceId()).isNull();
        assertThat(result.getItems()).isNull();
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final InvoiceResult result = InvoiceResult.builder().build();

        result.setVendorName("XYZ Medical");
        result.setInvoiceId("INV-999");
        result.setCurrency("USD");

        assertThat(result.getVendorName()).isEqualTo("XYZ Medical");
        assertThat(result.getInvoiceId()).isEqualTo("INV-999");
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    // ─── equals() and hashCode() ──────────────────────────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final InvoiceResult r1 = InvoiceResult.builder().invoiceId("INV-001").vendorName("Clinic A").build();
        final InvoiceResult r2 = InvoiceResult.builder().invoiceId("INV-001").vendorName("Clinic A").build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final InvoiceResult r1 = InvoiceResult.builder().invoiceId("INV-001").build();
        final InvoiceResult r2 = InvoiceResult.builder().invoiceId("INV-002").build();

        assertThat(r1).isNotEqualTo(r2);
    }

    // ─── Nested InvoiceItem ────────────────────────────────────────────────────

    @Test
    void invoiceItem_builder_setsAllFields() throws Exception {
        final InvoiceResult.InvoiceItem item = InvoiceResult.InvoiceItem.builder()
                .description("Blood Panel")
                .productCode("85025")
                .unit("each")
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("50.00"))
                .amount(new BigDecimal("100.00"))
                .build();

        assertThat(item.getDescription()).isEqualTo("Blood Panel");
        assertThat(item.getProductCode()).isEqualTo("85025");
        assertThat(item.getUnit()).isEqualTo("each");
        assertThat(item.getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void invoiceItem_equals_sameFields_returnsTrue() throws Exception {
        final InvoiceResult.InvoiceItem i1 = InvoiceResult.InvoiceItem.builder()
                .description("Visit").productCode("99213").amount(new BigDecimal("200.00")).build();
        final InvoiceResult.InvoiceItem i2 = InvoiceResult.InvoiceItem.builder()
                .description("Visit").productCode("99213").amount(new BigDecimal("200.00")).build();

        assertThat(i1).isEqualTo(i2);
        assertThat(i1.hashCode()).isEqualTo(i2.hashCode());
    }

    @Test
    void invoiceItem_setters_updateFields() throws Exception {
        final InvoiceResult.InvoiceItem item = InvoiceResult.InvoiceItem.builder().build();

        item.setDescription("Updated Desc");
        item.setProductCode("99214");
        item.setQuantity(new BigDecimal("3"));

        assertThat(item.getDescription()).isEqualTo("Updated Desc");
        assertThat(item.getProductCode()).isEqualTo("99214");
        assertThat(item.getQuantity()).isEqualByComparingTo(new BigDecimal("3"));
    }
}
