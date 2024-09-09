/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.testing.logging;

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.util.internal.GUtil;

import java.util.EnumSet;
import java.util.Set;

/**
 * Adapters for {@link TestLogging}. In a separate class since {@link TestLogging} interface is public.
 */
abstract class TestLoggingAdapters {

    static class EventsAdapter {

        @BytecodeUpgrade
        static Set<TestLogEvent> getEvents(TestLogging self) {
            return self.getEvents().get();
        }

        @BytecodeUpgrade
        static void setEvents(TestLogging self, Set<TestLogEvent> events) {
            self.getEvents().set(events);
        }

        @BytecodeUpgrade
        static void setEvents(TestLogging self, Iterable<?> events) {
            Set<TestLogEvent> set = EnumSet.noneOf(TestLogEvent.class);
            for (Object event : events) {
                set.add(GUtil.toEnum(TestLogEvent.class, event));
            }
            self.getEvents().set(set);
        }
    }

    static class ExceptionFormatAdapter {
        @BytecodeUpgrade
        static TestExceptionFormat getExceptionFormat(TestLogging self) {
            return self.getExceptionFormat().get();
        }

        @BytecodeUpgrade
        static void setExceptionFormat(TestLogging self, TestExceptionFormat exceptionFormat) {
            self.getExceptionFormat().set(exceptionFormat);
        }

        @BytecodeUpgrade
        static void setExceptionFormat(TestLogging self, Object exceptionFormat) {
            self.getExceptionFormat().set(GUtil.toEnum(TestExceptionFormat.class, exceptionFormat));
        }
    }

    static class StackTraceFiltersAdapter {
        @BytecodeUpgrade
        static Set<TestStackTraceFilter> getStackTraceFilters(TestLogging self) {
            return self.getStackTraceFilters().get();
        }

        @BytecodeUpgrade
        static void setStackTraceFilters(TestLogging self, Set<TestStackTraceFilter> stackTraces) {
            self.getStackTraceFilters().set(stackTraces);
        }

        @BytecodeUpgrade
        static void setStackTraceFilters(TestLogging self, Iterable<?> stackTraces) {
            Set<TestStackTraceFilter> set = EnumSet.noneOf(TestStackTraceFilter.class);
            for (Object trace : stackTraces) {
                set.add(GUtil.toEnum(TestStackTraceFilter.class, trace));
            }
            self.getStackTraceFilters().set(set);
        }
    }
}
