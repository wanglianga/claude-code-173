package com.uvend.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RestockRequest(
        @NotBlank String machineCode,
        @NotBlank(message = "补货照片必传") String photoUrl,
        String note,
        @NotEmpty @Valid List<RestockItemRequest> items) {
}
