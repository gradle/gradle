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

import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestOutputStore;
import org.gradle.api.internal.tasks.testing.junit.result.TestReportDataCollector;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer;
import org.gradle.api.internal.tasks.testing.logging.SimpleTestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventProgressListener;
import org.gradle.api.internal.tasks.testing.results.HtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@NonNullApi
public final class DefaultTestEventReporterFactory implements TestEventReporterFactory {

    private final ListenerManager listenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final HtmlTestReportGenerator htmlTestReportGenerator;

    @Inject
    public DefaultTestEventReporterFactory(
        ListenerManager listenerManager,
        StyledTextOutputFactory textOutputFactory,
        ProgressLoggerFactory progressLoggerFactory,
        HtmlTestReportGenerator htmlTestReportGenerator
    ) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.htmlTestReportGenerator = htmlTestReportGenerator;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(
        String rootName,
        Path binaryResultsDirectory,
        Path htmlReportDirectory
    ) {
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);

        // Renders console output for the task
        testListenerInternalBroadcaster.add(new SimpleTestEventLogger(textOutputFactory));

        // Emits progress logger events
        testListenerInternalBroadcaster.add(new TestEventProgressListener(progressLoggerFactory));

        // Record all emitted results to disk
        ClosableTestReportDataCollector testReportDataCollector = new ClosableTestReportDataCollector(binaryResultsDirectory);
        testListenerInternalBroadcaster.add(new TestListenerAdapter(testReportDataCollector.getDelegate(), testReportDataCollector.getDelegate()));

        return new LifecycleTrackingGroupTestEventReporter(new DefaultRootTestEventReporter(
            rootName,
            testListenerInternalBroadcaster.getSource(),
            new LongIdGenerator(),
            htmlReportDirectory,
            testReportDataCollector,
            htmlTestReportGenerator,
            listenerManager.getBroadcaster(TestExecutionResultsListener.class)
        ));
    }

    /**
     * Wrapper for {@link TestReportDataCollector} that track extra state, ensuring it is properly closable.
     *
     * This should eventually be merged with TestReportDataCollector.
     */
    public static class ClosableTestReportDataCollector implements Closeable {

        private final Path resultsDirectory;

        private final Map<String, TestClassResult> results;
        private final TestOutputStore.Writer outputWriter;
        private final TestReportDataCollector delegate;

        public ClosableTestReportDataCollector(Path resultsDirectory) {
            this.resultsDirectory = resultsDirectory;

            TestOutputStore testOutputStore = new TestOutputStore(resultsDirectory.toFile());
            this.outputWriter = testOutputStore.writer();
            this.results = new HashMap<>();

            try {
                Files.createDirectories(resultsDirectory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            this.delegate = new TestReportDataCollector(
                new HashMap<>(),
                outputWriter
            );
        }

        public TestReportDataCollector getDelegate() {
            return delegate;
        }

        public Path getBinaryResultsDirectory() {
            return resultsDirectory;
        }

        @Override
        public void close() {
            outputWriter.close();
            new TestResultSerializer(resultsDirectory.toFile()).write(results.values());
        }
    }

}
