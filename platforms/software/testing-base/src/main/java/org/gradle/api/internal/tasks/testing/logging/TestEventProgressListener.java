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

package org.gradle.api.internal.tasks.testing.logging;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates ProgressLogger updates for test events.
 */
@NonNullApi
public class TestEventProgressListener implements TestListenerInternal {

    private static final int MAX_TEST_NAME_LENGTH = 60;

    private final ProgressLoggerFactory factory;
    private final Map<Object, ProgressLogger> progressLoggers = new ConcurrentHashMap<>();

    public TestEventProgressListener(ProgressLoggerFactory factory) {
        this.factory = factory;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        final ProgressLogger progressLogger;
        if (testDescriptor.getParent() == null) {
            progressLogger = factory.newOperation(TestEventProgressListener.class);
        } else {
            ProgressLogger parentProgressLogger = progressLoggers.get(testDescriptor.getParent().getId());
            assert parentProgressLogger != null;
            progressLogger = factory.newOperation(TestEventProgressListener.class, parentProgressLogger);
        }

        final String description;
        if (testDescriptor.isComposite()) {
            description = testDescriptor.getDisplayName();
        } else {
            description = "Executing test " + JavaClassNameFormatter.abbreviateJavaPackage(testDescriptor.getDisplayName(), MAX_TEST_NAME_LENGTH);
        }
        progressLogger.start(description, description);
        progressLoggers.put(testDescriptor.getId(), progressLogger);
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        ProgressLogger progressLogger = progressLoggers.remove(testDescriptor.getId());
        assert progressLogger!=null;
        progressLogger.completed();
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
    }

    @Override
    public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
    }
}
