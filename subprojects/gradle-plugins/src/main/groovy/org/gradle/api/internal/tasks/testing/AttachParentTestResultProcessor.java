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

package org.gradle.api.internal.tasks.testing;

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
            suiteStack.addFirst(test.getId());
        }
        processor.started(test, event);
    }

    public void addFailure(Object testId, Throwable result) {
        processor.addFailure(testId, result);
    }

    public void completed(Object testId, TestCompleteEvent event) {
        if (!suiteStack.isEmpty()) {
            if (suiteStack.getFirst().equals(testId)) {
                suiteStack.removeFirst();
            }
            if (suiteStack.contains(testId)) {
                throw new IllegalArgumentException(String.format("Out of order completion event received for test with id '%s'.", testId));
            }
        }
        processor.completed(testId, event);
    }
}
