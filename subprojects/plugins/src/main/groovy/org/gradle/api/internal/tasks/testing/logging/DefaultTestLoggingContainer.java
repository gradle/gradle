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
import org.gradle.api.tasks.testing.logging.*;
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

    public TestLogging getDebug() {
        return perLevelTestLogging.get(LogLevel.DEBUG);
    }

    public void setDebug(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.DEBUG, logging);
    }

    public void debug(Action<TestLogging> action) {
        action.execute(getDebug());
    }

    public TestLogging getInfo() {
        return perLevelTestLogging.get(LogLevel.INFO);
    }

    public void setInfo(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.INFO, logging);
    }

    public void info(Action<TestLogging> action) {
        action.execute(getInfo());
    }

    public TestLogging getLifecycle() {
        return perLevelTestLogging.get(LogLevel.LIFECYCLE);
    }

    public void setLifecycle(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.LIFECYCLE, logging);
    }

    public void lifecycle(Action<TestLogging> action) {
        action.execute(getLifecycle());
    }

    public TestLogging getWarn() {
        return perLevelTestLogging.get(LogLevel.WARN);
    }

    public void setWarn(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.WARN, logging);
    }

    public void warn(Action<TestLogging> action) {
        action.execute(getWarn());
    }

    public TestLogging getQuiet() {
        return perLevelTestLogging.get(LogLevel.QUIET);
    }

    public void setQuiet(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.QUIET, logging);
    }

    public void quiet(Action<TestLogging> action) {
        action.execute(getQuiet());
    }

    public TestLogging getError() {
        return perLevelTestLogging.get(LogLevel.ERROR);
    }

    public void setError(TestLogging logging) {
        perLevelTestLogging.put(LogLevel.ERROR, logging);
    }

    public void error(Action<TestLogging> action) {
        action.execute(getError());
    }

    public Set<TestLogEvent> getEvents() {
        return getLifecycle().getEvents();
    }

    public void setEvents(Iterable<?> events) {
        getLifecycle().setEvents(events);
    }

    public void events(Object... events) {
        getLifecycle().events(events);
    }

    public int getMinGranularity() {
        return getLifecycle().getMinGranularity();
    }

    public void setMinGranularity(int granularity) {
        getLifecycle().setMinGranularity(granularity);
    }

    public int getMaxGranularity() {
        return getLifecycle().getMaxGranularity();
    }

    public void setMaxGranularity(int granularity) {
        getLifecycle().setMaxGranularity(granularity);
    }

    public int getDisplayGranularity() {
        return getLifecycle().getDisplayGranularity();
    }

    public void setDisplayGranularity(int granularity) {
        getLifecycle().setDisplayGranularity(granularity);
    }

    public boolean getShowExceptions() {
        return getLifecycle().getShowExceptions();
    }

    public void setShowExceptions(boolean flag) {
        getLifecycle().setShowExceptions(flag);
    }

    public boolean getShowCauses() {
        return getLifecycle().getShowCauses();
    }

    public void setShowCauses(boolean flag) {
        getLifecycle().setShowCauses(flag);
    }

    public boolean getShowStackTraces() {
        return getLifecycle().getShowStackTraces();
    }

    public void setShowStackTraces(boolean flag) {
        getLifecycle().setShowStackTraces(flag);
    }

    public TestExceptionFormat getExceptionFormat() {
        return getLifecycle().getExceptionFormat();
    }

    public void setExceptionFormat(Object exceptionFormat) {
        getLifecycle().setExceptionFormat(exceptionFormat);
    }

    public Set<TestStackTraceFilter> getStackTraceFilters() {
        return getLifecycle().getStackTraceFilters();
    }

    public void setStackTraceFilters(Iterable<?> stackTraces) {
        getLifecycle().setStackTraceFilters(stackTraces);
    }

    public void stackTraceFilters(Object... stackTraces) {
        getLifecycle().stackTraceFilters(stackTraces);
    }

    public boolean getShowStandardStreams() {
        return getLifecycle().getShowStandardStreams();
    }

    public TestLoggingContainer setShowStandardStreams(boolean flag) {
        getLifecycle().setShowStandardStreams(flag);
        return this;
    }

    public TestLogging get(LogLevel level) {
        return perLevelTestLogging.get(level);
    }
}
