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
import java.util.function.LongFunction;
import java.util.function.Supplier;

@NullMarked
class DefaultRootTestEventReporter extends DefaultGroupTestEventReporter {

    private final Path binaryResultsDir;
    private final SerializableTestResultStore.Writer testResultWriter;
    private final TestReportGenerator testReportGenerator;
    private final TestExecutionResultsListener executionResultsListener;
    private final Supplier<TestEventReporterFactoryInternal.FailureReportResult> tryReportFailures;

    // Mutable state
    @Nullable
    private String failureMessage;

    DefaultRootTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptorFactory,
        TestListenerInternal listener,
        IdGenerator<Long> idGenerator,
        Path binaryResultsDir,
        SerializableTestResultStore.Writer testResultWriter,
        TestReportGenerator testReportGenerator,
        TestExecutionResultsListener executionResultsListener,
        Supplier<TestEventReporterFactoryInternal.FailureReportResult> tryReportFailures
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
        this.tryReportFailures = tryReportFailures;
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

        // Generate HTML report
        Path reportIndexFile = testReportGenerator.generate(Collections.singletonList(binaryResultsDir));

        TestEventReporterFactoryInternal.FailureReportResult reportResult = tryReportFailures.get();
        String failureMessage;
        if (reportResult instanceof TestEventReporterFactoryInternal.FailureReportResult.TestFailureDetected) {
            failureMessage = ((TestEventReporterFactoryInternal.FailureReportResult.TestFailureDetected) reportResult).getFailureMessage();
        } else {
            failureMessage = this.failureMessage;
        }
        boolean hasTestFailures = failureMessage != null;

        // Notify aggregate listener of final results
        executionResultsListener.executionResultsAvailable(testDescriptor, binaryResultsDir, hasTestFailures);

        if (reportResult instanceof TestEventReporterFactoryInternal.FailureReportResult.FailureReported) {
            // Failures were reported by the tryReportFailures handler, so we don't need to throw an exception here.
            return;
        }

        // Throw an exception with rendered test results, if necessary
        if (hasTestFailures) {
            if (reportIndexFile == null) {
                // This can happen if we're given the NO_OP generator internally, in which case no report was requested so we will simply fail.
                throw new MarkedVerificationException(failureMessage);
            }
            String testResultsUrl = new ConsoleRenderer().asClickableFileUrl(reportIndexFile.toFile());
            throw new MarkedVerificationException(failureMessage + " See the test results for more details: " + testResultsUrl);
        }
    }

    @Override
    public void failed(Instant endTime, String message, String additionalContent) {
        if (TextUtil.isBlank(message)) {
            this.failureMessage = "Test(s) failed.";
        } else {
            this.failureMessage = message;
        }
        super.failed(endTime, message, additionalContent);
    }
}
