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

package org.gradle.api.internal.tasks.testing.junit.result;

import com.google.common.collect.Iterables;
import org.apache.tools.ant.util.DateUtils;
import org.gradle.api.internal.tasks.testing.report.ClassTestResults;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.xml.SimpleXmlWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JUnitXmlResultWriter {

    private static String getXmlTestSuiteName(ClassTestResults result) {
        // both JUnit Jupiter and Vintage use the simple class name as the default display name
        // so we use this as a heuristic to determine whether the display name was customized
        if (result.getName().endsWith("." + result.getDisplayName()) || result.getName().endsWith("$" + result.getDisplayName())) {
            return result.getName();
        } else {
            return result.getDisplayName();
        }
    }

    private final String hostName;
    private final JUnitXmlResultOptions options;

    public JUnitXmlResultWriter(String hostName, JUnitXmlResultOptions options) {
        this.hostName = hostName;
        this.options = options;
    }

    public void write(ClassTestResults results, OutputStream output) {
        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output, "  ");
            long earliestStartTime = results.getProviders().stream().mapToLong(provider -> provider.getResult().getStartTime()).min()
                .orElseThrow(() -> new IllegalStateException("No test results"));
            long latestEndTime = results.getProviders().stream().mapToLong(provider -> provider.getResult().getEndTime()).max()
                .orElseThrow(() -> new IllegalStateException("No test results"));
            writer.startElement("testsuite")
                .attribute("name", getXmlTestSuiteName(results))

                // NOTE: these totals are unaffected by “merge reruns” with Surefire, so we do the same
                .attribute("tests", String.valueOf(results.getTestCount()))
                .attribute("skipped", String.valueOf(results.getIgnoredCount()))
                .attribute("failures", String.valueOf(results.getFailureCount()))
                .attribute("errors", "0")

                .attribute("timestamp", DateUtils.format(earliestStartTime, DateUtils.ISO8601_DATETIME_PATTERN))
                .attribute("hostname", hostName)
                .attribute("time", String.valueOf((latestEndTime - earliestStartTime) / 1000.0));

            writer.startElement("properties");
            writer.endElement();

            String className = results.getName();

            if (options.mergeReruns) {
                writeTestCasesWithMergeRerunHandling(writer, results.getTestResults(), className);
            } else {
                writeTestCasesWithDiscreteRerunHandling(writer, results.getTestResults(), className);
            }

            if (options.includeSystemOutLog) {
                writer.startElement("system-out");
                writeOutputs(writer, results, !options.outputPerTestCase, TestOutputEvent.Destination.StdOut);
                writer.endElement();
            }

            if (options.includeSystemErrLog) {
                writer.startElement("system-err");
                writeOutputs(writer, results, !options.outputPerTestCase, TestOutputEvent.Destination.StdErr);
                writer.endElement();
            }

            writer.endElement();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeOutputs(SimpleXmlWriter writer, ClassTestResults results, boolean allClassOutput, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        if (allClassOutput) {
            results.getProviders().forEach(provider -> provider.copyAllOutput(destination, writer));
        } else {
            results.getProviders().forEach(provider -> provider.copyOutput(destination, writer));
        }
        writer.endCDATA();
    }

    /**
     * Output in the format the Surefire uses when enabling retries.
     *
     * - https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html
     * - https://github.com/apache/maven-surefire/blob/edb3b71b95db98eef6a8e4fa98d376fd3512b05a/maven-surefire-plugin/src/site/resources/xsd/surefire-test-report-3.0.xsd
     * - https://plugins.jenkins.io/flaky-test-handler/
     *
     * We deviate from Maven's behaviour when there are any skipped executions, or when there are multiple successful executions.
     * The “standard” does not specify the behaviour in this circumstance.
     *
     * There's no way to convey multiple successful “executions” of a test case in the XML structure.
     * If this happens, Maven just omits any information about successful executions if they were not the last.
     * We break the executions up into multiple testcases, which each successful execution being the last execution
     * of the test case, so as to not drop information.
     */
    private void writeTestCasesWithMergeRerunHandling(SimpleXmlWriter writer, final List<org.gradle.api.internal.tasks.testing.report.TestResult> methodResults, final String className) throws IOException {
        List<List<org.gradle.api.internal.tasks.testing.report.TestResult>> groupedExecutions = groupExecutions(methodResults);

        for (final List<org.gradle.api.internal.tasks.testing.report.TestResult> groupedExecution : groupedExecutions) {
            if (groupedExecution.size() == 1) {
                writeTestCase(writer, discreteTestCase(className, groupedExecution.get(0)));
            } else {
                final org.gradle.api.internal.tasks.testing.report.TestResult firstExecution = groupedExecution.get(0);
                final org.gradle.api.internal.tasks.testing.report.TestResult lastExecution = groupedExecution.get(groupedExecution.size() - 1);
                final boolean allFailed = lastExecution.getResultType() == TestResult.ResultType.FAILURE;

                // https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html
                // (if) The test passes in one of its re-runs […] the running time of a flaky test will be the running time of the last successful run.
                // (if) The test fails in all of the re-runs […] the running time of a failing test with re-runs will be the running time of the first failing run.
                long duration = allFailed ? firstExecution.getDuration() : lastExecution.getDuration();

                writeTestCase(writer, new TestCase(
                    firstExecution.getDisplayName(),
                    className,
                    duration,
                    mergeRerunExecutions(allFailed, groupedExecution, firstExecution)
                ));
            }
        }
    }

    private Iterable<TestCaseExecution> mergeRerunExecutions(final boolean allFailed, final List<org.gradle.api.internal.tasks.testing.report.TestResult> groupedExecution, final org.gradle.api.internal.tasks.testing.report.TestResult firstExecution) {
        return Iterables.concat(Iterables.transform(groupedExecution, execution -> {
            TestResult.ResultType resultType = execution.getResultType();
            switch (resultType) {
                case SUCCESS:
                    return Collections.singleton(success(execution));
                case SKIPPED:
                    return Collections.singleton(skipped(execution));
                case FAILURE:
                    return failures(execution, allFailed
                        ? execution == firstExecution ? FailureType.FAILURE : FailureType.RERUN_FAILURE
                        : FailureType.FLAKY_FAILURE
                    );
                default:
                    throw new IllegalStateException("unhandled type: " + resultType);
            }
        }));
    }

    /**
     * Group the method results into sequences of executions, where each group shares the same display name.
     *
     * Each group can only have one successful execution, which must be the last.
     * On each successful execution, a new group is started.
     *
     * The methodResults are assumed to be in execution order.
     */
    private List<List<org.gradle.api.internal.tasks.testing.report.TestResult>> groupExecutions(List<org.gradle.api.internal.tasks.testing.report.TestResult> methodResults) {
        List<List<org.gradle.api.internal.tasks.testing.report.TestResult>> groupedExecutions = new ArrayList<>();
        Map<String, Integer> latestGroupForName = new HashMap<String, Integer>();

        for (org.gradle.api.internal.tasks.testing.report.TestResult methodResult : methodResults) {
            String name = methodResult.getDisplayName();
            Integer index = latestGroupForName.get(name);
            if (index == null) {
                List<org.gradle.api.internal.tasks.testing.report.TestResult> executions = Collections.singletonList(methodResult);
                groupedExecutions.add(executions);
                if (methodResult.getResultType() == TestResult.ResultType.FAILURE) {
                    latestGroupForName.put(name, groupedExecutions.size() - 1);
                }
            } else {
                List<org.gradle.api.internal.tasks.testing.report.TestResult> executions = groupedExecutions.get(index);
                if (executions.size() == 1) {
                    executions = new ArrayList<>(executions);
                    groupedExecutions.set(index, executions);
                }
                executions.add(methodResult);
                if (methodResult.getResultType() != TestResult.ResultType.FAILURE) {
                    latestGroupForName.remove(name);
                }
            }
        }

        return groupedExecutions;
    }

    private void writeTestCasesWithDiscreteRerunHandling(SimpleXmlWriter writer, final Collection<org.gradle.api.internal.tasks.testing.report.TestResult> methodResults, final String className) throws IOException {
        for (org.gradle.api.internal.tasks.testing.report.TestResult methodResult : methodResults) {
            writeTestCase(writer, discreteTestCase(className, methodResult));
        }
    }

    private TestCase discreteTestCase(String className, org.gradle.api.internal.tasks.testing.report.TestResult methodResult) {
        return new TestCase(methodResult.getDisplayName(), className, methodResult.getDuration(), discreteTestCaseExecutions(methodResult));
    }

    private Iterable<? extends TestCaseExecution> discreteTestCaseExecutions(final org.gradle.api.internal.tasks.testing.report.TestResult methodResult) {
        TestResult.ResultType resultType = methodResult.getResultType();
        switch (resultType) {
            case FAILURE:
                return failures(methodResult, FailureType.FAILURE);
            case SKIPPED:
                return Collections.singleton(skipped(methodResult));
            case SUCCESS:
                return Collections.singleton(success(methodResult));
            default:
                throw new IllegalStateException("Unexpected result type: " + resultType);
        }
    }


    private void writeTestCase(SimpleXmlWriter writer, TestCase testCase) throws IOException {
        writer.startElement("testcase")
            .attribute("name", testCase.name)
            .attribute("classname", testCase.className)
            .attribute("time", String.valueOf(testCase.duration / 1000.0));

        for (TestCaseExecution execution : testCase.executions) {
            execution.write(writer);
        }

        writer.endElement();
    }

    abstract static class TestCaseExecution {
        @Nullable
        private final TestResultsProvider outputProvider;
        private final JUnitXmlResultOptions options;

        TestCaseExecution(@Nullable TestResultsProvider outputProvider, JUnitXmlResultOptions options) {
            this.outputProvider = outputProvider;
            this.options = options;
        }

        abstract void write(SimpleXmlWriter writer) throws IOException;

        protected void writeOutput(SimpleXmlWriter writer) throws IOException {
            if (outputProvider == null) {
                return;
            }
            if (options.includeSystemOutLog && outputProvider.hasOutput(TestOutputEvent.Destination.StdOut)) {
                writer.startElement("system-out");
                writer.startCDATA();
                outputProvider.copyOutput(TestOutputEvent.Destination.StdOut, writer);
                writer.endCDATA();
                writer.endElement();
            }

            if (options.includeSystemErrLog && outputProvider.hasOutput(TestOutputEvent.Destination.StdErr)) {
                writer.startElement("system-err");
                writer.startCDATA();
                outputProvider.copyOutput(TestOutputEvent.Destination.StdErr, writer);
                writer.endCDATA();
                writer.endElement();
            }
        }
    }

    private static class TestCase {
        final String name;
        final String className;
        final long duration;
        final Iterable<? extends TestCaseExecution> executions;

        TestCase(String name, String className, long duration, Iterable<? extends TestCaseExecution> executions) {
            this.name = name;
            this.className = className;
            this.duration = duration;
            this.executions = executions;
        }
    }

    private static class TestCaseExecutionSuccess extends TestCaseExecution {
        TestCaseExecutionSuccess(TestResultsProvider outputProvider, JUnitXmlResultOptions options) {
            super(outputProvider, options);
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            writeOutput(writer);
        }
    }


    private static class TestCaseExecutionSkipped extends TestCaseExecution {
        TestCaseExecutionSkipped(TestResultsProvider outputProvider, JUnitXmlResultOptions options) {
            super(outputProvider, options);
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            writer.startElement("skipped").endElement();
            writeOutput(writer);
        }
    }

    enum FailureType {
        FAILURE("failure", false),
        FLAKY_FAILURE("flakyFailure", true),
        RERUN_FAILURE("rerunFailure", true);

        private final String elementName;
        private final boolean useStacktraceElementAndNestedOutput;

        FailureType(String elementName, boolean useStacktraceElementAndNestedOutput) {
            this.elementName = elementName;
            this.useStacktraceElementAndNestedOutput = useStacktraceElementAndNestedOutput;
        }
    }

    private static class TestCaseExecutionFailure extends TestCaseExecution {
        private final PersistentTestFailure failure;
        private final FailureType type;

        TestCaseExecutionFailure(TestResultsProvider outputProvider, JUnitXmlResultOptions options, FailureType type, PersistentTestFailure failure) {
            super(outputProvider, options);
            this.failure = failure;
            this.type = type;
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            writer.startElement(type.elementName)
                .attribute("message", failure.getMessage())
                .attribute("type", failure.getExceptionType());

            if (type.useStacktraceElementAndNestedOutput) {
                writer.startElement("stackTrace")
                    .characters(failure.getStackTrace())
                    .endElement();
                writeOutput(writer);
                writer.endElement();
            } else {
                writer.characters(failure.getStackTrace());
                writer.endElement();
                writeOutput(writer);
            }
        }
    }

    private TestCaseExecution success(org.gradle.api.internal.tasks.testing.report.TestResult methodResult) {
        return new TestCaseExecutionSuccess(includeOutputIfNeeded(methodResult.getProvider()), options);
    }

    private TestCaseExecution skipped(org.gradle.api.internal.tasks.testing.report.TestResult methodResult) {
        return new TestCaseExecutionSkipped(includeOutputIfNeeded(methodResult.getProvider()), options);
    }

    private Iterable<TestCaseExecution> failures(org.gradle.api.internal.tasks.testing.report.TestResult methodResult, final FailureType failureType) {
        List<PersistentTestFailure> failures = methodResult.getFailures();
        if (failures.isEmpty()) {
            // This can happen with a failing engine. For now, we just ignore this.
            return Collections.emptyList();
        }
        final PersistentTestFailure firstFailure = failures.get(0);
        return Iterables.transform(failures, failure -> {
            boolean isFirst = failure == firstFailure;
            TestResultsProvider outputProvider = isFirst ? includeOutputIfNeeded(methodResult.getProvider()) : null;
            return new TestCaseExecutionFailure(outputProvider, options, failureType, failure);
        });
    }

    private TestResultsProvider includeOutputIfNeeded(TestResultsProvider methodProvider) {
        if (options.outputPerTestCase) {
            return methodProvider;
        }
        return null;
    }

}
