package com.careconnect.service;

import com.careconnect.dto.BillingVerifyRequest;
import com.careconnect.dto.BillingVerifyResponse;

public interface BillingService {
  BillingVerifyResponse verifyReceipt(BillingVerifyRequest request) throws Exception;
}
