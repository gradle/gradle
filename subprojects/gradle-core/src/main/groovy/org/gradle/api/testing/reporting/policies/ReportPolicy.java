package org.gradle.api.testing.reporting.policies;

/**
 * @author Tom Eyckmans
 */
public interface ReportPolicy {
    ReportPolicyName getName();

    ReportPolicyConfig createReportPolicyConfigInstance();

    ReportPolicyInstance createReportPolicyInstance();
}
