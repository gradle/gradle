/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.fabric.*;
import org.gradle.api.testing.reporting.ReportInfo;
import org.gradle.api.testing.reporting.TestClassProcessResultReportInfo;
import org.gradle.api.testing.reporting.policies.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class ConsoleReport implements Report {

    private static Logger logger = LoggerFactory.getLogger(ConsoleReport.class);

    private final Map<TestMethodProcessResultState, TestMethodProcessResultState> methodProcessResultStateMapping;
    private final ConsoleReportPolicy config;

    public ConsoleReport(TestFrameworkInstance testFrameworkInstance, ConsoleReportPolicy config) {
        this.config = config;
        methodProcessResultStateMapping = testFrameworkInstance.getTestFramework().getMethodProcessResultStateMapping();
    }

    public void start() {
    }

    public void stop() {
    }

    public void addReportInfo(ReportInfo reportInfo) {
        if (reportInfo instanceof TestClassProcessResultReportInfo) {
            final TestClassProcessResultReportInfo testClassInfo = (TestClassProcessResultReportInfo) reportInfo;

            final Pipeline pipeline = testClassInfo.getPipeline();
            final TestClassProcessResult classResult = testClassInfo.getTestClassProcessResult();
            final List<TestMethodProcessResult> methodResults = classResult.getMethodResults();

            final Map<TestMethodProcessResultState, Integer> stateCounts
                    = new HashMap<TestMethodProcessResultState, Integer>();

            for (final TestMethodProcessResult methodResult : methodResults) {
                final TestMethodProcessResultState mappedState = methodProcessResultStateMapping.get(
                        methodResult.getState());

                Integer stateCount = stateCounts.get(mappedState);
                if (stateCount == null) {
                    stateCount = 1;
                } else {
                    stateCount++;
                }
                stateCounts.put(mappedState, stateCount);
            }

            final List<TestMethodProcessResultState> noneZeroStates = new ArrayList<TestMethodProcessResultState>();

            for (final TestMethodProcessResultState state : TestMethodProcessResultStates.values()) {
                final Integer stateCount = stateCounts.get(state);
                if (stateCount != null) {
                    noneZeroStates.add(state);
                }
            }

            boolean show = false;
            final List<TestMethodProcessResultState> toShowStates = config.getToShowStates();
            final Iterator<TestMethodProcessResultState> toShowStatesIterator = toShowStates.iterator();
            while (!show && toShowStatesIterator.hasNext()) {
                show = noneZeroStates.contains(toShowStatesIterator.next());
            }

            if (show) {
                final int failureCount = stateCounts.get(TestMethodProcessResultStates.FAILURE) == null ? 0
                        : stateCounts.get(TestMethodProcessResultStates.FAILURE);
                final int errorCount = stateCounts.get(TestMethodProcessResultStates.ERROR) == null ? 0
                        : stateCounts.get(TestMethodProcessResultStates.ERROR);
                final int successCount = stateCounts.get(TestMethodProcessResultStates.SUCCESS) == null ? 0
                        : stateCounts.get(TestMethodProcessResultStates.SUCCESS);

                logger.info("pipeline {}, fork {} : Test {} : success#{}, failure#{}, error#{}", new Object[]{
                        pipeline.getName(), reportInfo.getForkId(),
                        classResult.getTestClassRunInfo().getTestClassName(), successCount, failureCount, errorCount
                });

                if (failureCount > 0 || errorCount > 0) {
                    for (final TestMethodProcessResult methodResult : methodResults) {
                        if (methodResult.getState() == TestMethodProcessResultStates.ERROR
                                || methodResult.getState() == TestMethodProcessResultStates.FAILURE) {
                            logger.info("pipeline {}, fork {} : Test {} :", new Object[]{
                                    pipeline.getName(), reportInfo.getForkId(),
                                    classResult.getTestClassRunInfo().getTestClassName() + "." + methodResult
                                            .getMethodName()
                            });
                            logger.info("cause", methodResult.getThrownException());
                        }
                    }
                }
            }
        }
        // else unsupported reportInfo -> warning ?
    }
}
