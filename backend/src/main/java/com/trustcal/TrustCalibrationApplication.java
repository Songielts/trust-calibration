package com.trustcal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the experiment platform for the study
 * "Showing Strength to Earn Trust? Capability Signals and Trust Calibration
 * in Human-AI Agent Interaction".
 */
@SpringBootApplication
public class TrustCalibrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrustCalibrationApplication.class, args);
    }
}
