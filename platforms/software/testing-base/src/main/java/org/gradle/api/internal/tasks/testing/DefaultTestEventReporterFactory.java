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

import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.testing.logging.SimpleTestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventProgressListener;
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

@NullMarked
public final class DefaultTestEventReporterFactory implements TestEventReporterFactoryInternal {

    private final ListenerManager listenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Instantiator instantiator;
    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final MetadataRendererRegistry metadataRendererRegistry;

    @Inject
    public DefaultTestEventReporterFactory(
        ListenerManager listenerManager,
        StyledTextOutputFactory textOutputFactory,
        ProgressLoggerFactory progressLoggerFactory,
        Instantiator instantiator,
        BuildOperationRunner buildOperationRunner,
        BuildOperationExecutor buildOperationExecutor,
        MetadataRendererRegistry metadataRendererRegistry
    ) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.instantiator = instantiator;
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
        this.metadataRendererRegistry = metadataRendererRegistry;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(String rootName, Directory binaryResultsDirectory, Directory htmlReportDirectory, boolean failOnTestFailures) {
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);

        // Renders console output for the task
        testListenerInternalBroadcaster.add(new SimpleTestEventLogger(textOutputFactory));

        // Emits progress logger events
        testListenerInternalBroadcaster.add(new TestEventProgressListener(progressLoggerFactory));

        GenericHtmlTestReportGenerator reportGenerator = instantiator.newInstance(GenericHtmlTestReportGenerator.class, buildOperationRunner, buildOperationExecutor, metadataRendererRegistry, htmlReportDirectory.getAsFile().toPath());
        GroupTestEventReporter testEventReporter = createInternalTestEventReporter(
            id -> new DefaultTestSuiteDescriptor(id, rootName),
            binaryResultsDirectory,
            reportGenerator,
            testListenerInternalBroadcaster,
            false,
            () -> scanBinaryResultsForFailures(binaryResultsDirectory)
        );

        if (failOnTestFailures) {
            return new FailOnCloseTestEventReporter(testEventReporter, htmlReportDirectory.getAsFile().toPath());
        } else {
            return testEventReporter;
        }
    }

    // TODO: This is slow, need to optimize checking for a failure by (at least) short-circuiting the scan as soon as we find one
    // Alternately, maybe we can just add something like TestEventReporter#reportedAnyFailures() and use that
    private Boolean scanBinaryResultsForFailures(Directory binaryResultsDirectory) {
        Path binaryResultsDir = binaryResultsDirectory.getAsFile().toPath();
        SerializableTestResultStore store = new SerializableTestResultStore(binaryResultsDir);

        FailureScanner consumer = new FailureScanner();
        try {
            store.forEachResult(consumer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return consumer.hasFailures;
    }

    private static final class FailureScanner implements Consumer<SerializableTestResultStore.OutputTrackedResult> {
        private boolean hasFailures = false;

        @Override
        public void accept(SerializableTestResultStore.OutputTrackedResult outputTrackedResult) {
            if (!outputTrackedResult.getInnerResult().getFailures().isEmpty()) {
                hasFailures = true;
            }
        }
    }

    @Override
    public GroupTestEventReporterInternal createInternalTestEventReporter(
        LongFunction<TestDescriptorInternal> rootDescriptorFactory,
        Directory binaryResultsDirectory,
        TestReportGenerator reportGenerator,
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster,
        boolean skipFirstLevelOnDisk,
        Supplier<Boolean> failureChecker
    ) {
        // Record all emitted results to disk
        Path binaryResultsDir = binaryResultsDirectory.getAsFile().toPath();
        SerializableTestResultStore.Writer resultsSerializingListener;
        try {
            resultsSerializingListener = new SerializableTestResultStore(binaryResultsDir).openWriter(skipFirstLevelOnDisk);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        testListenerInternalBroadcaster.add(resultsSerializingListener);

        TestListenerInternal testListenerBroadcaster = testListenerInternalBroadcaster.getSource();
        TestExecutionResultsListener executionResultsListenerBroadcaster = listenerManager.getBroadcaster(TestExecutionResultsListener.class);

        return new LifecycleTrackingGroupTestEventReporter(new DefaultRootTestEventReporter(
            rootDescriptorFactory,
            testListenerBroadcaster,
            new LongIdGenerator(),
            binaryResultsDir,
            resultsSerializingListener,
            reportGenerator,
            executionResultsListenerBroadcaster,
            failureChecker::get
        ));
    }

    private static final class FailOnCloseTestEventReporter implements GroupTestEventReporter {
        private final GroupTestEventReporter delegate;
        private final Path htmlReportPath;

        private boolean hadAnyFailures = false;

        public FailOnCloseTestEventReporter(GroupTestEventReporter delegate, Path reportPath) {
            this.delegate = delegate;
            this.htmlReportPath = reportPath;
        }

        @Override
        public TestEventReporter reportTest(String name, String displayName) {
            return delegate.reportTest(name, displayName);
        }

        @Override
        public GroupTestEventReporter reportTestGroup(String name) {
            return delegate.reportTestGroup(name);
        }

        @Override
        public void started(Instant startTime) {
            delegate.started(startTime);
        }

        @Override
        public void output(Instant logTime, TestOutputEvent.Destination destination, String output) {
            delegate.output(logTime, destination, output);
        }

        @Override
        public void metadata(Instant logTime, String key, Object value) {
            delegate.metadata(logTime, key, value);
        }

        @Override
        public void metadata(Instant logTime, Map<String, Object> values) {
            delegate.metadata(logTime, values);
        }

        @Override
        public void succeeded(Instant endTime) {
            delegate.succeeded(endTime);
        }

        @Override
        public void skipped(Instant endTime) {

        }

        @Override
        public void failed(Instant endTime, String message, String additionalContent) {
            hadAnyFailures = true;
            delegate.failed(endTime, message, additionalContent);
        }

        @Override
        public void close() {
            delegate.close();

            if (delegate instanceof GroupTestEventReporterInternal) {
                if (hadAnyFailures) {
                    String reportUrl = new ConsoleRenderer().asClickableFileUrl(htmlReportPath.toFile());
                    String message = "See the report at: " + reportUrl;
                    GradleException cause = new GradleException(message);
                    throw new GradleException("Test(s) failed.", cause); // TODO: broken
                }
            }
        }
    }
}
