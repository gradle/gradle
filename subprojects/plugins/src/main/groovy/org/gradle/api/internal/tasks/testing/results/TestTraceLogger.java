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

package org.gradle.api.internal.tasks.testing.results;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import org.gradle.api.tasks.testing.*;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.StyledTextOutputEvent;

import java.util.List;

public class TestTraceLogger extends AbstractTestLogger {
    private final TestTraceLogging traceLogging;

    public TestTraceLogger(OutputEventListener outputListener, TestTraceLogging traceLogging) {
        super(outputListener);
        this.traceLogging = traceLogging;
    }

    protected void before(TestDescriptor descriptor) {
        if (!shouldLog(descriptor, TraceEvent.STARTED)) { return; }

        logEvent(descriptor, TraceEvent.STARTED);
    }

    protected void after(TestDescriptor descriptor, TestResult result) {
        if (!shouldLog(descriptor, getEvent(result))) { return; }

        logEvent(descriptor, getEvent(result));
    }

    private void logEvent(TestDescriptor descriptor, TraceEvent event) {
        List<String> names = Lists.newArrayList();
        TestDescriptor current = descriptor;
        while (current != null) {
            names.add(current.getName());
            current = current.getParent();
        }

        int minLevel = Math.min(traceLogging.getMinDetailLevel(), names.size() - 1);
        List<String> displayedNames = Lists.reverse(names).subList(minLevel, names.size());
        String path = Joiner.on(" > ").join(displayedNames) + " ";
        print(new StyledTextOutputEvent.Span(path), new StyledTextOutputEvent.Span(getStyle(event), event.toString() + "\n"));
    }

    private TraceEvent getEvent(TestResult result) {
        switch (result.getResultType()) {
            case SUCCESS: return TraceEvent.PASSED;
            case FAILURE: return TraceEvent.FAILED;
            case SKIPPED: return TraceEvent.SKIPPED;
            default: throw new AssertionError();
        }
    }

    private StyledTextOutput.Style getStyle(TraceEvent event) {
        switch (event) {
            case STARTED: return StyledTextOutput.Style.Normal;
            case PASSED: return StyledTextOutput.Style.Identifier;
            case FAILED: return StyledTextOutput.Style.Failure;
            case SKIPPED: return StyledTextOutput.Style.Info;
            default: throw new AssertionError();
        }
    }

//    private String getSymbol(TraceEvent event) {
//        switch (event) {
//            case STARTED: return "STARTED";
//            case PASSED: return "\u2713";
//            case FAILED: return "\u2717";
//            case SKIPPED: return "\u25CB";
//            default: throw new AssertionError();
//        }
//    }

    private boolean shouldLog(TestDescriptor descriptor, TraceEvent event) {
        return traceLogging.isEnabled() && isLoggedDetailLevel(descriptor) && isLoggedEvent(event);
    }

    private boolean isLoggedDetailLevel(TestDescriptor descriptor) {
        int level = getLevel(descriptor);
        return level >= traceLogging.getMinDetailLevel() && level <= traceLogging.getMaxDetailLevel();
    }

    private int getLevel(TestDescriptor descriptor) {
        int level = 0;
        while (descriptor.getParent() != null) {
            level++;
            descriptor = descriptor.getParent();
        }
        return level;
    }

    private boolean isLoggedEvent(TraceEvent event) {
        return traceLogging.getEvents().contains(event);
    }
}
