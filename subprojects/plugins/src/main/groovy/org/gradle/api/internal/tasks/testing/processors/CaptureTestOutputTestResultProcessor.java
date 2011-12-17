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

/**
 * A {@link org.gradle.api.internal.tasks.testing.TestResultProcessor} which redirect stdout and stderr during the
 * execution of a test suite.
 */
public class CaptureTestOutputTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private final StandardOutputRedirector outputRedirector;
    private Object suiteId;

    public CaptureTestOutputTestResultProcessor(TestResultProcessor processor, StandardOutputRedirector outputRedirector) {
        this.processor = processor;
        this.outputRedirector = outputRedirector;
    }

    public void started(final TestDescriptorInternal test, TestStartEvent event) {
        processor.started(test, event);

        //should redirect output for every particular test
        redirectOutputFor(test.getId());

        //currently our test reports include std out/err per test class (aka suite) not per test method (aka test)
        //for historical reasons. Therefore we only start/stop redirector per suite.
        if (suiteId != null) {
            return;
        }
        suiteId = test.getId();
        outputRedirector.start();
    }

    public void completed(Object testId, TestCompleteEvent event) {
        if (testId.equals(suiteId)) {
            //when suite is completed we no longer redirect for this suite
            try {
                outputRedirector.stop();
            } finally {
                suiteId = null;
            }
        } else {
            //when test is completed, should redirect output for the 'suite' to log things like @AfterSuite, etc.
            redirectOutputFor(suiteId);
        }
        processor.completed(testId, event);
    }

    private void redirectOutputFor(final Object testId) {
        outputRedirector.redirectStandardOutputTo(new StdOutForwarder(testId));
        outputRedirector.redirectStandardErrorTo(new StdErrForwarder(testId));
    }

    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }


    class StdOutForwarder implements StandardOutputListener {
        private final Object testId;

        public StdOutForwarder(Object testId) {
            this.testId = testId;
        }

        public void onOutput(CharSequence output) {
            processor.output(testId, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, output.toString()));
        }
    }

    class StdErrForwarder implements StandardOutputListener {
        private final Object testId;

        public StdErrForwarder(Object testId) {
            this.testId = testId;
        }

        public void onOutput(CharSequence output) {
            processor.output(testId, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, output.toString()));
        }
    }
}
