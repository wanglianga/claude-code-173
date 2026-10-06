package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ComplaintRequest(@NotBlank String orderNo, @NotBlank String type, String description) {
}
