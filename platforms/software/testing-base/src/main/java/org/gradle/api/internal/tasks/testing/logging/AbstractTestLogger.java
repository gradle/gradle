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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Provides ability to log test events. Not thread safe.
 */
public abstract class AbstractTestLogger {
    private final StyledTextOutputFactory textOutputFactory;
    private final LogLevel logLevel;
    private final int displayGranularity;
    private TestDescriptor lastSeenTestDescriptor;
    private TestLogEvent lastSeenTestEvent;

    protected AbstractTestLogger(StyledTextOutputFactory textOutputFactory, LogLevel logLevel, int displayGranularity) {
        this.textOutputFactory = textOutputFactory;
        this.logLevel = logLevel;
        this.displayGranularity = displayGranularity;
    }

    protected void logEvent(TestDescriptor descriptor, TestLogEvent event) {
        logEvent(descriptor, event, null);
    }

    protected void logEvent(TestDescriptor descriptor, TestLogEvent event, @Nullable String details) {
        StyledTextOutput output = textOutputFactory.create("TestEventLogger", logLevel);
        if (!descriptor.equals(lastSeenTestDescriptor) || event != lastSeenTestEvent) {
            output.println().append(getEventPath(descriptor));
            output.withStyle(getStyle(event)).println(event.toString());
        }
        lastSeenTestDescriptor = descriptor;
        lastSeenTestEvent = event;
        if (details != null) {
            output.append(TextUtil.toPlatformLineSeparators(details));
        }
    }

    private String getEventPath(TestDescriptor descriptor) {
        List<String> names = Lists.newArrayList();
        TestDescriptor current = descriptor;
        while (current != null) {
            if (isAtomicTestWithNoTestClassInAncestorDescriptors(current)) {
                // This deals with the fact that in TestNG, there are no class-level events,
                // but we nevertheless want to see the class name. We use "." rather than
                // " > " as a separator to make it clear that the class is not a separate
                // level. This matters when configuring granularity.
                names.add(current.getClassName() + "." + current.getDisplayName());
            } else {
                names.add(current.getDisplayName());
            }
            current = current.getParent();
        }

        int effectiveDisplayGranularity = displayGranularity == -1
                ? names.size() - 1 : Math.min(displayGranularity, names.size() - 1);
        List<String> displayedNames = Lists.reverse(names).subList(effectiveDisplayGranularity, names.size());
        return Joiner.on(" > ").join(displayedNames) + " ";
    }

    private boolean isAtomicTestWithNoTestClassInAncestorDescriptors(TestDescriptor current) {
        boolean isAtomicTestWhoseParentIsNotTheTestClass = !current.isComposite() && current.getClassName() != null &&
            (current.getParent() == null || !current.getClassName().equals(current.getParent().getName()));
        if (!isAtomicTestWhoseParentIsNotTheTestClass) {
            return false;
        }
        if (current.getParent() != null) {
            // If there is a parent, then check that the grandparent has no test class.
            // There is currently no requirement to check further up the descriptor hierarchy than this.
            return current.getParent().getParent() == null || current.getParent().getParent().getClassName() == null;
        }
        // If there is no parent, then there's no need to check if the grandparent has a test class.
        return true;
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
