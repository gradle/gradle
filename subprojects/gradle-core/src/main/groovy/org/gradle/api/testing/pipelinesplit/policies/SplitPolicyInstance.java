package org.gradle.api.testing.pipelinesplit.policies;

/**
 * @author Tom Eyckmans
 */
public interface SplitPolicyInstance {
    void prepare();

    SplitPolicyMatcher createSplitPolicyMatcher();

}
