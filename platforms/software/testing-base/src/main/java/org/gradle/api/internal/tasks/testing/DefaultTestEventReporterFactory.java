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
import org.gradle.api.internal.tasks.testing.logging.SimpleTestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventProgressListener;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

@NonNullApi
public final class DefaultTestEventReporterFactory implements TestEventReporterFactory {
    private final ListenerManager listenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final ProgressLoggerFactory progressLoggerFactory;

    public DefaultTestEventReporterFactory(ListenerManager listenerManager, StyledTextOutputFactory textOutputFactory, ProgressLoggerFactory progressLoggerFactory) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(String rootName) {
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);

        // Renders console output for the task
        testListenerInternalBroadcaster.add(new SimpleTestEventLogger(textOutputFactory));
        // Emits progress logger events
        testListenerInternalBroadcaster.add(new TestEventProgressListener(progressLoggerFactory));

        IdGenerator<?> idGenerator = new LongIdGenerator();
        return new LifecycleTrackingGroupTestEventReporter(new DefaultRootTestEventReporter(testListenerInternalBroadcaster.getSource(), idGenerator, new DefaultTestSuiteDescriptor(idGenerator.generateId(), rootName)));
    }
}
