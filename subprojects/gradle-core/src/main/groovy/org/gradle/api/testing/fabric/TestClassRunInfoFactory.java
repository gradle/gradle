package org.gradle.api.testing.fabric;

/**
 * @author Tom Eyckmans
 */
public interface TestClassRunInfoFactory {
    TestClassRunInfo createTestClassRunInfo(String testClassName);
}
