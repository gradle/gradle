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

import java.util.EnumSet;

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
    public void events(Object... events) {
        getEvents().addAll(toEnumSet(TestLogEvent.class, events));
    }

    @Override
    public abstract Property<Integer> getMinGranularity();

    @Override
    public abstract Property<Integer> getMaxGranularity();

    @Override
    public abstract Property<Integer> getDisplayGranularity();

    @Override
    public abstract Property<Boolean> getShowExceptions();

    @Override
    public abstract Property<Boolean> getShowCauses();

    @Override
    public abstract Property<Boolean> getShowStackTraces();

    @Override
    public abstract Property<TestExceptionFormat> getExceptionFormat();

    @Override
    public abstract SetProperty<TestStackTraceFilter> getStackTraceFilters();

    @Override
    public void stackTraceFilters(Object... filters) {
        getStackTraceFilters().addAll(toEnumSet(TestStackTraceFilter.class, filters));
    }

    @Override
    public abstract Property<Boolean> getShowStandardStreams();
}
