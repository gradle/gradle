package org.gradle.api.testing.detection;

/**
 * @author Tom Eyckmans
 */
public interface TestDetectionOrchestratorFactory {
    TestDetectionRunner createDetectionRunner();

    Thread createDetectionThread(TestDetectionRunner detectionRunner);
}
