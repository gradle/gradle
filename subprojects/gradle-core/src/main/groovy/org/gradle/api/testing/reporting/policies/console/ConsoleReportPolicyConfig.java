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
    private int amountOfReportingThreads = 10;

    public ConsoleReportPolicyConfig(ReportPolicyName policyName) {
        super(policyName);
        toShowStates = new ArrayList<TestMethodProcessResultState>();
    }

    public void addShowStates(TestMethodProcessResultState... states) {
        if (states != null && states.length != 0) {
            for (final TestMethodProcessResultState state : states) {
                addShowState(state);
            }
        }
    }

    public void addShowState(TestMethodProcessResultState state) {
        if (state == null) {
            throw new IllegalArgumentException("state == null!");
        }
        if (toShowStates.contains(state)) {
            throw new IllegalArgumentException("state already added!");
        }

        toShowStates.add(state);
    }

    public List<TestMethodProcessResultState> getToShowStates() {
        return toShowStates;
    }

    public int getAmountOfReportingThreads() {
        return amountOfReportingThreads;
    }

    public void setAmountOfReportingThreads(int amountOfReportingThreads) {
        if (amountOfReportingThreads < 1) {
            throw new IllegalArgumentException("amountOfReportingThreads can't be less then 1");
        }

        this.amountOfReportingThreads = amountOfReportingThreads;
    }
}
