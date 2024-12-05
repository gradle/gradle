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
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.xml.SimpleXmlWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JUnitXmlResultWriter {

    private static String getXmlTestSuiteName(PersistentTestResult result) {
        // both JUnit Jupiter and Vintage use the simple class name as the default display name
        // so we use this as a heuristic to determine whether the display name was customized
        if (result.getName().endsWith("." + result.getDisplayName()) || result.getName().endsWith("$" + result.getDisplayName())) {
            return result.getName();
        } else {
            return result.getDisplayName();
        }
    }

    private static final class TestCollector extends TestVisitingResultsProviderAction {
        private final List<TestResultsProvider> providers = new ArrayList<>();
        private int skippedCount;
        private int failuresCount;

        @Override
        protected void visitTest(TestResultsProvider provider) {
            providers.add(provider);
            TestResult.ResultType resultType = provider.getResult().getResultType();
            switch (resultType) {
                case SUCCESS:
                    break;
                case SKIPPED:
                    skippedCount++;
                    break;
                case FAILURE:
                    failuresCount++;
                    break;
                default:
                    throw new IllegalStateException("Unexpected result type: " + resultType);
            }
        }
    }

    private final String hostName;
    private final JUnitXmlResultOptions options;

    public JUnitXmlResultWriter(String hostName, JUnitXmlResultOptions options) {
        this.hostName = hostName;
        this.options = options;
    }

    public void write(TestResultsProvider provider, OutputStream output) {
        TestCollector tests = new TestCollector();
        provider.visitChildren(tests);
        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output, "  ");
            writer.startElement("testsuite")
                .attribute("name", getXmlTestSuiteName(provider.getResult()))

                // NOTE: these totals are unaffected by “merge reruns” with Surefire, so we do the same
                .attribute("tests", String.valueOf(tests.providers.size()))
                .attribute("skipped", String.valueOf(tests.skippedCount))
                .attribute("failures", String.valueOf(tests.failuresCount))
                .attribute("errors", "0")

                .attribute("timestamp", DateUtils.format(provider.getResult().getStartTime(), DateUtils.ISO8601_DATETIME_PATTERN))
                .attribute("hostname", hostName)
                .attribute("time", String.valueOf(provider.getResult().getDuration() / 1000.0));

            writer.startElement("properties");
            writer.endElement();

            String className = provider.getResult().getName();

            if (options.mergeReruns) {
                writeTestCasesWithMergeRerunHandling(writer, tests.providers, className);
            } else {
                writeTestCasesWithDiscreteRerunHandling(writer, tests.providers, className);
            }

            if (options.includeSystemOutLog) {
                writer.startElement("system-out");
                writeOutputs(writer, provider, !options.outputPerTestCase, TestOutputEvent.Destination.StdOut);
                writer.endElement();
            }

            if (options.includeSystemErrLog) {
                writer.startElement("system-err");
                writeOutputs(writer, provider, !options.outputPerTestCase, TestOutputEvent.Destination.StdErr);
                writer.endElement();
            }

            writer.endElement();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeOutputs(SimpleXmlWriter writer, TestResultsProvider provider, boolean allClassOutput, TestOutputEvent.Destination destination) throws IOException {
        writer.startCDATA();
        if (allClassOutput) {
            provider.copyAllOutput(destination, writer);
        } else {
            provider.copyOutput(destination, writer);
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
    private void writeTestCasesWithMergeRerunHandling(SimpleXmlWriter writer, final List<TestResultsProvider> methodResults, final String className) throws IOException {
        List<List<TestResultsProvider>> groupedExecutions = groupExecutions(methodResults);

        for (final List<TestResultsProvider> groupedExecution : groupedExecutions) {
            if (groupedExecution.size() == 1) {
                writeTestCase(writer, discreteTestCase(className, groupedExecution.get(0)));
            } else {
                final TestResultsProvider firstExecution = groupedExecution.get(0);
                final TestResultsProvider lastExecution = groupedExecution.get(groupedExecution.size() - 1);
                final boolean allFailed = lastExecution.getResult().getResultType() == TestResult.ResultType.FAILURE;

                // https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html
                // (if) The test passes in one of its re-runs […] the running time of a flaky test will be the running time of the last successful run.
                // (if) The test fails in all of the re-runs […] the running time of a failing test with re-runs will be the running time of the first failing run.
                long duration = allFailed ? firstExecution.getResult().getDuration() : lastExecution.getResult().getDuration();

                writeTestCase(writer, new TestCase(
                    firstExecution.getResult().getDisplayName(),
                    className,
                    duration,
                    mergeRerunExecutions(allFailed, groupedExecution, firstExecution)
                ));
            }
        }
    }

    private Iterable<TestCaseExecution> mergeRerunExecutions(final boolean allFailed, final List<TestResultsProvider> groupedExecution, final TestResultsProvider firstExecution) {
        return Iterables.concat(Iterables.transform(groupedExecution, execution -> {
            TestResult.ResultType resultType = execution.getResult().getResultType();
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
     * The methodProviders are assumed to be in execution order.
     */
    private List<List<TestResultsProvider>> groupExecutions(List<TestResultsProvider> methodProviders) {
        List<List<TestResultsProvider>> groupedExecutions = new ArrayList<>();
        Map<String, Integer> latestGroupForName = new HashMap<String, Integer>();

        for (TestResultsProvider methodProvider : methodProviders) {
            PersistentTestResult methodResult = methodProvider.getResult();
            String name = methodResult.getDisplayName();
            Integer index = latestGroupForName.get(name);
            if (index == null) {
                List<TestResultsProvider> executions = Collections.singletonList(methodProvider);
                groupedExecutions.add(executions);
                if (methodResult.getResultType() == TestResult.ResultType.FAILURE) {
                    latestGroupForName.put(name, groupedExecutions.size() - 1);
                }
            } else {
                List<TestResultsProvider> executions = groupedExecutions.get(index);
                if (executions.size() == 1) {
                    executions = new ArrayList<>(executions);
                    groupedExecutions.set(index, executions);
                }
                executions.add(methodProvider);
                if (methodResult.getResultType() != TestResult.ResultType.FAILURE) {
                    latestGroupForName.remove(name);
                }
            }
        }

        return groupedExecutions;
    }

    private void writeTestCasesWithDiscreteRerunHandling(SimpleXmlWriter writer, final List<TestResultsProvider> methodProviders, final String className) throws IOException {
        for (TestResultsProvider methodProvider : methodProviders) {
            writeTestCase(writer, discreteTestCase(className, methodProvider));
        }
    }

    private TestCase discreteTestCase(String className, TestResultsProvider methodProvider) {
        return new TestCase(methodProvider.getResult().getDisplayName(), className, methodProvider.getResult().getDuration(), discreteTestCaseExecutions(methodProvider));
    }

    private Iterable<? extends TestCaseExecution> discreteTestCaseExecutions(final TestResultsProvider methodProvider) {
        TestResult.ResultType resultType = methodProvider.getResult().getResultType();
        switch (resultType) {
            case FAILURE:
                return failures(methodProvider, FailureType.FAILURE);
            case SKIPPED:
                return Collections.singleton(skipped(methodProvider));
            case SUCCESS:
                return Collections.singleton(success(methodProvider));
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

    private TestCaseExecution success(TestResultsProvider methodProvider) {
        return new TestCaseExecutionSuccess(includeOutputIfNeeded(methodProvider), options);
    }

    private TestCaseExecution skipped(TestResultsProvider methodProvider) {
        return new TestCaseExecutionSkipped(includeOutputIfNeeded(methodProvider), options);
    }

    private Iterable<TestCaseExecution> failures(TestResultsProvider methodProvider, final FailureType failureType) {
        List<PersistentTestFailure> failures = methodProvider.getResult().getFailures();
        if (failures.isEmpty()) {
            // This can happen with a failing engine. For now, we just ignore this.
            return Collections.emptyList();
        }
        final PersistentTestFailure firstFailure = failures.get(0);
        return Iterables.transform(failures, failure -> {
            boolean isFirst = failure == firstFailure;
            TestResultsProvider outputProvider = isFirst ? includeOutputIfNeeded(methodProvider) : null;
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
