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

import java.util.LinkedList;

public class AttachParentTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private final LinkedList<Object> suiteStack = new LinkedList<Object>();

    public AttachParentTestResultProcessor(TestResultProcessor processor) {
        this.processor = processor;
    }

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        if (event.getParentId() == null && !suiteStack.isEmpty()) {
            event.setParentId(suiteStack.getFirst());
        }
        if (test.isComposite()) {
            if (suiteStack.contains(test.getId())) {
                throw new IllegalArgumentException(String.format("Multiple start events received for test with id '%s'.", test.getId()));
            }
            suiteStack.addFirst(test.getId());
        }
        processor.started(test, event);
    }

    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }

    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    public void completed(Object testId, TestCompleteEvent event) {
        int pos = suiteStack.indexOf(testId);
        if (pos >= 0) {
            // Implicitly stop everything up to the given test
            suiteStack.subList(0, pos + 1).clear();
        }
        processor.completed(testId, event);
    }
}
