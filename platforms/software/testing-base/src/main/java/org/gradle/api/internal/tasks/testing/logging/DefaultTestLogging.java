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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;
import org.gradle.util.internal.GUtil;

import java.util.EnumSet;
import java.util.Set;

import static org.gradle.util.internal.GUtil.toEnumSet;

public abstract class DefaultTestLogging implements TestLogging {

    public DefaultTestLogging() {
        getEvents().convention(EnumSet.noneOf(TestLogEvent.class));
        getMinGranularity().convention(-1);
        getMaxGranularity().convention(-1);
        getDisplayGranularity().convention(2);
        getShowExceptions().convention(true);
        getShowCauses().convention(true);
        getShowStackTraces().convention(true);
        getExceptionFormat().convention(TestExceptionFormat.FULL);
        getStackTraceFilters().convention(EnumSet.of(TestStackTraceFilter.TRUNCATE));
    }

    @Override
    public abstract SetProperty<TestLogEvent> getEvents();

    @Override
    public void setEvents(Set<TestLogEvent> events) {
        getEvents().set(events);
    }

    @Override
    public void setEvents(Iterable<?> events) {
        getEvents().set(toEnumSet(TestLogEvent.class, events));
    }

    @Override
    public void events(Object... events) {
        getEvents().addAll(toEnumSet(TestLogEvent.class, events));
    }

    @Override
    public abstract Property<Integer> getMinGranularity();

    @Override
    public void setMinGranularity(int minGranularity) {
        getMinGranularity().set(minGranularity);
    }

    @Override
    public abstract Property<Integer> getMaxGranularity();

    @Override
    public void setMaxGranularity(int maxGranularity) {
        getMaxGranularity().set(maxGranularity);
    }

    @Override
    public abstract Property<Integer> getDisplayGranularity();

    @Override
    public void setDisplayGranularity(int displayGranularity) {
        getDisplayGranularity().set(displayGranularity);
    }

    @Override
    public abstract Property<Boolean> getShowExceptions();

    @Override
    public void setShowExceptions(boolean showExceptions) {
        getShowExceptions().set(showExceptions);
    }

    @Override
    public abstract Property<Boolean> getShowCauses();

    @Override
    public void setShowCauses(boolean showCauses) {
        getShowCauses().set(showCauses);
    }

    @Override
    public abstract Property<Boolean> getShowStackTraces();

    @Override
    public void setShowStackTraces(boolean showStackTraces) {
        getShowStackTraces().set(showStackTraces);
    }

    @Override
    public abstract Property<TestExceptionFormat> getExceptionFormat();

    @Override
    public void setExceptionFormat(TestExceptionFormat exceptionFormat) {
        getExceptionFormat().set(exceptionFormat);
    }

    @Override
    public void setExceptionFormat(Object exceptionFormat) {
        getExceptionFormat().set(GUtil.toEnum(TestExceptionFormat.class, exceptionFormat));
    }

    @Override
    public abstract SetProperty<TestStackTraceFilter> getStackTraceFilters();

    @Override
    public void setStackTraceFilters(Set<TestStackTraceFilter> stackTraces) {
        getStackTraceFilters().set(stackTraces);
    }

    @Override
    public void setStackTraceFilters(Iterable<?> stackTraces) {
        getStackTraceFilters().set(toEnumSet(TestStackTraceFilter.class, stackTraces));
    }

    @Override
    public void stackTraceFilters(Object... filters) {
        getStackTraceFilters().addAll(toEnumSet(TestStackTraceFilter.class, filters));
    }

    @Override
    public abstract Property<Boolean> getShowStandardStreams();

    @Override
    public TestLogging setShowStandardStreams(boolean showStandardStreams) {
        getShowStandardStreams().set(showStandardStreams);
        return this;
    }
}
