package org.gradle.api.testing.reporting.policies;

import org.gradle.api.testing.reporting.policies.errorstoconsole.ErrorsToConsoleReportPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tom Eyckmans
 */
public class ReportPolicyRegister {
    private static final Map<ReportPolicyName, ReportPolicy> reportPolicies = new ConcurrentHashMap<ReportPolicyName, ReportPolicy>();

    static {
        registerReportPolicy(new ErrorsToConsoleReportPolicy());
    }

    public static void registerReportPolicy(final ReportPolicy reportPolicy) {
        if ( reportPolicy == null ) throw new IllegalArgumentException("reportPolicy == null!");

        final ReportPolicyName reportPolicyName = reportPolicy.getName();

        if ( reportPolicyName == null ) throw new IllegalArgumentException("reportPolicy.name == null!");
        if ( reportPolicies.containsKey(reportPolicyName) )
            throw new IllegalArgumentException("report policy (" + reportPolicyName + ") already registered!");

        reportPolicies.put(reportPolicyName, reportPolicy);
    }

    public static ReportPolicy getReportPolicy(final ReportPolicyName reportPolicyName) {
        if ( reportPolicyName == null ) throw new IllegalArgumentException("reportPolicyName == null!");

        return reportPolicies.get(reportPolicyName);
    }
}
