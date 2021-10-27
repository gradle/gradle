/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.AttachParentTestResultProcessor;
import org.gradle.internal.time.Clock;

public class TestMainAction implements Runnable {
    private final Runnable detector;
    private final TestClassProcessor processor;
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final Object rootTestSuiteId;
    private final String displayName;

    public TestMainAction(Runnable detector, TestClassProcessor processor, TestResultProcessor resultProcessor, Clock clock, Object rootTestSuiteId, String displayName) {
        this.detector = detector;
        this.processor = processor;
        this.resultProcessor = new AttachParentTestResultProcessor(resultProcessor);
        this.clock = clock;
        this.rootTestSuiteId = rootTestSuiteId;
        this.displayName = displayName;
    }

    @Override
    public void run() {
        TestDescriptorInternal suite = new RootTestSuiteDescriptor(rootTestSuiteId, displayName);
        resultProcessor.started(suite, new TestStartEvent(clock.getCurrentTime()));
        try {
            processor.startProcessing(resultProcessor);
            try {
                detector.run();
            } finally {
                processor.stop();
            }
        } finally {
            resultProcessor.completed(suite.getId(), new TestCompleteEvent(clock.getCurrentTime()));
        }
    }

    private static final class RootTestSuiteDescriptor extends DefaultTestSuiteDescriptor {
        private RootTestSuiteDescriptor(Object id, String name) {
            super(id, name);
        }

        @Override
        public String toString() {
            return getName();
        }
    }
}
