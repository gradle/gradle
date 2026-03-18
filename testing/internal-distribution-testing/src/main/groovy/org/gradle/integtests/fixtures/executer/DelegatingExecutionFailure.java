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

package org.gradle.integtests.fixtures.executer;

import org.hamcrest.Matcher;

import java.util.function.Consumer;

/**
 * Implements all methods of {@link ExecutionFailure} by delegating another failure instance.
 */
interface DelegatingExecutionFailure extends DelegatingExecutionResult, ExecutionFailure {

    /**
     * The delegate failure instance.
     */
    @Override
    ExecutionFailure getDelegate();

    @Override
    default ExecutionFailure getIgnoreBuildSrc() {
        return getDelegate().getIgnoreBuildSrc();
    }

    @Override
    default ExecutionFailure assertHasLineNumber(int lineNumber) {
        getDelegate().assertHasLineNumber(lineNumber);
        return this;
    }

    @Override
    default ExecutionFailure assertHasFileName(String filename) {
        getDelegate().assertHasFileName(filename);
        return this;
    }

    @Override
    default ExecutionFailure assertHasFailures(int count) {
        getDelegate().assertHasFailures(count);
        return this;
    }

    @Override
    default ExecutionFailure assertHasFailure(String description, Consumer<? super ExecutionFailure.Failure> action) {
        getDelegate().assertHasFailure(description, action);
        return this;
    }

    @Override
    default ExecutionFailure assertHasCause(String cause) {
        getDelegate().assertHasCause(cause);
        return this;
    }

    @Override
    default ExecutionFailure assertThatCause(Matcher<? super String> matcher) {
        getDelegate().assertThatCause(matcher);
        return this;
    }

    @Override
    default ExecutionFailure assertHasDescription(String description) {
        getDelegate().assertHasDescription(description);
        return this;
    }

    @Override
    default ExecutionFailure assertThatDescription(Matcher<? super String> matcher) {
        getDelegate().assertThatDescription(matcher);
        return this;
    }

    @Override
    default ExecutionFailure assertThatAllDescriptions(Matcher<? super String> matcher) {
        getDelegate().assertThatAllDescriptions(matcher);
        return this;
    }

    @Override
    default ExecutionFailure assertHasResolutions(String... resolutions) {
        getDelegate().assertHasResolutions(resolutions);
        return this;
    }

    @Override
    default ExecutionFailure assertHasResolution(String resolution) {
        getDelegate().assertHasResolution(resolution);
        return this;
    }

    @Override
    default ExecutionFailure assertHasNoCause(String description) {
        getDelegate().assertHasNoCause(description);
        return this;
    }

    @Override
    default ExecutionFailure assertHasNoCause() {
        getDelegate().assertHasNoCause();
        return this;
    }

    @Override
    default ExecutionFailure assertTestsFailed() {
        getDelegate().assertTestsFailed();
        return this;
    }

    @Override
    default DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
        return getDelegate().assertResolutionFailure(configurationPath);
    }
}
