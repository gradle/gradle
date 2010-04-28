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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.util.LineBufferingOutputStream;

import java.io.IOException;
import java.io.PrintStream;

public class CaptureTestOutputTestResultProcessor implements TestResultProcessor {
    private final TestResultProcessor processor;
    private PrintStream out;
    private PrintStream err;
    private Object suite;

    public CaptureTestOutputTestResultProcessor(TestResultProcessor processor) {
        this.processor = processor;
    }

    public void started(final TestDescriptorInternal test, TestStartEvent event) {
        processor.started(test, event);
        suite = test.getId();
        out = System.out;
        err = System.err;
        System.setOut(new PrintStream(new LineBufferingOutputStream() {
            @Override
            protected void writeLine(String message) throws IOException {
                processor.output(suite, new TestOutputEvent(TestOutputEvent.Destination.StdOut, String.format("%s%n",
                        message)));
            }
        }));
        System.setErr(new PrintStream(new LineBufferingOutputStream() {
            @Override
            protected void writeLine(String message) throws IOException {
                processor.output(suite, new TestOutputEvent(TestOutputEvent.Destination.StdErr, String.format("%s%n",
                        message)));
            }
        }));
    }

    public void completed(Object testId, TestCompleteEvent event) {
        processor.completed(testId, event);
        if (testId.equals(suite)) {
            try {
                PrintStream capturingOut = System.out;
                PrintStream capturingErr = System.err;
                System.setOut(out);
                System.setErr(err);
                capturingOut.close();
                capturingErr.close();
            } finally {
                suite = null;
                out = null;
                err = null;
            }
        }
    }

    public void output(Object testId, TestOutputEvent event) {
        processor.output(testId, event);
    }

    public void failure(Object testId, Throwable result) {
        processor.failure(testId, result);
    }
}
