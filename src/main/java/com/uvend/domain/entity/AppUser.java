package com.uvend.domain.entity;

import com.uvend.domain.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "display_name", nullable = false)
    private String displayName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24)
    private Role role;
    @Column(name = "supplier_id")
    private Long supplierId;
    private Boolean active = true;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public AppUser(String username, String passwordHash, String displayName, Role role) {
        this.username = username; this.passwordHash = passwordHash;
        this.displayName = displayName; this.role = role;
    }
}
