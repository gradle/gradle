package org.gradle.api.testing.reporting.policies.console;

import org.gradle.api.testing.reporting.policies.ReportPolicyConfig;
import org.gradle.api.testing.reporting.policies.ReportPolicyName;
import org.gradle.api.testing.fabric.TestMethodProcessResultState;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Tom Eyckmans
 */
public class ConsoleReportPolicyConfig extends ReportPolicyConfig {

    private final List<TestMethodProcessResultState> toShowStates;

    public ConsoleReportPolicyConfig(ReportPolicyName policyName) {
        super(policyName);
        toShowStates = new ArrayList<TestMethodProcessResultState>();
    }

    public void addShowStates(TestMethodProcessResultState ... states) {
        if ( states != null && states.length != 0 ) {
            for (final TestMethodProcessResultState state : states) {
                addShowState(state);
            }
        }
    }

    public void addShowState(TestMethodProcessResultState state) {
        if ( state == null ) throw new IllegalArgumentException("state == null!");
        if ( toShowStates.contains(state) ) throw new IllegalArgumentException("state already added!");
        
        toShowStates.add(state);
    }

    public List<TestMethodProcessResultState> getToShowStates() {
        return toShowStates;
    }
}
