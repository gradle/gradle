/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.LongFunction;

@NullMarked
class DefaultRootTestEventReporter extends DefaultGroupTestEventReporter {

    private static final String DEFAULT_FAILURE_MESSAGE = "Test(s) failed.";

    private final Path binaryResultsDir;
    private final SerializableTestResultStore.Writer testResultWriter;
    @Nullable
    private final TestReportGenerator testReportGenerator;
    private final TestExecutionResultsListener executionResultsListener;
    private final boolean closeThrowsOnTestFailures;

    // Mutable state
    @Nullable
    private String failureMessage;
    private long endTime;

    DefaultRootTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptorFactory,
        TestListenerInternal listener,
        IdGenerator<Long> idGenerator,
        Path binaryResultsDir,
        SerializableTestResultStore.Writer testResultWriter,
        @Nullable TestReportGenerator testReportGenerator,
        TestExecutionResultsListener executionResultsListener,
        boolean closeThrowsOnTestFailures
    ) {
        super(
            listener,
            idGenerator,
            rootDescriptorFactory.apply(idGenerator.generateId()),
            new TestResultState(null)
        );

        this.binaryResultsDir = binaryResultsDir;
        this.testResultWriter = testResultWriter;
        this.testReportGenerator = testReportGenerator;
        this.executionResultsListener = executionResultsListener;
        this.closeThrowsOnTestFailures = closeThrowsOnTestFailures;
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Throwable t) {
            // Ensure binary results are written to disk.
            try {
                testResultWriter.close();
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            throw t;
        }

        try {
            testResultWriter.close();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        GeneratedTestReport report = testReportGenerator == null
            ? null
            : testReportGenerator.generate(Collections.singletonList(binaryResultsDir));

        // Notify aggregate listener of final results
        executionResultsListener.executionResultsAvailable(createExecution(report));

        // Throw an exception with rendered test results, if necessary
        boolean hasTestFailures = failureMessage != null;
        if (hasTestFailures && closeThrowsOnTestFailures) {
            if (report == null) {
                // No report was requested, just fail with the message we have
                throw new MarkedVerificationException(failureMessage);
            }
            String testResultsUrl = new ConsoleRenderer().asClickableFileUrl(report.location().toFile());
            throw new MarkedVerificationException(failureMessage + " See the report at: " + testResultsUrl);
        }
    }

    @Override
    public void succeeded(Instant endTime) {
        this.endTime = endTime.toEpochMilli();
        super.succeeded(endTime);
    }

    @Override
    public void skipped(Instant endTime, @Nullable TestFailure assumptionFailure) {
        this.endTime = endTime.toEpochMilli();
        super.skipped(endTime, assumptionFailure);
    }

    @Override
    public void failed(Instant endTime, List<TestFailure> failures) {
        this.endTime = endTime.toEpochMilli();
        if (failureMessage == null) {
            // Failures reported directly, such as by Test tasks, have no message of their own
            failureMessage = DEFAULT_FAILURE_MESSAGE;
        }
        super.failed(endTime, failures);
    }

    @Override
    public void failed(Instant endTime, String message, String additionalContent) {
        if (TextUtil.isBlank(message)) {
            this.failureMessage = DEFAULT_FAILURE_MESSAGE;
        } else {
            this.failureMessage = message;
        }
        super.failed(endTime, message, additionalContent);
    }

    private TestExecutionResultsListener.Execution createExecution(@Nullable GeneratedTestReport report) {
        long testCount = testResultState.getTotalCount();
        long failedCount = testResultState.getFailureCount();
        long skippedCount = testCount - testResultState.getSuccessfulCount() - failedCount;
        boolean failed = failureMessage != null || failedCount > 0;
        return new TestExecutionResultsListener.Execution(
            testDescriptor.getDisplayName(),
            getStartTime(),
            endTime,
            testCount,
            failedCount,
            skippedCount,
            failed,
            report
        );
    }

}
