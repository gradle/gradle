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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestOutputEvent;

public class AttachParentTestResultProcessor implements TestResultProcessor {
    private Object rootId;
    private final TestResultProcessor processor;

    public AttachParentTestResultProcessor(TestResultProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void started(TestDescriptorInternal test, TestStartEvent event) {
        if (rootId == null) {
            assert test.isComposite();
            rootId = test.getId();
        } else if (event.getParentId() == null) {
            event = event.withParentId(rootId);
        }
        processor.started(test, event);
    }

    @Override
    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    @Override
    public void completed(Object testId, TestCompleteEvent event) {
        if (testId.equals(rootId)) {
            rootId = null;
        }
        processor.completed(testId, event);
    }
}
