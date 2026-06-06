package com.careconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class CompetencyScaleDtos {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompetencyScaleItem {
    private int value;
    private String label;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CompetencyScaleResponse {
    private int min;
    private int max;
    private Map<Integer, String> labels;
    private List<CompetencyScaleItem> items;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UpdateCompetencyScaleRequest {
    private Integer min;
    private Integer max;
    private Map<Integer, String> labels;
  }
}

