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
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.tasks.testing.logging.SimpleTestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventProgressListener;
import org.gradle.api.internal.tasks.testing.results.HtmlTestReportGenerator;
import org.gradle.api.internal.tasks.testing.results.TestExecutionResultsListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

import javax.inject.Inject;
import java.nio.file.Path;

@NonNullApi
public final class DefaultTestEventReporterFactory implements TestEventReporterFactory {

    private final ListenerManager listenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final ProjectLayout projectLayout;
    private final HtmlTestReportGenerator htmlTestReportGenerator;

    @Inject
    public DefaultTestEventReporterFactory(
        ListenerManager listenerManager,
        StyledTextOutputFactory textOutputFactory,
        ProgressLoggerFactory progressLoggerFactory,
        ProjectLayout projectLayout,
        HtmlTestReportGenerator htmlTestReportGenerator
    ) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.projectLayout = projectLayout;
        this.htmlTestReportGenerator = htmlTestReportGenerator;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(String rootName) {
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);

        // Renders console output for the task
        testListenerInternalBroadcaster.add(new SimpleTestEventLogger(textOutputFactory));

        // Emits progress logger events
        testListenerInternalBroadcaster.add(new TestEventProgressListener(progressLoggerFactory));

        // Record all emitted results to disk
        Path resultsDir = projectLayout.getBuildDirectory().get().dir("test-results").dir(rootName).getAsFile().toPath();
        RootTestEventRecorder binaryResultsRecorder = new RootTestEventRecorder(resultsDir);
        testListenerInternalBroadcaster.add(binaryResultsRecorder);

        // TODO: Use dir from reporting extension?
        Path reportDir = projectLayout.getBuildDirectory().get().dir("reports").dir("tests").dir(rootName).getAsFile().toPath();

        return new LifecycleTrackingGroupTestEventReporter(new DefaultRootTestEventReporter(
            rootName,
            testListenerInternalBroadcaster.getSource(),
            new LongIdGenerator(),
            reportDir,
            binaryResultsRecorder,
            htmlTestReportGenerator,
            listenerManager.getBroadcaster(TestExecutionResultsListener.class)
        ));
    }

}
