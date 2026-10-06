package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AssortmentRequest(@NotBlank String machineCode, @NotBlank String slotCode,
                                String newProductSku, String reason) {
}
