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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.logging.StandardOutputRedirector;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link org.gradle.api.internal.tasks.testing.TestResultProcessor} which redirect stdout and stderr during the
 * execution of a test suite.
 */
public class CaptureTestOutputTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private final StandardOutputRedirector outputRedirector;
    private Object rootId;
    private Map<Object, Object> parents = new HashMap<Object, Object>();

    public CaptureTestOutputTestResultProcessor(TestResultProcessor processor, StandardOutputRedirector outputRedirector) {
        this.processor = processor;
        this.outputRedirector = outputRedirector;
    }

    public void started(final TestDescriptorInternal test, TestStartEvent event) {
        processor.started(test, event);

        //should redirect output for every particular test
        redirectOutputFor(test.getId());

        if (rootId == null) {
            rootId = test.getId();
            outputRedirector.start();
        } else {
            Object parentId = event.getParentId(); //TODO SF coverage
            if (parentId == null) {
                //if we don't know the parent we will use the top suite
                //this way we always have and id to attach logging events for
                parentId = rootId;
            }
            parents.put(test.getId(), parentId);
        }
    }

    public void completed(Object testId, TestCompleteEvent event) {
        if (testId.equals(rootId)) {
            //when root suite is completed we stop redirecting
            try {
                outputRedirector.stop();
            } finally {
                rootId = null;
            }
        } else {
            Object parent = parents.remove(testId);
            //when test is completed we should redirect output for the parent
            //so that log events emitted during @AfterSuite, @AfterClass are processed
            redirectOutputFor(parent);
        }
        processor.completed(testId, event);
    }

    private void redirectOutputFor(final Object testId) {
        outputRedirector.redirectStandardOutputTo(new Forwarder(testId, TestOutputEvent.Destination.StdOut));
        outputRedirector.redirectStandardErrorTo(new Forwarder(testId, TestOutputEvent.Destination.StdErr));
    }

    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }

    class Forwarder implements StandardOutputListener {
        private final Object testId;
        private final TestOutputEvent.Destination dest;

        public Forwarder(Object testId, TestOutputEvent.Destination dest) {
            this.testId = testId;
            this.dest = dest;
        }

        public void onOutput(CharSequence output) {
            if (testId == null) {
                throw new RuntimeException("Unable send output event from test executor. Please report this problem. Destination: " + dest + ", event: " + output.toString());
            }
            processor.output(testId, new DefaultTestOutputEvent(dest, output.toString()));
        }
    }
}
