package com.careconnect.dto;

import lombok.Data;

@Data
public class BillingVerifyRequest {
  private Long userId;
  private String platform; // APPLE or GOOGLE
  private String receipt; // Apple base64 receipt or Google purchase token
  private String productId; // product / price id
  private String packageName; // optional for Google
}
