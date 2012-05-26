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

import org.gradle.api.tasks.testing.logging.*;
import org.gradle.util.GUtil;

import java.util.EnumSet;
import java.util.Set;

public class DefaultTestLogging implements TestLogging {
    private Set<TestLogEvent> events = EnumSet.noneOf(TestLogEvent.class);
    private int minGranularity = -1;
    private int maxGranularity = -1;
    private boolean showExceptions = true;
    private boolean showCauses = true;
    private boolean showStackTraces = true;
    private TestExceptionFormat exceptionFormat = TestExceptionFormat.FULL;
    private Set<TestStackTraceFilter> stackTraceFilters = EnumSet.noneOf(TestStackTraceFilter.class);

    public Set<TestLogEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<TestLogEvent> events) {
        this.events = events;
    }

    public void events(Object... events) {
        setEvents(toEnumSet(TestLogEvent.class, events));
    }

    public int getMinGranularity() {
        return minGranularity;
    }

    public void setMinGranularity(int granularity) {
        minGranularity = granularity;
    }

    public void minGranularity(int granularity) {
        setMinGranularity(granularity);
    }

    public int getMaxGranularity() {
        return maxGranularity;
    }

    public void setMaxGranularity(int granularity) {
        maxGranularity = granularity;
    }

    public void maxGranularity(int granularity) {
        setMaxGranularity(granularity);
    }

    public boolean getShowExceptions() {
        return showExceptions;
    }

    public void setShowExceptions(boolean flag) {
        showExceptions = flag;
    }

    public void showExceptions(boolean flag) {
        setShowExceptions(flag);
    }

    public boolean getShowCauses() {
        return showCauses;
    }

    public void setShowCauses(boolean flag) {
        showCauses = flag;
    }

    public void showCauses(boolean flag) {
        setShowCauses(flag);
    }

    public boolean getShowStackTraces() {
        return showStackTraces;
    }

    public void setShowStackTraces(boolean flag) {
        showStackTraces = flag;
    }

    public void showStackTraces(boolean flag) {
        setShowStackTraces(flag);
    }

    public TestExceptionFormat getExceptionFormat() {
        return exceptionFormat;
    }

    public void setExceptionFormat(TestExceptionFormat exceptionFormat) {
        this.exceptionFormat = exceptionFormat;
    }

    public void exceptionFormat(Object exceptionFormat) {
        setExceptionFormat(toEnum(TestExceptionFormat.class, exceptionFormat));
    }

    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return stackTraceFilters;
    }

    public void setStackTraceFilters(Set<TestStackTraceFilter> filters) {
        stackTraceFilters = filters;
    }

    public void stackTraceFilters(Object... filters) {
        setStackTraceFilters(toEnumSet(TestStackTraceFilter.class, filters));
    }

    public boolean getShowStandardStreams() {
        return events.contains(TestLogEvent.STANDARD_OUT) && events.contains(TestLogEvent.STANDARD_ERROR);
    }

    public void setShowStandardStreams(boolean flag) {
        events.addAll(EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
    }

    public void showStandardStreams(boolean flag) {
        setShowStandardStreams(flag);
    }

    private <T extends Enum<T>> T toEnum(Class<T> enumType, Object value) {
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        if (value instanceof CharSequence) {
            return Enum.valueOf(enumType, GUtil.toConstant(value.toString()));
        }
        throw new IllegalArgumentException(String.format("Cannot convert value '%s' of type '%s' to enum type '%s'",
                value, value.getClass(), enumType));
    }

    private <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> enumType, Object... values) {
        EnumSet<T> result = EnumSet.noneOf(enumType);
        for (Object value : values) {
            result.add(toEnum(enumType, value));
        }
        return result;
    }
}