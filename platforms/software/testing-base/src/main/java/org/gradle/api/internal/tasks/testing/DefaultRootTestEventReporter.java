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

import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.LongFunction;

@NullMarked
class DefaultRootTestEventReporter extends DefaultGroupTestEventReporter {

    private final Path binaryResultsDir;
    private final SerializableTestResultStore.Writer testResultWriter;
    private final TestReportGenerator testReportGenerator;
    private final TestExecutionResultsListener executionResultsListener;
    private final Lazy<Boolean> failureChecker;

    DefaultRootTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptorFactory,
        TestListenerInternal listener,
        IdGenerator<Long> idGenerator,
        Path binaryResultsDir,
        SerializableTestResultStore.Writer testResultWriter,
        TestReportGenerator testReportGenerator,
        TestExecutionResultsListener executionResultsListener,
        Lazy<Boolean> failureChecker
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
        this.failureChecker = failureChecker;
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
        testReportGenerator.generate(Collections.singletonList(binaryResultsDir));

        // Notify aggregate listener of final results
        executionResultsListener.executionResultsAvailable(testDescriptor, binaryResultsDir, hasFailures());
    }

    public boolean hasFailures() {
        return failureChecker.get();
    }
}
