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
import org.gradle.logging.StandardOutputRedirector;

/**
 * A {@link org.gradle.api.internal.tasks.testing.TestResultProcessor} which redirect stdout and stderr during the
 * execution of a test suite.
 */
public class CaptureTestOutputTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private final StandardOutputRedirector outputRedirector;
    private Object suite;

    public CaptureTestOutputTestResultProcessor(TestResultProcessor processor, StandardOutputRedirector outputRedirector) {
        this.processor = processor;
        this.outputRedirector = outputRedirector;
    }

    public void started(final TestDescriptorInternal test, TestStartEvent event) {
        processor.started(test, event);
        if (suite != null) {
            return;
        }
        suite = test.getId();
        outputRedirector.redirectStandardOutputTo(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                processor.output(suite, new TestOutputEvent(TestOutputEvent.Destination.StdOut, output.toString()));
            }
        });
        outputRedirector.redirectStandardErrorTo(new StandardOutputListener() {
            public void onOutput(CharSequence output) {
                processor.output(suite, new TestOutputEvent(TestOutputEvent.Destination.StdErr, output.toString()));
            }
        });
        outputRedirector.start();
    }

    public void completed(Object testId, TestCompleteEvent event) {
        if (testId.equals(suite)) {
            try {
                outputRedirector.stop();
            } finally {
                suite = null;
            }
        }
        processor.completed(testId, event);
    }

    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }
}
