package com.uvend.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record SettlementRequest(@NotNull Long supplierId,
                                @NotNull LocalDate periodStart,
                                @NotNull LocalDate periodEnd) {
}
