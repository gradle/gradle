/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

import static org.gradle.api.tasks.testing.TestResult.ResultType;

public abstract class CompositeTestResults extends TestResultModel {
    private final CompositeTestResults parent;
    private int tests;
    private final Set<TestResult> failures = new TreeSet<TestResult>();
    private long duration;

    protected CompositeTestResults(CompositeTestResults parent) {
        this.parent = parent;
    }

    public int getTestCount() {
        return tests;
    }

    public int getFailureCount() {
        return failures.size();
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public String getFormattedDuration() {
        return getTestCount() == 0 ? "-" : super.getFormattedDuration();
    }

    public Set<TestResult> getFailures() {
        return failures;
    }

    public ResultType getResultType() {
        return failures.isEmpty() ? ResultType.SUCCESS : ResultType.FAILURE;
    }

    public String getFormattedSuccessRate() {
        Number successRate = getSuccessRate();
        if (successRate == null) {
            return "-";
        }
        return successRate + "%";
    }

    public Number getSuccessRate() {
        if (getTestCount() == 0) {
            return null;
        }

        BigDecimal tests = BigDecimal.valueOf(getTestCount());
        BigDecimal successful = BigDecimal.valueOf(getTestCount() - getFailureCount());

        return successful.divide(tests, 2, BigDecimal.ROUND_DOWN).multiply(BigDecimal.valueOf(100)).intValue();
    }

    protected void failed(TestResult failedTest) {
        failures.add(failedTest);
        if (parent != null) {
            parent.failed(failedTest);
        }
    }

    protected TestResult addTest(TestResult test) {
        tests++;
        duration += test.getDuration();
        return test;
    }
}
