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
import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.logging.internal.StyledTextOutputEvent;

import java.util.List;

public abstract class AbstractTestLogger {
    private final OutputEventListener outputListener;
    private final LogLevel logLevel;

    protected AbstractTestLogger(OutputEventListener outputListener, LogLevel logLevel) {
        this.outputListener = outputListener;
        this.logLevel = logLevel;
    }

    protected void logEvent(TestDescriptor descriptor, TestLogEvent event) {
        logEvent(descriptor, event, null);
    }

    protected void logEvent(TestDescriptor descriptor, TestLogEvent event, @Nullable String details) {
        List<String> names = Lists.newArrayList();
        TestDescriptor current = descriptor;
        while (current != null) {
            names.add(current.getName());
            current = current.getParent();
        }

        // Here we assume that the first two names are _always_ the suite for the whole
        // test run and the suite for the test JVM. Only when events of that granularity
        // are logged do we show these names. If it turns out that our assumption is incorrect
        // or hard coding this behavior is not good enough, we could make this configurable.
        // But I'd rather not add another obscure configuration option unless really necessary.
        int minDisplayedName = Math.min(2, names.size() - 1);

        List<String> displayedNames = Lists.reverse(names).subList(minDisplayedName, names.size());
        String path = Joiner.on(" > ").join(displayedNames) + " ";
        String detailText = details == null ? "\n" : "\n" + details + "\n";
        log(new StyledTextOutputEvent.Span(path), new StyledTextOutputEvent.Span(getStyle(event),
                event.toString()), new StyledTextOutputEvent.Span(detailText));
    }

    private void log(StyledTextOutputEvent.Span... spans) {
        outputListener.onOutput(new StyledTextOutputEvent(System.currentTimeMillis(),
                "testLogging", logLevel, spans));
    }

    private StyledTextOutput.Style getStyle(TestLogEvent event) {
        switch (event) {
            case PASSED: return StyledTextOutput.Style.Identifier;
            case FAILED: return StyledTextOutput.Style.Failure;
            case SKIPPED: return StyledTextOutput.Style.Info;
            default: return StyledTextOutput.Style.Normal;
        }
    }
}
