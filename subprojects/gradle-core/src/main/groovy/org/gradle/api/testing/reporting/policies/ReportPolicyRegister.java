/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
