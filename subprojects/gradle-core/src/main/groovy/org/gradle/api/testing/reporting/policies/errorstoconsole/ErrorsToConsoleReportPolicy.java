package org.gradle.api.testing.reporting.policies.errorstoconsole;

import org.gradle.api.testing.reporting.policies.*;

/**
 * @author Tom Eyckmans
 */
public class ErrorsToConsoleReportPolicy implements ReportPolicy {
    public ReportPolicyName getName() {
        return ReportPolicyNames.ERRORS_TO_CONSOLE;
    }

    public ReportPolicyConfig createReportPolicyConfigInstance() {
        return new ReportPolicyConfig(getName());
    }

    public ReportPolicyInstance createReportPolicyInstance() {
        return new ErrorsToConsoleReportPolicyInstance();
    }
}
