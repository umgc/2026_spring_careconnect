
package com.careconnect.service.invoice;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "careconnect.llm.enabled", havingValue = "true", matchIfMissing = false)
public class LlmExtractionService {

    private final @Qualifier("chatModel") ChatModel chatModel;

    /**
     * Returns the raw JSON string produced by the LLM.
     * You can persist this or map it to Invoice using Jackson.
     */
    public String extractInvoiceData(String rawInvoiceText) {
        String systemMessageText = """
You are a structured invoice data extraction engine.

Extract invoice data from the provided text.

Return ONLY valid JSON matching this schema exactly:

{
  "id": "",
  "invoiceNumber": "",
  "provider": {
    "name": "",
    "address": "",
    "phone": "",
    "email": ""
  },
  "patient": {
    "name": "",
    "address": "",
    "accountNumber": "",
    "billingAddress": ""
  },
  "dates": {
    "statementDate": "",
    "dueDate": "",
    "paidDate": ""
  },
  "services": [
    {
      "description": "",
      "serviceCode": "",
      "serviceDate": "",
      "quantity": 0,
      "charge": 0.0,
      "patientBalance": 0.0,
      "insuranceAdjustments": 0.0
    }
  ],
  "paymentStatus": "",
  "billedToInsurance": false,
  "amounts": {
    "totalCharges": 0.0,
    "totalAdjustments": 0.0,
    "total": 0.0,
    "amountDue": 0.0
  },
  "paymentReferences": {
    "paymentLink": "",
    "qrCodeUrl": "",
    "notes": "",
    "supportedMethods": []
  },
  "checkPayableTo": {
    "name": "",
    "address": "",
    "reference": ""
  },
  "aiSummary": "",
  "recommendedActions": []
}

FIELD REQUIREMENTS:
- invoiceNumber → invoice number
- statementDate → invoice date
- dueDate → payment due date
- provider.name → vendor name
- provider.email → vendor email
- provider.address → vendor address
- patient.name → bill to name
- patient.address → bill to address
- services[].description → line item description
- services[].quantity → quantity
- services[].charge → unit cost if available, otherwise total line amount
- amounts.totalCharges → subtotal
- amounts.totalAdjustments → tax if present
- amounts.total → total
- amounts.amountDue → amount due
- aiSummary → 1-2 sentence summary of invoice

RULES:
- Return ONLY JSON
- No explanations
- No markdown
- No extra text
- If a field is missing, use empty string or 0.0
- Do not invent data
- Numeric fields must be numbers (no $ symbols)
- Dates must be ISO 8601 format (YYYY-MM-DD)
""";


        final var messages = List.of(
                SystemMessage.from(systemMessageText),
                new UserMessage(rawInvoiceText)
        );
        ChatResponse response = chatModel.chat(messages);
        String text = (response != null && response.aiMessage() != null)
                ? response.aiMessage().text()
                : "";
        return text == null ? "" : text.trim();
    }
}
