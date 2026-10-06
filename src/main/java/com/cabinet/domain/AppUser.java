package com.cabinet.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_user",
        uniqueConstraints = @UniqueConstraint(columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser extends BaseEntity {

    /** 登录名 */
    private String username;

    /** BCrypt 密文 */
    private String password;

    /** REPLENISHER / OPERATOR / FINANCE / CUSTOMER_SERVICE / ADMIN */
    private String role;

    private String displayName;

    @Builder.Default
    private boolean enabled = true;
}
