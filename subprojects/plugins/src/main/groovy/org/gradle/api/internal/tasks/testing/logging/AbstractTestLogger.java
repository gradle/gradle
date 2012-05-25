/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.StyledTextOutputEvent;

public abstract class AbstractTestLogger implements TestListener {
    private final OutputEventListener outputListener;
    private final LogLevel logLevel;

    protected AbstractTestLogger(OutputEventListener outputListener, LogLevel logLevel) {
        this.outputListener = outputListener;
        this.logLevel = logLevel;
    }

    public void beforeSuite(TestDescriptor descriptor) {
        before(descriptor);
    }

    public void afterSuite(TestDescriptor descriptor, TestResult result) {
        after(descriptor, result);
    }

    public void beforeTest(TestDescriptor descriptor) {
        before(descriptor);
    }

    public void afterTest(TestDescriptor descriptor, TestResult result) {
        after(descriptor, result);
    }

    protected void before(TestDescriptor descriptor) {}

    protected void after(TestDescriptor descriptor, TestResult result) {}

    protected void log(StyledTextOutput.Style style, String message) {
        log(new StyledTextOutputEvent.Span(style, message));
    }

    protected void log(StyledTextOutputEvent.Span... spans) {
        outputListener.onOutput(new StyledTextOutputEvent(System.currentTimeMillis(),
                "testLogging", logLevel, spans));
    }
}
