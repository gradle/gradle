package org.gradle.api.testing.pipelinesplit.policies;

/**
 * @author Tom Eyckmans
 */
public class SplitPolicyConfig {

    private final SplitPolicyName policyName;

    public SplitPolicyConfig(SplitPolicyName policyName) {
        if (policyName == null) throw new IllegalArgumentException("policyName == null!");

        this.policyName = policyName;
    }

    public SplitPolicyName getPolicyName() {
        return policyName;
    }
}
