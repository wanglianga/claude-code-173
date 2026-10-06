package com.uvend.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record EventBatchRequest(@NotEmpty @Valid List<EventRequest> events) {
}
