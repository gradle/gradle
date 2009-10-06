package org.gradle.api.testing.detection;

/**
 * @author Tom Eyckmans
 */
public interface TestClassReceiver {
    void receiveTestClass(final String testClassName);
}
