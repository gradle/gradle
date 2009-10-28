package org.gradle.api.testing.reporting.policies;

/**
 * @author Tom Eyckmans
 */
public class ReportPolicyConfig {
    private ReportPolicyName policyName;

    public ReportPolicyConfig(ReportPolicyName policyName) {
        if ( policyName == null ) throw new IllegalArgumentException("policyName is null!");

        this.policyName = policyName;
    }

    public ReportPolicyName getPolicyName() {
        return policyName;
    }
}
