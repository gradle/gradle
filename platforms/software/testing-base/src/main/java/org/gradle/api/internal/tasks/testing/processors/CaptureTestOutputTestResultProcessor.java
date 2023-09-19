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

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link org.gradle.api.internal.tasks.testing.TestResultProcessor} which redirect stdout and stderr during the
 * execution of a test suite.
 */
public class CaptureTestOutputTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private final TestOutputRedirector outputRedirector;
    private Object rootId;
    private Map<Object, Object> parents = new ConcurrentHashMap<Object, Object>();

    public CaptureTestOutputTestResultProcessor(TestResultProcessor processor, StandardOutputRedirector outputRedirector) {
        this(processor, new TestOutputRedirector(processor, outputRedirector));
    }

    CaptureTestOutputTestResultProcessor(TestResultProcessor processor, TestOutputRedirector outputRedirector) {
        this.processor = processor;
        this.outputRedirector = outputRedirector;
    }

    @Override
    public void started(final TestDescriptorInternal test, TestStartEvent event) {
        processor.started(test, event);

        outputRedirector.setOutputOwner(test.getId());

        if (rootId == null) {
            outputRedirector.startRedirecting();
            rootId = test.getId();
        } else {
            Object parentId = event.getParentId();
            if (parentId == null) {
                //if we don't know the parent we will use the top suite
                //this way we always have and id to attach logging events for
                parentId = rootId;
            }
            parents.put(test.getId(), parentId);
        }
    }

    @Override
    public void completed(Object testId, TestCompleteEvent event) {
        if (testId.equals(rootId)) {
            //when root suite is completed we stop redirecting
            try {
                outputRedirector.stopRedirecting();
            } finally {
                rootId = null;
            }
        } else {
            //when test is completed we should redirect output for the parent
            //so that log events emitted during @AfterSuite, @AfterClass are processed
            Object newOwner = parents.remove(testId);
            outputRedirector.setOutputOwner(newOwner);
        }
        processor.completed(testId, event);
    }

    @Override
    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        processor.failure(testId, result);
    }
}
