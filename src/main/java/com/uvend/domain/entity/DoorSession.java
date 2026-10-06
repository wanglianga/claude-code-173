package com.uvend.domain.entity;

import com.uvend.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "door_session")
@Getter @Setter @NoArgsConstructor
public class DoorSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_no", nullable = false, unique = true, length = 48)
    private String sessionNo;
    @Column(name = "machine_id", nullable = false)
    private Long machineId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "opened_at")
    private Instant openedAt = Instant.now();
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "video_clip_url", length = 512)
    private String videoClipUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.OPEN;
    private String note;
}
