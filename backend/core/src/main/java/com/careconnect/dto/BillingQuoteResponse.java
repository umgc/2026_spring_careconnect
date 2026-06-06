package com.careconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response with itemized billing breakdown for a subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingQuoteResponse {
  private Long tierId;
  private String tierName;
  private Long subtotalCents; // subscription price in cents
  private Long taxCents; // calculated tax in cents
  private Long totalCents; // subtotal + tax in cents
  private String currency; // "USD" or similar
  private Double taxRate; // e.g., 0.0825 for 8.25%
  private String taxJurisdiction; // e.g., "CA" or "CA - California"
  private String errorMessage; // if tax calc failed
}
