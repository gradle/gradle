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

import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingInputs;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.util.internal.GUtil.toEnum;
import static org.gradle.util.internal.GUtil.toEnumSet;

public class DefaultTestLogging implements TestLogging {
    private static final Set<TestLogEvent> STANDARD_STREAMS = EnumSet.of(TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR);

    private final TestLoggingInputs lazyProperties;

    @Inject
    public DefaultTestLogging(TestLoggingInputs lazyProperties) {
        this.lazyProperties = lazyProperties;
        setDefault(lazyProperties.getEvents(), EnumSet.noneOf(TestLogEvent.class));
        lazyProperties.getMinGranularity().convention(-1);
        lazyProperties.getMaxGranularity().convention(-1);
        lazyProperties.getDisplayGranularity().convention(2);
        lazyProperties.getShowExceptions().convention(true);
        lazyProperties.getShowCauses().convention(true);
        lazyProperties.getShowStackTraces().convention(true);
        lazyProperties.getExceptionFormat().convention(TestExceptionFormat.FULL);
        setDefault(lazyProperties.getStackTraceFilters(), EnumSet.of(TestStackTraceFilter.TRUNCATE));
    }

    /**
     * Sets the default as convention and as value: {@code add()} on a property that has only a convention drops the convention.
     */
    static <T> void setDefault(SetProperty<T> property, Set<T> value) {
        property.convention(value);
        property.set(value);
    }

    @Internal
    public TestLoggingInputs getLazyProperties() {
        return lazyProperties;
    }

    @Override
    @BackedByLazyProperty("lazyProperties.events")
    public Set<TestLogEvent> getEvents() {
        return EagerPropertySupport.conventionKeepingViewOrNull(lazyProperties.getEvents());
    }

    @Override
    public void setEvents(Set<TestLogEvent> events) {
        EagerPropertySupport.setEager(lazyProperties.getEvents(), copyOf(events));
    }

    @Override
    public void setEvents(Iterable<?> events) {
        lazyProperties.getEvents().set(toEnumSet(TestLogEvent.class, events));
    }

    @Override
    public void events(Object... events) {
        EagerPropertySupport.appendAll(lazyProperties.getEvents(), toEnumSet(TestLogEvent.class, events));
    }

    @Override
    @BackedByLazyProperty("lazyProperties.minGranularity")
    public int getMinGranularity() {
        return lazyProperties.getMinGranularity().get();
    }

    @Override
    public void setMinGranularity(int granularity) {
        lazyProperties.getMinGranularity().set(granularity);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.maxGranularity")
    public int getMaxGranularity() {
        return lazyProperties.getMaxGranularity().get();
    }

    @Override
    public void setMaxGranularity(int granularity) {
        lazyProperties.getMaxGranularity().set(granularity);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.displayGranularity")
    public int getDisplayGranularity() {
        return lazyProperties.getDisplayGranularity().get();
    }

    @Override
    public void setDisplayGranularity(int granularity) {
        lazyProperties.getDisplayGranularity().set(granularity);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.showExceptions")
    public boolean getShowExceptions() {
        return lazyProperties.getShowExceptions().get();
    }

    @Override
    public void setShowExceptions(boolean flag) {
        lazyProperties.getShowExceptions().set(flag);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.showCauses")
    public boolean getShowCauses() {
        return lazyProperties.getShowCauses().get();
    }

    @Override
    public void setShowCauses(boolean flag) {
        lazyProperties.getShowCauses().set(flag);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.showStackTraces")
    public boolean getShowStackTraces() {
        return lazyProperties.getShowStackTraces().get();
    }

    @Override
    public void setShowStackTraces(boolean flag) {
        lazyProperties.getShowStackTraces().set(flag);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.exceptionFormat")
    public TestExceptionFormat getExceptionFormat() {
        return lazyProperties.getExceptionFormat().getOrNull();
    }

    @Override
    public void setExceptionFormat(TestExceptionFormat exceptionFormat) {
        setExceptionFormat((Object) exceptionFormat);
    }

    @Override
    public void setExceptionFormat(Object exceptionFormat) {
        EagerPropertySupport.setEager(lazyProperties.getExceptionFormat(), toEnum(TestExceptionFormat.class, exceptionFormat));
    }

    @Override
    @BackedByLazyProperty("lazyProperties.stackTraceFilters")
    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return EagerPropertySupport.conventionKeepingViewOrNull(lazyProperties.getStackTraceFilters());
    }

    @Override
    public void setStackTraceFilters(Set<TestStackTraceFilter> stackTraceFilters) {
        EagerPropertySupport.setEager(lazyProperties.getStackTraceFilters(), copyOf(stackTraceFilters));
    }

    @Override
    public void setStackTraceFilters(Iterable<?> filters) {
        lazyProperties.getStackTraceFilters().set(toEnumSet(TestStackTraceFilter.class, filters));
    }

    @Override
    public void stackTraceFilters(Object... filters) {
        EagerPropertySupport.appendAll(lazyProperties.getStackTraceFilters(), toEnumSet(TestStackTraceFilter.class, filters));
    }

    // Derived from the events, as before, so it has no lazy property of its own
    @Override
    public boolean getShowStandardStreams() {
        Set<TestLogEvent> events = getEvents();
        return events != null && events.containsAll(STANDARD_STREAMS);
    }

    @Override
    public TestLogging setShowStandardStreams(boolean flag) {
        if (flag) {
            EagerPropertySupport.appendAll(lazyProperties.getEvents(), STANDARD_STREAMS);
        } else {
            getEvents().removeAll(STANDARD_STREAMS);
        }
        return this;
    }

    // The setters copied the set before, so later changes to the argument do not reach the property
    @Nullable
    private static <T> Set<T> copyOf(@Nullable Set<T> values) {
        return values == null ? null : new LinkedHashSet<>(values);
    }
}
