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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.xml.SimpleXmlWriter;
import org.gradle.util.internal.TextUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class JUnitXmlResultWriter {

    private final Path reportDirectory;
    private final String hostName;
    private final TestResultsProvider testResultsProvider;
    private final JUnitXmlResultOptions options;

    public JUnitXmlResultWriter(Path reportDirectory, String hostName, TestResultsProvider testResultsProvider, JUnitXmlResultOptions options) {
        this.reportDirectory = reportDirectory;
        this.hostName = hostName;
        this.testResultsProvider = testResultsProvider;
        this.options = options;
    }

    public void write(TestClassResult result, OutputStream output) {
        long classId = result.getId();

        try {
            SimpleXmlWriter writer = new SimpleXmlWriter(output, "  ");
            writer.startElement("testsuite")
                .attribute("name", result.getXmlTestSuiteName())

                // NOTE: these totals are unaffected by "merge reruns" with Surefire, so we do the same
                .attribute("tests", String.valueOf(result.getTestsCount()))
                .attribute("skipped", String.valueOf(result.getSkippedCount()))
                .attribute("failures", String.valueOf(result.getFailuresCount()))
                .attribute("errors", "0")

                .attribute("timestamp", ISO_INSTANT.format(Instant.ofEpochMilli(result.getStartTime())))
                .attribute("hostname", hostName)
                .attribute("time", String.valueOf(result.getDuration() / 1000.0));

            writer.startElement("properties");
            List<DefaultTestKeyValueDataEvent> keyValues = result.getMetadatas().stream().filter(DefaultTestKeyValueDataEvent.class::isInstance).map(DefaultTestKeyValueDataEvent.class::cast).collect(Collectors.toList());
            writeProperties(writer, keyValues);
            writer.endElement();

            Iterable<TestMethodResult> methodResults = result.getResults();
            String className = result.getClassName();

            if (options.mergeReruns) {
                writeTestCasesWithMergeRerunHandling(writer, methodResults, className, classId);
            } else {
                writeTestCasesWithDiscreteRerunHandling(writer, methodResults, className, classId);
            }

            List<DefaultTestFileAttachmentDataEvent> fileAttachments = result.getMetadatas().stream().filter(DefaultTestFileAttachmentDataEvent.class::isInstance).map(DefaultTestFileAttachmentDataEvent.class::cast).collect(Collectors.toList());
            writeOutputs(writer, "system-out", classId, options.includeSystemOutLog, !options.outputPerTestCase, TestOutputEvent.Destination.StdOut, fileAttachments);
            writeOutputs(writer, "system-err", classId, options.includeSystemErrLog, !options.outputPerTestCase, TestOutputEvent.Destination.StdErr, Collections.emptyList());

            writer.endElement();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void writeOutputs(SimpleXmlWriter writer, String elementName, long classId, boolean includeRawOutput, boolean allClassOutput, TestOutputEvent.Destination destination, List<DefaultTestFileAttachmentDataEvent> fileAttachments) throws IOException {
        boolean mayWrite = includeRawOutput || !fileAttachments.isEmpty();

        if (mayWrite) {
            writer.startElement(elementName);
            writer.startCDATA();
            if (includeRawOutput) {
                if (allClassOutput) {
                    testResultsProvider.writeAllOutput(classId, destination, writer);
                } else {
                    testResultsProvider.writeNonTestOutput(classId, destination, writer);
                }
            }
            writeFileAttachments(writer, fileAttachments);
            writer.endCDATA();
            writer.endElement();
        }
    }

    /**
     * Output in the format the Surefire uses when enabling retries.
     *
     * - https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html
     * - https://github.com/apache/maven-surefire/blob/edb3b71b95db98eef6a8e4fa98d376fd3512b05a/maven-surefire-plugin/src/site/resources/xsd/surefire-test-report-3.0.xsd
     * - https://plugins.jenkins.io/flaky-test-handler/
     *
     * We deviate from Maven's behaviour when there are any skipped executions, or when there are multiple successful executions.
     * The "standard" does not specify the behaviour in this circumstance.
     *
     * There's no way to convey multiple successful "executions" of a test case in the XML structure.
     * If this happens, Maven just omits any information about successful executions if they were not the last.
     * We break the executions up into multiple testcases, which each successful execution being the last execution
     * of the test case, so as to not drop information.
     */
    private void writeTestCasesWithMergeRerunHandling(SimpleXmlWriter writer, final Iterable<TestMethodResult> methodResults, final String className, final long classId) throws IOException {
        List<List<TestMethodResult>> groupedExecutions = groupExecutions(methodResults);

        for (final List<TestMethodResult> groupedExecution : groupedExecutions) {
            if (groupedExecution.size() == 1) {
                writeTestCase(writer, discreteTestCase(className, classId, groupedExecution.get(0)));
            } else {
                final TestMethodResult firstExecution = groupedExecution.get(0);
                final TestMethodResult lastExecution = groupedExecution.get(groupedExecution.size() - 1);
                final boolean allFailed = lastExecution.getResultType() == TestResult.ResultType.FAILURE;

                // https://maven.apache.org/components/surefire/maven-surefire-plugin/examples/rerun-failing-tests.html
                // (if) The test passes in one of its re-runs […] the running time of a flaky test will be the running time of the last successful run.
                // (if) The test fails in all of the re-runs […] the running time of a failing test with re-runs will be the running time of the first failing run.
                long duration = allFailed ? firstExecution.getDuration() : lastExecution.getDuration();

                writeTestCase(writer, new TestCase(
                    firstExecution.getDisplayName(),
                    className,
                    duration,
                    mergeRerunExecutions(allFailed, groupedExecution, firstExecution, classId)
                ));
            }
        }
    }

    private Iterable<TestCaseExecution> mergeRerunExecutions(final boolean allFailed, final List<TestMethodResult> groupedExecution, final TestMethodResult firstExecution, final long classId) {
        return Iterables.concat(Iterables.transform(groupedExecution, new Function<TestMethodResult, Iterable<? extends TestCaseExecution>>() {
            @Override
            public Iterable<? extends TestCaseExecution> apply(final TestMethodResult execution) {
                switch (execution.getResultType()) {
                    case SUCCESS:
                        return Collections.singleton(success(classId, execution.getId(), execution.getMetadatas()));
                    case SKIPPED:
                        return Collections.singleton(skipped(classId, execution.getId(), execution.getAssumptionFailure(), execution.getMetadatas()));
                    case FAILURE:
                        return failures(classId, execution, allFailed
                            ? execution == firstExecution ? FailureType.FAILURE : FailureType.RERUN_FAILURE
                            : FailureType.FLAKY_FAILURE
                        );
                    default:
                        throw new IllegalStateException("unhandled type: " + execution.getResultType());
                }
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
    private List<List<TestMethodResult>> groupExecutions(Iterable<TestMethodResult> methodResults) {
        List<List<TestMethodResult>> groupedExecutions = new ArrayList<List<TestMethodResult>>();
        Map<String, Integer> latestGroupForName = new HashMap<String, Integer>();

        for (TestMethodResult methodResult : methodResults) {
            String name = methodResult.getDisplayName();
            Integer index = latestGroupForName.get(name);
            if (index == null) {
                List<TestMethodResult> executions = Collections.singletonList(methodResult);
                groupedExecutions.add(executions);
                if (methodResult.getResultType() == TestResult.ResultType.FAILURE) {
                    latestGroupForName.put(name, groupedExecutions.size() - 1);
                }
            } else {
                List<TestMethodResult> executions = groupedExecutions.get(index);
                if (executions.size() == 1) {
                    executions = new ArrayList<TestMethodResult>(executions);
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

    private void writeTestCasesWithDiscreteRerunHandling(SimpleXmlWriter writer, final Iterable<TestMethodResult> methodResults, final String className, final long classId) throws IOException {
        for (TestMethodResult methodResult : methodResults) {
            writeTestCase(writer, discreteTestCase(className, classId, methodResult));
        }
    }

    private TestCase discreteTestCase(String className, long classId, TestMethodResult methodResult) {
        return new TestCase(methodResult.getDisplayName(), className, methodResult.getDuration(), discreteTestCaseExecutions(classId, methodResult));
    }

    private Iterable<? extends TestCaseExecution> discreteTestCaseExecutions(final long classId, final TestMethodResult methodResult) {
        switch (methodResult.getResultType()) {
            case FAILURE:
                return failures(classId, methodResult, FailureType.FAILURE);
            case SKIPPED:
                return Collections.singleton(skipped(classId, methodResult.getId(), methodResult.getAssumptionFailure(), methodResult.getMetadatas()));
            case SUCCESS:
                return Collections.singleton(success(classId, methodResult.getId(), methodResult.getMetadatas()));
            default:
                throw new IllegalStateException("Unexpected result type: " + methodResult.getResultType());
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

    abstract class TestCaseExecution {
        private final OutputProvider outputProvider;
        private final JUnitXmlResultOptions options;
        private final List<TestMetadataEvent> metadatas;

        TestCaseExecution(OutputProvider outputProvider, JUnitXmlResultOptions options, List<TestMetadataEvent> metadatas) {
            this.outputProvider = outputProvider;
            this.options = options;
            this.metadatas = metadatas;
        }

        protected void write(SimpleXmlWriter writer) throws IOException {
            List<DefaultTestKeyValueDataEvent> keyValues = metadatas.stream().filter(DefaultTestKeyValueDataEvent.class::isInstance).map(DefaultTestKeyValueDataEvent.class::cast).collect(Collectors.toList());
            if (!keyValues.isEmpty()) {
                writer.startElement("properties");
                writeProperties(writer, keyValues);
                writer.endElement();
            }
        }

        protected void writeOutput(SimpleXmlWriter writer) throws IOException {
            List<DefaultTestFileAttachmentDataEvent> fileAttachments = metadatas.stream().filter(DefaultTestFileAttachmentDataEvent.class::isInstance).map(DefaultTestFileAttachmentDataEvent.class::cast).collect(Collectors.toList());
            boolean mayWrite = (options.includeSystemOutLog && outputProvider.has(TestOutputEvent.Destination.StdOut)) || !fileAttachments.isEmpty();
            if (mayWrite) {
                writer.startElement("system-out");
                writer.startCDATA();
                if (options.includeSystemOutLog && outputProvider.has(TestOutputEvent.Destination.StdOut)) {
                    outputProvider.write(TestOutputEvent.Destination.StdOut, writer);
                }
                writeFileAttachments(writer, fileAttachments);
                writer.endCDATA();
                writer.endElement();
            }

            if (options.includeSystemErrLog && outputProvider.has(TestOutputEvent.Destination.StdErr)) {
                writer.startElement("system-err");
                writer.startCDATA();
                outputProvider.write(TestOutputEvent.Destination.StdErr, writer);
                writer.endCDATA();
                writer.endElement();
            }
        }
    }

    private void writeFileAttachments(SimpleXmlWriter writer, List<DefaultTestFileAttachmentDataEvent> fileAttachments) throws IOException {
        if (!fileAttachments.isEmpty()) {
            writer.write('\n');
            for (DefaultTestFileAttachmentDataEvent fileAttachment : fileAttachments) {
                // Always produce *nix style paths:
                writer.write("[[ATTACHMENT|" + TextUtil.normaliseFileSeparators(reportDirectory.relativize(fileAttachment.getPath()).toString()) + "]]\n");
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

    private class TestCaseExecutionSuccess extends TestCaseExecution {
        TestCaseExecutionSuccess(OutputProvider outputProvider, JUnitXmlResultOptions options, List<TestMetadataEvent> metadatas) {
            super(outputProvider, options, metadatas);
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            super.write(writer);

            writeOutput(writer);
        }
    }


    private class TestCaseExecutionSkipped extends TestCaseExecution {
        private final SerializableFailure assumptionFailure;
        TestCaseExecutionSkipped(
            OutputProvider outputProvider,
            JUnitXmlResultOptions options,
            SerializableFailure assumptionFailure,
            List<TestMetadataEvent> metadatas
        ) {
            super(outputProvider, options, metadatas);
            this.assumptionFailure = assumptionFailure;
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            super.write(writer);

            writer.startElement("skipped");
            if (assumptionFailure != null) {
                writer.attribute("message", assumptionFailure.getMessage());
                writer.attribute("type", assumptionFailure.getExceptionType());
                writer.write(assumptionFailure.getStackTrace());
            }
            writer.endElement();
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

    private class TestCaseExecutionFailure extends TestCaseExecution {
        private final SerializableFailure failure;
        private final FailureType type;

        TestCaseExecutionFailure(OutputProvider outputProvider, JUnitXmlResultOptions options, FailureType type, SerializableFailure failure, List<TestMetadataEvent> metadatas) {
            super(outputProvider, options, metadatas);
            this.failure = failure;
            this.type = type;
        }

        @Override
        public void write(SimpleXmlWriter writer) throws IOException {
            super.write(writer);

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

    private TestCaseExecution success(long classId, long id, List<TestMetadataEvent> metadatas) {
        return new TestCaseExecutionSuccess(outputProvider(classId, id), options, metadatas);
    }

    private TestCaseExecution skipped(long classId, long id, SerializableFailure assumptionFailure, List<TestMetadataEvent> metadatas) {
        return new TestCaseExecutionSkipped(outputProvider(classId, id), options, assumptionFailure, metadatas);
    }

    private Iterable<TestCaseExecution> failures(final long classId, final TestMethodResult methodResult, final FailureType failureType) {
        List<SerializableFailure> failures = methodResult.getFailures();
        if (failures.isEmpty()) {
            // This can happen with a failing engine. For now, we just ignore this.
            return Collections.emptyList();
        }
        final SerializableFailure firstFailure = failures.get(0);
        return Iterables.transform(failures, new Function<SerializableFailure, TestCaseExecution>() {
            @Override
            public TestCaseExecution apply(final SerializableFailure failure) {
                boolean isFirst = failure == firstFailure;
                OutputProvider outputProvider = isFirst ? outputProvider(classId, methodResult.getId()) : NullOutputProvider.INSTANCE;
                return new TestCaseExecutionFailure(outputProvider, options, failureType, failure, methodResult.getMetadatas());
            }
        });
    }

    private static void writeProperties(SimpleXmlWriter writer, List<DefaultTestKeyValueDataEvent> metadatas) throws IOException {
        for (DefaultTestKeyValueDataEvent metadata : metadatas) {
            for (Map.Entry<String, String> element : metadata.getValues().entrySet()) {
                writer.startElement("property")
                    .attribute("name", element.getKey())
                    .attribute("value", element.getValue())
                    .endElement();
            }
        }
    }

    private OutputProvider outputProvider(long classId, long id) {
        return options.outputPerTestCase ? new BackedOutputProvider(classId, id) : NullOutputProvider.INSTANCE;
    }

    interface OutputProvider {
        boolean has(TestOutputEvent.Destination destination);

        void write(TestOutputEvent.Destination destination, Writer writer);
    }

    class BackedOutputProvider implements OutputProvider {
        private final long classId;
        private final long testId;

        public BackedOutputProvider(long classId, long testId) {
            this.classId = classId;
            this.testId = testId;
        }

        @Override
        public boolean has(TestOutputEvent.Destination destination) {
            return testResultsProvider.hasOutput(classId, testId, destination);
        }

        @Override
        public void write(TestOutputEvent.Destination destination, Writer writer) {
            testResultsProvider.writeTestOutput(classId, testId, destination, writer);
        }
    }

    static class NullOutputProvider implements OutputProvider {
        static final OutputProvider INSTANCE = new NullOutputProvider();

        @Override
        public boolean has(TestOutputEvent.Destination destination) {
            return false;
        }

        @Override
        public void write(TestOutputEvent.Destination destination, Writer writer) {
            throw new UnsupportedOperationException();
        }
    }

}
