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

import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;

import java.util.EnumSet;
import java.util.Set;

import static org.gradle.util.internal.GUtil.toEnum;
import static org.gradle.util.internal.GUtil.toEnumSet;

public class DefaultTestLogging implements TestLogging {
    private Set<TestLogEvent> events = EnumSet.noneOf(TestLogEvent.class);
    private int minGranularity = -1;
    private int maxGranularity = -1;
    private int displayGranularity = 2;
    private boolean showExceptions = true;
    private boolean showCauses = true;
    private boolean showStackTraces = true;
    private TestExceptionFormat exceptionFormat = TestExceptionFormat.FULL;
    private Set<TestStackTraceFilter> stackTraceFilters = EnumSet.of(TestStackTraceFilter.TRUNCATE);

    @Override
    public Set<TestLogEvent> getEvents() {
        return events;
    }

    @Override
    public void setEvents(Set<TestLogEvent> events) {
        this.events = EnumSet.copyOf(events);
    }

    @Override
    public void setEvents(Iterable<?> events) {
        this.events = toEnumSet(TestLogEvent.class, events);
    }

    @Override
    public void events(Object... events) {
        this.events.addAll(toEnumSet(TestLogEvent.class, events));
    }

    @Override
    public int getMinGranularity() {
        return minGranularity;
    }

    @Override
    public void setMinGranularity(int granularity) {
        minGranularity = granularity;
    }

    @Override
    public int getMaxGranularity() {
        return maxGranularity;
    }

    @Override
    public void setMaxGranularity(int granularity) {
        maxGranularity = granularity;
    }

    @Override
    public int getDisplayGranularity() {
        return displayGranularity;
    }

    @Override
    public void setDisplayGranularity(int granularity) {
        displayGranularity = granularity;
    }

    @Override
    public boolean getShowExceptions() {
        return showExceptions;
    }

    @Override
    public void setShowExceptions(boolean flag) {
        showExceptions = flag;
    }

    @Override
    public boolean getShowCauses() {
        return showCauses;
    }

    @Override
    public void setShowCauses(boolean flag) {
        showCauses = flag;
    }

    @Override
    public boolean getShowStackTraces() {
        return showStackTraces;
    }

    @Override
    public void setShowStackTraces(boolean flag) {
        showStackTraces = flag;
    }

    @Override
    public TestExceptionFormat getExceptionFormat() {
        return exceptionFormat;
    }

    @Override
    public void setExceptionFormat(TestExceptionFormat exceptionFormat) {
        setExceptionFormat((Object) exceptionFormat);
    }

    @Override
    public void setExceptionFormat(Object exceptionFormat) {
        this.exceptionFormat = toEnum(TestExceptionFormat.class, exceptionFormat);
    }

    @Override
    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return stackTraceFilters;
    }

    @Override
    public void setStackTraceFilters(Set<TestStackTraceFilter> stackTraceFilters) {
        this.stackTraceFilters = EnumSet.copyOf(stackTraceFilters);
    }

    @Override
    public void setStackTraceFilters(Iterable<?> filters) {
        stackTraceFilters = toEnumSet(TestStackTraceFilter.class, filters);
    }

    @Override
    public void stackTraceFilters(Object... filters) {
        stackTraceFilters.addAll(toEnumSet(TestStackTraceFilter.class, filters));
    }

    @Override
    public boolean getShowStandardStreams() {
        return events.contains(TestLogEvent.STANDARD_OUT) && events.contains(TestLogEvent.STANDARD_ERROR);
    }

    @Override
    public TestLogging setShowStandardStreams(boolean flag) {
        if (flag) {
            events.addAll(EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        } else {
            events.removeAll(EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        }
        return this;
    }

}
