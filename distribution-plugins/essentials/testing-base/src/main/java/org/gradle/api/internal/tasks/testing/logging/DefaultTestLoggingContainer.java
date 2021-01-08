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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;
import org.gradle.internal.reflect.Instantiator;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class DefaultTestLoggingContainer implements TestLoggingContainer {
    private final Map<LogLevel, TestLogging> perLevelTestLogging = Maps.newEnumMap(LogLevel.class);

    public DefaultTestLoggingContainer(Instantiator instantiator) {
        for (LogLevel level: LogLevel.values()) {
            perLevelTestLogging.put(level, instantiator.newInstance(DefaultTestLogging.class));
        }

        setEvents(EnumSet.of(TestLogEvent.FAILED));
        setExceptionFormat(TestExceptionFormat.SHORT);

        getInfo().setEvents(EnumSet.of(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        getInfo().setStackTraceFilters(EnumSet.of(TestStackTraceFilter.TRUNCATE));

        getDebug().setEvents(EnumSet.allOf(TestLogEvent.class));
        getDebug().setMinGranularity(0);
        getDebug().setStackTraceFilters(EnumSet.noneOf(TestStackTraceFilter.class));
    }

    @Override
    public TestLogging getDebug() {
        return perLevelTestLogging.get(LogLevel.DEBUG);
    }

    @Override
    public void setDebug(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.DEBUG, logging);
    }

    @Override
    public void debug(Action<TestLogging> action) {
        action.execute(getDebug());
    }

    @Override
    public TestLogging getInfo() {
        return perLevelTestLogging.get(LogLevel.INFO);
    }

    @Override
    public void setInfo(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.INFO, logging);
    }

    @Override
    public void info(Action<TestLogging> action) {
        action.execute(getInfo());
    }

    @Override
    public TestLogging getLifecycle() {
        return perLevelTestLogging.get(LogLevel.LIFECYCLE);
    }

    @Override
    public void setLifecycle(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.LIFECYCLE, logging);
    }

    @Override
    public void lifecycle(Action<TestLogging> action) {
        action.execute(getLifecycle());
    }

    @Override
    public TestLogging getWarn() {
        return perLevelTestLogging.get(LogLevel.WARN);
    }

    @Override
    public void setWarn(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.WARN, logging);
    }

    @Override
    public void warn(Action<TestLogging> action) {
        action.execute(getWarn());
    }

    @Override
    public TestLogging getQuiet() {
        return perLevelTestLogging.get(LogLevel.QUIET);
    }

    @Override
    public void setQuiet(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.QUIET, logging);
    }

    @Override
    public void quiet(Action<TestLogging> action) {
        action.execute(getQuiet());
    }

    @Override
    public TestLogging getError() {
        return perLevelTestLogging.get(LogLevel.ERROR);
    }

    @Override
    public void setError(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.ERROR, logging);
    }

    @Override
    public void error(Action<TestLogging> action) {
        action.execute(getError());
    }

    @Override
    public Set<TestLogEvent> getEvents() {
        return getDefaultTestLogging().getEvents();
    }

    @Override
    public void setEvents(Set<TestLogEvent> events) {
        getDefaultTestLogging().setEvents(events);
    }

    @Override
    public void setEvents(Iterable<?> events) {
        getDefaultTestLogging().setEvents(events);
    }

    @Override
    public void events(Object... events) {
        getDefaultTestLogging().events(events);
    }

    @Override
    public int getMinGranularity() {
        return getDefaultTestLogging().getMinGranularity();
    }

    @Override
    public void setMinGranularity(int granularity) {
        getDefaultTestLogging().setMinGranularity(granularity);
    }

    @Override
    public int getMaxGranularity() {
        return getDefaultTestLogging().getMaxGranularity();
    }

    @Override
    public void setMaxGranularity(int granularity) {
        getDefaultTestLogging().setMaxGranularity(granularity);
    }

    @Override
    public int getDisplayGranularity() {
        return getDefaultTestLogging().getDisplayGranularity();
    }

    @Override
    public void setDisplayGranularity(int granularity) {
        getDefaultTestLogging().setDisplayGranularity(granularity);
    }

    @Override
    public boolean getShowExceptions() {
        return getDefaultTestLogging().getShowExceptions();
    }

    @Override
    public void setShowExceptions(boolean flag) {
        getDefaultTestLogging().setShowExceptions(flag);
    }

    @Override
    public boolean getShowCauses() {
        return getDefaultTestLogging().getShowCauses();
    }

    @Override
    public void setShowCauses(boolean flag) {
        getDefaultTestLogging().setShowCauses(flag);
    }

    @Override
    public boolean getShowStackTraces() {
        return getDefaultTestLogging().getShowStackTraces();
    }

    @Override
    public void setShowStackTraces(boolean flag) {
        getDefaultTestLogging().setShowStackTraces(flag);
    }

    @Override
    public TestExceptionFormat getExceptionFormat() {
        return getDefaultTestLogging().getExceptionFormat();
    }

    @Override
    public void setExceptionFormat(TestExceptionFormat exceptionFormat) {
        setExceptionFormat((Object) exceptionFormat);
    }

    @Override
    public void setExceptionFormat(Object exceptionFormat) {
        getDefaultTestLogging().setExceptionFormat(exceptionFormat);
    }

    @Override
    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return getDefaultTestLogging().getStackTraceFilters();
    }

    @Override
    public void setStackTraceFilters(Set<TestStackTraceFilter> stackTraces) {
        getDefaultTestLogging().setStackTraceFilters(stackTraces);
    }

    @Override
    public void setStackTraceFilters(Iterable<?> stackTraces) {
        getDefaultTestLogging().setStackTraceFilters(stackTraces);
    }

    @Override
    public void stackTraceFilters(Object... stackTraces) {
        getDefaultTestLogging().stackTraceFilters(stackTraces);
    }

    @Override
    public boolean getShowStandardStreams() {
        return getDefaultTestLogging().getShowStandardStreams();
    }

    @Override
    public TestLoggingContainer setShowStandardStreams(boolean flag) {
        getDefaultTestLogging().setShowStandardStreams(flag);
        return this;
    }

    @Override
    public TestLogging get(LogLevel level) {
        return perLevelTestLogging.get(level);
    }

    private TestLogging getDefaultTestLogging() {
        return getLifecycle();
    }
}
