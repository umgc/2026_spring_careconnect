package com.careconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to calculate billing quote (subtotal + taxes + total for a subscription tier)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingQuoteRequest {
  private Long tierId;
  private Long userId; // or fetch from JWT
  private String state; // 2-letter state code (e.g., "CA")
  // Optional: full address for comprehensive tax calc
  private String postalCode;
  private String city;
}
