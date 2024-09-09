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
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.TextUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * Console logger for test events.
 */
public class TestEventLogger extends AbstractTestLogger implements TestListener, TestOutputListener {
    private static final String INDENT = "    ";

    private final TestExceptionFormatter exceptionFormatter;
    private final int minGranularity;
    private final int maxGranularity;
    private final Set<TestLogEvent> testLogEvents;
    private final boolean showExceptions;

    public TestEventLogger(
        StyledTextOutputFactory textOutputFactory,
        LogLevel logLevel,
        TestExceptionFormatter exceptionFormatter,
        boolean showExceptions,
        int minGranularity,
        int maxGranularity,
        int displayGranularity,
        Boolean showStandardStreams,
        Set<TestLogEvent> testLogEvents
    ) {
        super(textOutputFactory, logLevel, displayGranularity);
        this.exceptionFormatter = exceptionFormatter;
        this.showExceptions = showExceptions;
        this.minGranularity = minGranularity;
        this.maxGranularity = maxGranularity;
        this.testLogEvents = calculateTestLogEvents(showStandardStreams, testLogEvents);
    }

    private static Set<TestLogEvent> calculateTestLogEvents(Boolean showStandardStreams, Set<TestLogEvent> events) {
        if (showStandardStreams == null) {
            return events;
        }

        Set<TestLogEvent> calculatedEvents = events.isEmpty() ? EnumSet.noneOf(TestLogEvent.class) : EnumSet.copyOf(events);
        if (showStandardStreams) {
            calculatedEvents.addAll(EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        } else {
            calculatedEvents.removeAll(EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        }
        return calculatedEvents;
    }

    @Override
    public void beforeSuite(TestDescriptor descriptor) {
        before(descriptor);
    }

    @Override
    public void afterSuite(TestDescriptor descriptor, TestResult result) {
        after(descriptor, result);
    }

    @Override
    public void beforeTest(TestDescriptor descriptor) {
        before(descriptor);
    }

    @Override
    public void afterTest(TestDescriptor descriptor, TestResult result) {
        after(descriptor, result);
    }

    @Override
    public void onOutput(TestDescriptor descriptor, TestOutputEvent outputEvent) {
        if (outputEvent.getDestination() == TestOutputEvent.Destination.StdOut
                && isLoggedEventType(TestLogEvent.STANDARD_OUT)) {
            logEvent(descriptor, TestLogEvent.STANDARD_OUT, TextUtil.indent(outputEvent.getMessage(), INDENT) + "\n");
        } else if (outputEvent.getDestination() == TestOutputEvent.Destination.StdErr
                && isLoggedEventType(TestLogEvent.STANDARD_ERROR)) {
            logEvent(descriptor, TestLogEvent.STANDARD_ERROR, TextUtil.indent(outputEvent.getMessage(), INDENT) + "\n");
        }
    }

    private void before(TestDescriptor descriptor) {
        if (shouldLogEvent(descriptor, TestLogEvent.STARTED)) {
            logEvent(descriptor, TestLogEvent.STARTED);
        }
    }

    private void after(TestDescriptor descriptor, TestResult result) {
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

    private boolean shouldLogEvent(TestDescriptor descriptor, TestLogEvent event) {
        return isLoggedGranularity(descriptor) && isLoggedEventType(event);
    }

    private boolean shouldLogExceptions(TestResult result) {
        return showExceptions && !result.getExceptions().isEmpty();
    }

    private boolean isLoggedGranularity(TestDescriptor descriptor) {
        int level = getLevel(descriptor);
        return ((minGranularity == -1 && !descriptor.isComposite()) ||
            (minGranularity > -1 && level >= minGranularity)) &&
            (maxGranularity == -1 || level <= maxGranularity);
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
        return testLogEvents.contains(event);
    }
}
