/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import com.google.common.base.Joiner;
import org.gradle.api.internal.tasks.testing.DecoratingTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class TestCountLogger implements TestListener {
    private final ProgressLoggerFactory factory;
    private ProgressLogger progressLogger;
    private final Logger logger;

    private final AggregatedTestResult aggregatedTestResult = new AggregatedTestResult();
    private final Map<String, String> workerTestClassNames = new LinkedHashMap<String, String>();
    private boolean hadFailures;

    public TestCountLogger(ProgressLoggerFactory factory) {
        this(factory, LoggerFactory.getLogger(TestCountLogger.class));
    }

    TestCountLogger(ProgressLoggerFactory factory, Logger logger) {
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
        calculateWorkerTestResult(testDescriptor);
        progressLogger.progress(summary());
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        calculateAggregatedTestResult(result);
        progressLogger.progress(summary());
    }

    private void calculateAggregatedTestResult(TestResult result) {
        aggregatedTestResult.addTotalTests(result.getTestCount());
        aggregatedTestResult.addFailedTests(result.getFailedTestCount());
        aggregatedTestResult.addSkippedTests(result.getSkippedTestCount());
    }

    private void calculateWorkerTestResult(TestDescriptor testDescriptor) {
        TestDescriptor workerDescriptor = findWorkerTestSuiteDescriptorParent(testDescriptor);

        if (workerDescriptor != null && !workerTestClassNames.containsKey(workerDescriptor.getName())) {
            TestDescriptor defaultDescriptor = findDefaultDescriptorParent(testDescriptor);
            String fullyQualifiedTestClassName = defaultDescriptor.getClassName();
            String testClassName = fullyQualifiedTestClassName.substring(fullyQualifiedTestClassName.lastIndexOf('.') + 1);
            workerTestClassNames.put(workerDescriptor.getName(), testClassName);
        }
    }

    private TestDescriptor findWorkerTestSuiteDescriptorParent(TestDescriptor testDescriptor) {
        return findDescriptorParent(testDescriptor, WorkerTestClassProcessor.WorkerTestSuiteDescriptor.class);
    }

    private TestDescriptor findDefaultDescriptorParent(TestDescriptor testDescriptor) {
        return findDescriptorParent(testDescriptor, DefaultTestDescriptor.class);
    }

    private TestDescriptor findDescriptorParent(TestDescriptor testDescriptor, Class<? extends TestDescriptor> descriptorClass) {
        if (testDescriptor == null) {
            return null;
        }

        if (testDescriptor instanceof DecoratingTestDescriptor) {
            DecoratingTestDescriptor decoratingTestDescriptor = (DecoratingTestDescriptor)testDescriptor;
            TestDescriptorInternal actualTestDescriptor = decoratingTestDescriptor.getDescriptor();

            if (descriptorClass.isInstance(actualTestDescriptor)) {
                return actualTestDescriptor;
            }
        }

        return findDescriptorParent(testDescriptor.getParent(), descriptorClass);
    }

    private String summary() {
        StringBuilder summary = new StringBuilder();
        summary.append(aggregatedTestResult.toString());
        int workerCount = workerTestClassNames.size();

        if (workerCount > 0) {
            appendWorkerInfo(summary, workerCount);
        }

        return summary.toString();
    }

    private void appendWorkerInfo(StringBuilder builder, int workerCount) {
        builder.append(" > (");
        builder.append(workerCount);
        builder.append(" worker");

        if (workerCount > 1) {
            builder.append("s");
        }

        builder.append(") ");
        builder.append(joinTestClassNames());
    }

    private String joinTestClassNames() {
       return Joiner.on(", ").join(workerTestClassNames.values());
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        if (suite.getParent() == null) {
            progressLogger = factory.newOperation(TestCountLogger.class);
            progressLogger.setDescription("Run tests");
            progressLogger.started();
            progressLogger.progress(summary());
        }
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null) {
            if (aggregatedTestResult.getFailedTests() > 0) {
                logger.error(TextUtil.getPlatformLineSeparator() + summary());
            }
            progressLogger.completed();

            if (result.getResultType() == org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE) {
                hadFailures = true;
            }

            workerTestClassNames.clear();
        } else {
            TestDescriptor workerDescriptor = findWorkerTestSuiteDescriptorParent(suite);

            if (workerDescriptor != null) {
                workerTestClassNames.remove(workerDescriptor.getName());
            }
        }
    }

    public boolean hadFailures() {
        return hadFailures;
    }

    private class AggregatedTestResult {
        private long totalTests;
        private long failedTests;
        private long skippedTests;

        public void addTotalTests(long totalTests) {
            this.totalTests += totalTests;
        }

        public long getFailedTests() {
            return failedTests;
        }

        public void addFailedTests(long failedTests) {
            this.failedTests += failedTests;
        }

        public void addSkippedTests(long skippedTests) {
            this.skippedTests += skippedTests;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();

            append(builder, totalTests, "test");
            builder.append(" completed");
            if (failedTests > 0) {
                builder.append(", ");
                builder.append(failedTests);
                builder.append(" failed");
            }
            if (skippedTests > 0) {
                builder.append(", ");
                builder.append(skippedTests);
                builder.append(" skipped");
            }

            return builder.toString();
        }

        private void append(StringBuilder dest, long count, String noun) {
            dest.append(count);
            dest.append(" ");
            dest.append(noun);
            if (count != 1) {
                dest.append("s");
            }
        }
    }
}
