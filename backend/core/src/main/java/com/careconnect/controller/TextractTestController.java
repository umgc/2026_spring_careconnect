package com.careconnect.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/api/test")
public class TextractTestController {

    private final TextractClient textractClient;

    @Autowired
    private ChatModel chatModel;

    public TextractTestController(TextractClient textractClient) {
        this.textractClient = textractClient;
    }

@PostMapping("/extract-invoice")
public String extractInvoice(@RequestBody Map<String, Object> body) {

    List<String> lines = (List<String>) body.get("lines");
    String invoiceText = String.join("\n", lines);

    String prompt = """
You are an invoice data extraction engine.

Extract structured invoice data from the text below.

Return ONLY valid JSON matching this schema:

{
  "invoice_number": "",
  "invoice_date": "",
  "payment_due_date": "",
  "vendor_name": "",
  "vendor_email": "",
  "vendor_address": "",
  "bill_to_name": "",
  "bill_to_address": "",
  "line_items": [
    {
      "description": "",
      "quantity": "",
      "unit_cost": "",
      "amount": ""
    }
  ],
  "subtotal": "",
  "tax": "",
  "total": "",
  "amount_due": ""
}

Rules:
- Return ONLY JSON
- No explanations
- No markdown
- No extra text

Invoice Text:
""" + invoiceText;

    ChatResponse response = chatModel.chat(UserMessage.from(prompt));

    return response.aiMessage().text();
}
}