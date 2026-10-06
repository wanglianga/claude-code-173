package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RestockItemRequest(
        @NotBlank String batchNo,
        @NotBlank String slotCode,
        /** 设备视觉/RFID 复核出的实际货道；与 slotCode 不同即“放错货道”。 */
        String actualSlotCode,
        @Positive int qty,
        @NotBlank String tempRequirement,
        /** 补货时货道卡货（设备上报）。 */
        boolean jammed) {
}
