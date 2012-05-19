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

/**
 * Configuration options for logging test related information to the console.
 */
public class DefaultTestLogging implements TestLogging {
    private Set<TestLogEvent> events = EnumSet.noneOf(TestLogEvent.class);
    private int minGranularity = 2;
    private int maxGranularity = -1;
    private Set<TestStackTraceFilter> stackTraceFilters = EnumSet.of(TestStackTraceFilter.ENTRY_POINT);
    TestPackageFormat packages = TestPackageFormat.FULL;

    public Set<TestLogEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<TestLogEvent> events) {
        this.events = events;
    }

    public void events(Object... events) {
        this.events.addAll(toEnumSet(TestLogEvent.class, events));
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

    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return stackTraceFilters;
    }

    public void setStackTraceFilters(Set<TestStackTraceFilter> stackTraces) {
        this.stackTraceFilters = stackTraces;
    }

    public void stackTraceFilters(Object... stackTraces) {
        this.stackTraceFilters.addAll(toEnumSet(TestStackTraceFilter.class, stackTraces));
    }

    public TestPackageFormat getPackageFormat() {
        return packages;
    }

    public void setPackageFormat(TestPackageFormat packageFormat) {
        this.packages = packageFormat;
    }

    public void packageFormat(Object packageFormat) {
        this.packages = toEnum(TestPackageFormat.class, packageFormat);
    }

    public boolean getShowStandardStreams() {
        return events.contains(TestLogEvent.STANDARD_OUT) && events.contains(TestLogEvent.STANDARD_ERR);
    }

    public void setShowStandardStreams(boolean showStandardStreams) {
        events(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERR);
    }

    private <T extends Enum<T>> T toEnum(Class<T> enumType, Object value) {
        if (enumType.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof CharSequence) {
            return Enum.valueOf(enumType, GUtil.toConstant(value.toString()));
        }
        throw new IllegalArgumentException(String.format("Cannot convert value '%s' of type '%s' to enum type '%'s"));

    }

    private <T extends Enum<T>> EnumSet<T> toEnumSet(Class<T> enumType, Object... values) {
        EnumSet<T> result = EnumSet.noneOf(enumType);
        for (Object value : values) {
            result.add(toEnum(enumType, value));
        }
        return result;
    }
}