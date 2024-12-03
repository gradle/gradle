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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.junit.result.TestReportDataCollector;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records test events for a single root test execution to a binary results directory.
 */
public class RootTestEventRecorder implements TestListenerInternal, Closeable {

    private final TestListenerInternal delegate;
    private final TestReportDataCollector testReportDataCollector;
    private final Path resultsDirectory;

    public RootTestEventRecorder(Path resultsDirectory) {
        this.resultsDirectory = resultsDirectory;

        try {
            Files.createDirectories(resultsDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.testReportDataCollector = new TestReportDataCollector(
            resultsDirectory,
            new ConcurrentHashMap<>()
        );
        this.delegate = new TestListenerAdapter(testReportDataCollector, testReportDataCollector);
    }

    public Path getBinaryResultsDirectory() {
        return resultsDirectory;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        delegate.started(testDescriptor, startEvent);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        delegate.completed(testDescriptor, testResult, completeEvent);
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        delegate.output(testDescriptor, event);
    }

    @Override
    public void close() {
        testReportDataCollector.close();
    }
}
