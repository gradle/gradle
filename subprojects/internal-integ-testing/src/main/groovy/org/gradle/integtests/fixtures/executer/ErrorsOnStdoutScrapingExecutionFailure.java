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

public class ErrorsOnStdoutScrapingExecutionFailure extends ErrorsOnStdoutScrapingExecutionResult implements ExecutionFailure {
    private final ExecutionFailure delegate;

    public ErrorsOnStdoutScrapingExecutionFailure(ExecutionFailure delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    @Override
    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        return delegate.assertHasLineNumber(lineNumber);
    }

    @Override
    public ExecutionFailure assertHasFileName(String filename) {
        return delegate.assertHasFileName(filename);
    }

    @Override
    public ExecutionFailure assertHasCause(String description) {
        return delegate.assertHasCause(description);
    }

    @Override
    public ExecutionFailure assertThatCause(Matcher<String> matcher) {
        return delegate.assertThatCause(matcher);
    }

    @Override
    public ExecutionFailure assertHasDescription(String context) {
        return delegate.assertHasDescription(context);
    }

    @Override
    public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
        return delegate.assertThatDescription(matcher);
    }

    @Override
    public ExecutionFailure assertHasResolution(String resolution) {
        return delegate.assertHasResolution(resolution);
    }

    @Override
    public ExecutionFailure assertHasNoCause(String description) {
        return delegate.assertHasNoCause(description);
    }

    @Override
    public ExecutionFailure assertHasNoCause() {
        return delegate.assertHasNoCause();
    }

    @Override
    public ExecutionFailure assertTestsFailed() {
        return delegate.assertTestsFailed();
    }

    @Override
    public DependencyResolutionFailure assertResolutionFailure(String configurationPath) {
        return delegate.assertResolutionFailure(configurationPath);
    }
}
