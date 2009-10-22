package org.gradle.api.testing.detection;

/**
 * @author Tom Eyckmans
 */
public interface TestDetectionOrchestrator {
    void startDetection();

    void stopDetection();

    void waitForDetectionEnd();
}
