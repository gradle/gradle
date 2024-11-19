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
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

@NonNullApi
public final class DefaultTestEventReporterFactory implements TestEventReporterFactory {
    private final StyledTextOutputFactory textOutputFactory;
    private final ListenerManager listenerManager;

    public DefaultTestEventReporterFactory(ListenerManager listenerManager, StyledTextOutputFactory textOutputFactory) {
        this.listenerManager = listenerManager;
        this.textOutputFactory = textOutputFactory;
    }

    @Override
    public GroupTestEventReporter createTestEventReporter(String rootName) {
        SimpleTestEventLogger eventLogger = new SimpleTestEventLogger(textOutputFactory);

        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);
        testListenerInternalBroadcaster.add(eventLogger);

        TestResultProcessor processor = new StateTrackingTestResultProcessor(testListenerInternalBroadcaster.getSource());
        IdGenerator<?> idGenerator = new LongIdGenerator();

        return new DefaultRootTestEventReporter(processor, idGenerator, new DefaultTestSuiteDescriptor(idGenerator.generateId(), rootName));
    }
}
