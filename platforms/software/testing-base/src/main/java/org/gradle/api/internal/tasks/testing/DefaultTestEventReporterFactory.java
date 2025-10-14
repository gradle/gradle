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

import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.testing.logging.SimpleTestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventProgressListener;
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;

@NullMarked
public final class DefaultTestEventReporterFactory implements TestEventReporterFactory {

    private final ListenerManager listenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final ObjectFactory objectFactory;

    @Inject
    public DefaultTestEventReporterFactory(
        ListenerManager listenerManager,
        StyledTextOutputFactory textOutputFactory,
        ProgressLoggerFactory progressLoggerFactory,
        ObjectFactory objectFactory
    ) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.objectFactory = objectFactory;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(
        String rootName,
        Directory binaryResultsDirectory,
        Directory htmlReportDirectory
    ) {
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);

        // Renders console output for the task
        testListenerInternalBroadcaster.add(new SimpleTestEventLogger(textOutputFactory));

        // Emits progress logger events
        testListenerInternalBroadcaster.add(new TestEventProgressListener(progressLoggerFactory));

        // Record all emitted results to disk
        Path binaryResultsDir = binaryResultsDirectory.getAsFile().toPath();
        SerializableTestResultStore.Writer resultsSerializingListener = newResultsSerializingListener(binaryResultsDir);
        testListenerInternalBroadcaster.add(resultsSerializingListener);

        GenericHtmlTestReportGenerator htmlReportGenerator = objectFactory.newInstance(GenericHtmlTestReportGenerator.class, htmlReportDirectory.getAsFile().toPath());

        return new LifecycleTrackingGroupTestEventReporter(new DefaultRootTestEventReporter(
            rootName,
            testListenerInternalBroadcaster.getSource(),
            new LongIdGenerator(),
            binaryResultsDir,
            resultsSerializingListener,
            htmlReportGenerator,
            listenerManager.getBroadcaster(TestExecutionResultsListener.class)
        ));
    }

    private SerializableTestResultStore.Writer newResultsSerializingListener(Path binaryResultsDir) {
        try {
            return new SerializableTestResultStore(binaryResultsDir).openWriter();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
