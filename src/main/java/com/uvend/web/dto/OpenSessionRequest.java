package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenSessionRequest(
        @NotBlank String machineCode,
        String videoClipUrl) {
}
