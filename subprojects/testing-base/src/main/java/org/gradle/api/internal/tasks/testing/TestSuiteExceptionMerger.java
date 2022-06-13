/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import com.google.common.base.Joiner;
import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.ExceptionMerger;
import org.gradle.internal.exceptions.StyledException;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestSuiteTarget;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This class exists to consolidate multiple {@link TestSuiteVerificationException}s into a single {@link MergedTestSuiteFailuresException}
 * containing all the individual failures in order to provide a summarized failure message.
 *
 * @since 7.6
 */
@Incubating
public final class TestSuiteExceptionMerger implements ExceptionMerger<TestSuiteVerificationException, MergedTestSuiteFailuresException> {
    private final List<TestSuiteVerificationException> exceptions = new ArrayList<TestSuiteVerificationException>();

    @Override
    public void merge(TestSuiteVerificationException exception) {
        exceptions.add(exception);
    }

    @Override
    public MergedTestSuiteFailuresException getMergedException() {
        String failureOutput = buildFailureOutput();
        return new MergedTestSuiteFailuresException(failureOutput, exceptions);
    }

    @Override
    public Class<TestSuiteVerificationException> getMergeableExceptionType() {
        return TestSuiteVerificationException.class;
    }

    @Override
    public Class<MergedTestSuiteFailuresException> getMergedExceptionType() {
        return MergedTestSuiteFailuresException.class;
    }

    private LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>> assembleFailuresBySortedSuite() {
        LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>> result = new LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>>();
        for (TestSuiteVerificationException exception: exceptions) {
            TestSuiteTarget target  = exception.getTestSuiteTarget();
            if (!result.containsKey(target.getTestSuite())) {
                result.put(target.getTestSuite(), new HashMap<TestSuiteTarget, TestSuiteVerificationException>());
            }
            result.get(target.getTestSuite()).put(target, exception);
        }
        return result;
    }

    private List<String> assembleSortedAllFailedTasks(LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>> failuresBySortedSuite) {
        List<String> allFailedTasks = new ArrayList<String>();
        for (Map<TestSuiteTarget, TestSuiteVerificationException> failures: failuresBySortedSuite.values()) {
            for (TestSuiteVerificationException failure: failures.values()) {
                allFailedTasks.add(failure.getTestSuiteTarget().getTestTask().get().getPath());
            }
        }
        Collections.sort(allFailedTasks);
        return allFailedTasks;
    }

    private String buildFailureOutput() {
        LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>> failuresBySortedSuite = assembleFailuresBySortedSuite();
        List<String> sortedAllFailedTasks = assembleSortedAllFailedTasks(failuresBySortedSuite);

        final StringBuilder failureOutput = new StringBuilder(buildExecutionFailedHeader(failuresBySortedSuite, sortedAllFailedTasks));
        for (TestSuite testSuite : failuresBySortedSuite.keySet()) {
            failureOutput.append(buildSuiteFailureDetails(testSuite, failuresBySortedSuite.get(testSuite)));
        }

        return failureOutput.toString();
    }

    private String buildExecutionFailedHeader(LinkedHashMap<TestSuite, Map<TestSuiteTarget, TestSuiteVerificationException>> sortedFailuresBySuite,
                                              List<String> sortedAllFailedTasks) {
        StringBuilder failureOutput = new StringBuilder("Execution failed for task");
        failureOutput.append(sortedAllFailedTasks.size() > 1 ? "s '" : " '");
        Joiner.on("', '").appendTo(failureOutput, sortedAllFailedTasks);
        failureOutput.append("'.");
        return failureOutput.toString();
    }

    private String buildSuiteFailureDetails(TestSuite testSuite, Map<TestSuiteTarget, TestSuiteVerificationException> failures) {
        final StringBuilder failureOutput = new StringBuilder();
        failureOutput.append('\n');
        failureOutput.append(StyledException.style(StyledTextOutput.Style.Info, "> "));
        failureOutput.append("Test suite '").append(testSuite.getName()).append("' has failing tests.");

        for (TestSuiteTarget target: testSuite.getTargets()) {
            failureOutput.append('\n');
            failureOutput.append(StyledException.style(StyledTextOutput.Style.Info, "> "));
            failureOutput.append("\t").append("'").append(target.getName()).append("' target ");

            if (target.getTestTask().get().getDidWork()) {
                boolean targetFailed = targetFailed(target, failures);
                if (targetFailed) {
                    failureOutput.append("FAILED: ");
                } else {
                    failureOutput.append("PASSED: ");
                }

                failureOutput.append(target.getFormattedSummary()).append(".");

                if (targetFailed) {
                    String reportUrl = getReportUrl(target, failures);
                    if (reportUrl != null) {
                        failureOutput.append(StyledException.style(StyledTextOutput.Style.Normal, " See report at: " + reportUrl));
                    }
                }
            } else {
                failureOutput.append("did not start");
            }
        }

        return failureOutput.toString();
    }

    @Nullable
    private String getReportUrl(TestSuiteTarget target, Map<TestSuiteTarget, TestSuiteVerificationException> failures) {
        return failures.get(target).getReportUrl();
    }

    private boolean targetFailed(TestSuiteTarget target, Map<TestSuiteTarget, TestSuiteVerificationException> failures) {
        return failures.containsKey(target);
    }
}
