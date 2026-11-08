package com.trustcal.entity;

import jakarta.persistence.*;

/** Simple key-value store for study settings that the researcher changes at run time. */
@Entity
@Table(name = "study_setting")
public class StudySetting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", length = 255)
    private String value;

    public StudySetting() {
    }

    public StudySetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
