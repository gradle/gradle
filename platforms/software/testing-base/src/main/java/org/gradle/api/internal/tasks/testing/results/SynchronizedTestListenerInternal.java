/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.jspecify.annotations.NullMarked;

/**
 * Wrapper to synchronize over all calls to a delegate {@link TestListenerInternal}.
 */
@NullMarked
public final class SynchronizedTestListenerInternal implements TestListenerInternal {
    private final TestListenerInternal delegate;
    private final Object lock = new Object();

    public SynchronizedTestListenerInternal(TestListenerInternal delegate) {
        this.delegate = delegate;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {
        synchronized (lock) {
            delegate.started(testDescriptor, startEvent);
        }
    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        synchronized (lock) {
            delegate.completed(testDescriptor, testResult, completeEvent);
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {
        synchronized (lock) {
            delegate.output(testDescriptor, event);
        }
    }

    @Override
    public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {
        synchronized (lock) {
            delegate.metadata(testDescriptor, event);
        }
    }
}
