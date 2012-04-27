/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.tasks.testing;

import groovy.lang.Closure;

import org.gradle.api.Action;
import org.gradle.util.ConfigureUtil;

/**
 * Configuration options for logging test related information to the console.
 */
public class TestLogging {
    private TestTraceLogging traces = new TestTraceLogging();
    private TestExceptionLogging exceptions = new TestExceptionLogging();
    private boolean showStandardStreams;

    public TestTraceLogging getTraces() {
        return traces;
    }

    public void setTraces(TestTraceLogging traces) {
        this.traces = traces;
    }

    public void traces(Action<TestTraceLogging> action) {
        traces.setEnabled(true);
        action.execute(traces);
    }

    public void traces(Closure<?> closure) {
        traces.setEnabled(true);
        ConfigureUtil.configure(closure, traces);
    }

    public TestExceptionLogging getExceptions() {
        return exceptions;
    }

    public void setExceptions(TestExceptionLogging exceptions) {
        this.exceptions = exceptions;
    }

    public void exceptions(Action<TestExceptionLogging> action) {
        exceptions.setEnabled(true);
        action.execute(exceptions);
    }

    public void exceptions(Closure<?> closure) {
        exceptions.setEnabled(true);
        ConfigureUtil.configure(closure, exceptions);
    }

    /**
     * Whether to show eagerly the standard stream events. Standard output is printed at INFO level, standard error at ERROR level.
     */
    public boolean getShowStandardStreams() {
        return showStandardStreams;
    }

    /**
     * Whether to show eagerly the standard stream events. Standard output is printed at INFO level, standard error at ERROR level.
     *
     * @param standardStreams to configure
     */
    public void setShowStandardStreams(boolean standardStreams) {
        this.showStandardStreams = standardStreams;
    }
}