/*
 * Copyright 2018 the original author or authors.
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

public class ErrorsOnStdoutScrapingExecutionFailure extends ErrorsOnStdoutScrapingExecutionResult implements ExecutionFailure {
    private final ExecutionFailure delegate;

    public ErrorsOnStdoutScrapingExecutionFailure(ExecutionFailure delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public ExecutionFailure getIgnoreBuildSrc() {
        return new ErrorsOnStdoutScrapingExecutionFailure(delegate.getIgnoreBuildSrc());
    }

    @Override
    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        delegate.assertHasLineNumber(lineNumber);
        return this;
    }

    @Override
    public ExecutionFailure assertHasFileName(String filename) {
        delegate.assertHasFileName(filename);
        return this;
    }

    @Override
    public ExecutionFailure assertHasFailures(int count) {
        delegate.assertHasFailures(count);
        return this;
    }

    @Override
    public ExecutionFailure assertHasCause(String description) {
        delegate.assertHasCause(description);
        return this;
    }

    @Override
    public ExecutionFailure assertThatCause(Matcher<? super String> matcher) {
        delegate.assertThatCause(matcher);
        return this;
    }

    @Override
    public ExecutionFailure assertHasDescription(String context) {
        delegate.assertHasDescription(context);
        return this;
    }

    @Override
    public ExecutionFailure assertThatDescription(Matcher<? super String> matcher) {
        delegate.assertThatDescription(matcher);
        return this;
    }

    @Override
    public ExecutionFailure assertThatAllDescriptions(Matcher<? super String> matcher) {
        delegate.assertThatDescription(matcher);
        return this;
    }

    @Override
    public ExecutionFailure assertHasFailure(String description, Consumer<? super Failure> action) {
        delegate.assertHasFailure(description, action);
        return this;
    }

    @Override
    public ExecutionFailure assertHasResolutions(String... resolutions) {
        delegate.assertHasResolutions(resolutions);
        return this;
    }

    @Override
    public ExecutionFailure assertHasResolution(String resolution) {
        delegate.assertHasResolution(resolution);
        return this;
    }

    @Override
    public ExecutionFailure assertHasNoCause(String description) {
        delegate.assertHasNoCause(description);
        return this;
    }

    @Override
    public ExecutionFailure assertHasNoCause() {
        delegate.assertHasNoCause();
        return this;
    }

    @Override
    public ExecutionFailure assertTestsFailed() {
        delegate.assertTestsFailed();
        return this;
    }

    @Override
    public DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
        return delegate.assertResolutionFailure(configurationPath);
    }
}
