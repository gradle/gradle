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

import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.VerificationException;
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

@NullMarked
class DefaultRootTestEventReporter extends DefaultGroupTestEventReporter {

    private final Path binaryResultsDir;
    private final SerializableTestResultStore.Writer testResultWriter;
    private final GenericHtmlTestReportGenerator htmlTestReportGenerator;
    private final TestExecutionResultsListener executionResultsListener;

    // Mutable state
    @Nullable
    private String failureMessage;

    DefaultRootTestEventReporter(
        String rootName,
        TestListenerInternal listener,
        IdGenerator<?> idGenerator,
        Path binaryResultsDir,
        SerializableTestResultStore.Writer testResultWriter,
        GenericHtmlTestReportGenerator htmlTestReportGenerator,
        TestExecutionResultsListener executionResultsListener
    ) {
        super(
            listener,
            idGenerator,
            new DefaultTestSuiteDescriptor(idGenerator.generateId(), rootName),
            new TestResultState(null)
        );

        this.binaryResultsDir = binaryResultsDir;
        this.testResultWriter = testResultWriter;
        this.htmlTestReportGenerator = htmlTestReportGenerator;
        this.executionResultsListener = executionResultsListener;
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

        boolean rootTestFailed = failureMessage != null;

        // Notify aggregate listener of final results
        executionResultsListener.executionResultsAvailable(testDescriptor, binaryResultsDir, rootTestFailed);

        // Generate HTML report
        Path reportIndexFile = htmlTestReportGenerator.generate(Collections.singletonList(binaryResultsDir));

        // Throw an exception with rendered test results, if necessary
        if (rootTestFailed) {
            String testResultsUrl = new ConsoleRenderer().asClickableFileUrl(reportIndexFile.toFile());
            throw new VerificationException(failureMessage + " See the test results for more details: " + testResultsUrl);
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
