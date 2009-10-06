package org.gradle.api.testing.pipelinesplit.policies;

import org.gradle.api.testing.fabric.TestClassRunInfo;

/**
 * @author Tom Eyckmans
 */
public interface SplitPolicyMatcher {
    boolean match(TestClassRunInfo testClassRunInfo);
}
