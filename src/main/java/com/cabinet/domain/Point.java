package com.cabinet.domain;

import com.cabinet.domain.enums.LocationType;
import jakarta.persistence.*;
import lombok.*;

/** 点位：社区/办公楼/医院 */
@Entity
@Table(name = "point_location",
        uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Point extends BaseEntity {

    private String code;
    private String name;

    @Enumerated(EnumType.STRING)
    private LocationType type;

    private String address;
}
