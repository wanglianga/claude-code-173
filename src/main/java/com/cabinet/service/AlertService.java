package com.cabinet.service;

import com.cabinet.domain.Alert;
import com.cabinet.domain.Cabinet;
import com.cabinet.domain.enums.AlertStatus;
import com.cabinet.domain.enums.AlertType;
import com.cabinet.repo.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional
    public Alert raise(Cabinet cabinet, AlertType type, String severity, String message,
                       String bizType, Long bizId) {
        Alert alert = Alert.builder()
                .cabinet(cabinet)
                .alertType(type)
                .severity(severity)
                .message(message)
                .bizType(bizType)
                .bizId(bizId)
                .status(AlertStatus.OPEN)
                .build();
        return alertRepository.save(alert);
    }

    @Transactional
    public void resolve(Long alertId, String resolution) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setStatus(AlertStatus.RESOLVED);
            a.setResolution(resolution);
            a.setResolvedAt(java.time.LocalDateTime.now());
        });
    }
}
