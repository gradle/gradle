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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.api.tasks.testing.logging.TestStackTraceFilter;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.Map;

public abstract class DefaultTestLoggingContainer implements TestLoggingContainer {
    private final Map<LogLevel, TestLogging> perLevelTestLogging = Maps.newEnumMap(LogLevel.class);

    @Inject
    public DefaultTestLoggingContainer(ObjectFactory objects) {
        for (LogLevel level: LogLevel.values()) {
            perLevelTestLogging.put(level, objects.newInstance(DefaultTestLogging.class));
        }

        getDefaultTestLogging().getEvents().set(EnumSet.of(TestLogEvent.FAILED));
        getExceptionFormat().convention(TestExceptionFormat.SHORT);

        getInfo().getEvents().set(EnumSet.of(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_OUT, TestLogEvent.STANDARD_ERROR));
        getInfo().getStackTraceFilters().convention(EnumSet.of(TestStackTraceFilter.TRUNCATE));

        getDebug().getEvents().set(EnumSet.allOf(TestLogEvent.class));
        getDebug().getMinGranularity().set(0);
        getDebug().getStackTraceFilters().convention(EnumSet.noneOf(TestStackTraceFilter.class));
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
    public SetProperty<TestLogEvent> getEvents() {
        return getDefaultTestLogging().getEvents();
    }

    @Override
    public void events(Object... events) {
        getDefaultTestLogging().events(events);
    }

    @Override
    public Property<Integer> getMinGranularity() {
        return getDefaultTestLogging().getMinGranularity();
    }

    @Override
    public Property<Integer> getMaxGranularity() {
        return getDefaultTestLogging().getMaxGranularity();
    }

    @Override
    public Property<Integer> getDisplayGranularity() {
        return getDefaultTestLogging().getDisplayGranularity();
    }

    @Override
    public Property<Boolean> getShowExceptions() {
        return getDefaultTestLogging().getShowExceptions();
    }

    @Override
    public Property<Boolean> getShowCauses() {
        return getDefaultTestLogging().getShowCauses();
    }

    @Override
    public Property<Boolean> getShowStackTraces() {
        return getDefaultTestLogging().getShowStackTraces();
    }

    @Override
    public Property<TestExceptionFormat> getExceptionFormat() {
        return getDefaultTestLogging().getExceptionFormat();
    }

    @Override
    public SetProperty<TestStackTraceFilter> getStackTraceFilters() {
        return getDefaultTestLogging().getStackTraceFilters();
    }

    @Override
    public void stackTraceFilters(Object... stackTraces) {
        getDefaultTestLogging().stackTraceFilters(stackTraces);
    }

    @Override
    public Property<Boolean> getShowStandardStreams() {
        return getDefaultTestLogging().getShowStandardStreams();
    }

    @Override
    public TestLogging get(LogLevel level) {
        return perLevelTestLogging.get(level);
    }

    private TestLogging getDefaultTestLogging() {
        return getLifecycle();
    }
}
