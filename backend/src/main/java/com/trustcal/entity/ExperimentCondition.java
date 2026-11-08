package com.trustcal.entity;

/**
 * 2 x 2 between-subjects design:
 * capability signal (shown / hidden) x step-by-step reasoning (shown / hidden).
 */
public enum ExperimentCondition {
    CONTROL(false, false, "Control (answer only)"),
    CAPABILITY(true, false, "Capability signal"),
    REASONING(false, true, "Step-by-step reasoning"),
    CAPABILITY_REASONING(true, true, "Capability + reasoning");

    private final boolean showCapability;
    private final boolean showReasoning;
    private final String label;

    ExperimentCondition(boolean showCapability, boolean showReasoning, String label) {
        this.showCapability = showCapability;
        this.showReasoning = showReasoning;
        this.label = label;
    }

    public boolean isShowCapability() {
        return showCapability;
    }

    public boolean isShowReasoning() {
        return showReasoning;
    }

    public String getLabel() {
        return label;
    }
}
