package com.tubereturns.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "description")
    private String description;

    public FeatureFlag(String key, boolean enabled, String description) {
        this.key = key;
        this.enabled = enabled;
        this.description = description;
    }
}
