package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record StocktakeRequest(@NotBlank String machineCode, String note, List<CountLine> counts) {
    public record CountLine(@NotBlank String slotCode, String batchNo, int actualQty) {}
}
