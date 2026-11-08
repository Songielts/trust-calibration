package com.trustcal.entity;

import jakarta.persistence.*;

/**
 * A task domain and the capability signal the agent advertises for it
 * (historical accuracy + a benchmark score). In the CAPABILITY conditions
 * these figures are shown next to the agent's advice.
 */
@Entity
@Table(name = "domain_profile")
public class DomainProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** Advertised historical accuracy in this domain, 0-100. */
    @Column(name = "historical_accuracy", nullable = false)
    private Double historicalAccuracy;

    /** Number of past questions the historical accuracy is based on. */
    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "benchmark_name", length = 100)
    private String benchmarkName;

    /** Benchmark score, 0-100. */
    @Column(name = "benchmark_score")
    private Double benchmarkScore;

    @Column(name = "display_order")
    private Integer displayOrder;

    public DomainProfile() {
    }

    public DomainProfile(String code, String name, String description, double historicalAccuracy,
                         int sampleSize, String benchmarkName, double benchmarkScore, int displayOrder) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.historicalAccuracy = historicalAccuracy;
        this.sampleSize = sampleSize;
        this.benchmarkName = benchmarkName;
        this.benchmarkScore = benchmarkScore;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getHistoricalAccuracy() { return historicalAccuracy; }
    public void setHistoricalAccuracy(Double historicalAccuracy) { this.historicalAccuracy = historicalAccuracy; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
    public String getBenchmarkName() { return benchmarkName; }
    public void setBenchmarkName(String benchmarkName) { this.benchmarkName = benchmarkName; }
    public Double getBenchmarkScore() { return benchmarkScore; }
    public void setBenchmarkScore(Double benchmarkScore) { this.benchmarkScore = benchmarkScore; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
