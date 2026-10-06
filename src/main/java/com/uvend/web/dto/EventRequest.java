package com.uvend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 柜内事件上报：
 * type=PICK / PUT_BACK 需 slotCode、batchNo、qty；
 * type=RECOGNITION 需 method、confidence，可带 productSku/batchNo；
 * type=WEIGHT / RFID / TEMPERATURE 走 detail；
 * type=JAM 需 slotCode。
 */
public record EventRequest(
        @NotBlank String type,
        String method,
        String slotCode,
        String batchNo,
        String productSku,
        Integer qty,
        BigDecimal confidence,
        java.util.Map<String, Object> detail) {
}
