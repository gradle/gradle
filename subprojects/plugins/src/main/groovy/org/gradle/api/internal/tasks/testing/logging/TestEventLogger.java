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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import org.gradle.api.Nullable;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.*;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.StyledTextOutputEvent;
import org.gradle.util.TextUtil;

import java.util.List;

/**
 * Logger for test events.
 */
public class TestEventLogger extends AbstractTestLogger implements TestOutputListener {
    private static final String INDENT = "    ";

    private final TestLogging testLogging;
    private final TestExceptionFormatter exceptionFormatter;

    public TestEventLogger(OutputEventListener outputListener, LogLevel logLevel, TestLogging testLogging, TestExceptionFormatter exceptionFormatter) {
        super(outputListener, logLevel);
        this.testLogging = testLogging;
        this.exceptionFormatter = exceptionFormatter;
    }

    public void onOutput(TestDescriptor descriptor, TestOutputEvent outputEvent) {
        if (outputEvent.getDestination() == TestOutputEvent.Destination.StdOut
                && shouldLogStandardStreamEvent(TestLogEvent.STANDARD_OUT)) {
            logEvent(descriptor, TestLogEvent.STANDARD_OUT, TextUtil.indent(outputEvent.getMessage(), INDENT) + "\n");
        } else if (outputEvent.getDestination() == TestOutputEvent.Destination.StdErr
                && shouldLogStandardStreamEvent(TestLogEvent.STANDARD_ERROR)) {
            logEvent(descriptor, TestLogEvent.STANDARD_ERROR, TextUtil.indent(outputEvent.getMessage(), INDENT) + "\n");
        }
    }

    protected void before(TestDescriptor descriptor) {
        if (shouldLogEvent(descriptor, TestLogEvent.STARTED)) {
            logEvent(descriptor, TestLogEvent.STARTED);
        }
    }

    protected void after(TestDescriptor descriptor, TestResult result) {
        TestLogEvent event = getEvent(result);

        if (shouldLogEvent(descriptor, event)) {
            String details = shouldLogExceptions(result) ? exceptionFormatter.format(descriptor, result.getExceptions()) : null;
            logEvent(descriptor, event, details);
        }
    }

    private TestLogEvent getEvent(TestResult result) {
        switch (result.getResultType()) {
            case SUCCESS: return TestLogEvent.PASSED;
            case FAILURE: return TestLogEvent.FAILED;
            case SKIPPED: return TestLogEvent.SKIPPED;
            default: throw new AssertionError();
        }
    }

    private void logEvent(TestDescriptor descriptor, TestLogEvent event) {
        logEvent(descriptor, event, null);
    }

    private void logEvent(TestDescriptor descriptor, TestLogEvent event, @Nullable String details) {
        List<String> names = Lists.newArrayList();
        TestDescriptor current = descriptor;
        while (current != null) {
            names.add(Strings.isNullOrEmpty(current.getName()) ? "Test Run" : current.getName());
            current = current.getParent();
        }

        // TODO: figure out what to do instead of hard-coding 2 (additional config value)?
        int minDisplayedName = Math.min(2, names.size() - 1);
        List<String> displayedNames = Lists.reverse(names).subList(minDisplayedName, names.size());
        String path = Joiner.on(" > ").join(displayedNames) + " ";
        String detailText = details == null ? "\n" : "\n" + details + "\n";
        log(new StyledTextOutputEvent.Span(path), new StyledTextOutputEvent.Span(getStyle(event),
                event.toString()), new StyledTextOutputEvent.Span(detailText));
    }

    private StyledTextOutput.Style getStyle(TestLogEvent event) {
        switch (event) {
            case PASSED: return StyledTextOutput.Style.Identifier;
            case FAILED: return StyledTextOutput.Style.Failure;
            case SKIPPED: return StyledTextOutput.Style.Info;
            default: return StyledTextOutput.Style.Normal;
        }
    }

    private boolean shouldLogEvent(TestDescriptor descriptor, TestLogEvent event) {
        return isLoggedGranularity(descriptor) && isLoggedEventType(event);
    }

    private boolean shouldLogExceptions(TestResult result) {
        return testLogging.getShowExceptions() && !result.getExceptions().isEmpty();
    }

    private boolean shouldLogStandardStreamEvent(TestLogEvent event) {
        return isLoggedEventType(event);
    }

    private boolean isLoggedGranularity(TestDescriptor descriptor) {
        int level = getLevel(descriptor);
        return ((testLogging.getMinGranularity() == -1 && !descriptor.isComposite())
                || testLogging.getMinGranularity() > -1 && level >= testLogging.getMinGranularity())
            && (testLogging.getMaxGranularity() == -1 || level <= testLogging.getMaxGranularity());
    }

    private int getLevel(TestDescriptor descriptor) {
        int level = 0;
        while (descriptor.getParent() != null) {
            level++;
            descriptor = descriptor.getParent();
        }
        return level;
    }

    private boolean isLoggedEventType(TestLogEvent event) {
        return testLogging.getEvents().contains(event);
    }
}
