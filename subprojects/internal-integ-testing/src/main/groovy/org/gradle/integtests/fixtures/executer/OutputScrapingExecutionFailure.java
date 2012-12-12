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
package org.gradle.integtests.fixtures.executer;

import org.hamcrest.Matcher;

import java.util.regex.Pattern;

import static org.hamcrest.Matchers.not;
import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.matchesRegexp;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class OutputScrapingExecutionFailure extends OutputScrapingExecutionResult implements ExecutionFailure {
    private final Pattern causePattern = Pattern.compile("(?m)\\s*> ");

    public OutputScrapingExecutionFailure(String output, String error) {
        super(output, error);
    }

    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        assertThat(getError(), containsString(String.format(" line: %d", lineNumber)));
        return this;
    }

    public ExecutionFailure assertHasFileName(String filename) {
        assertThat(getError(), containsLine(startsWith(filename)));
        return this;
    }

    public ExecutionFailure assertHasCause(String description) {
        assertThatCause(startsWith(description));
        return this;
    }

    public ExecutionFailure assertThatCause(Matcher<String> matcher) {
        String error = getError();
        java.util.regex.Matcher regExpMatcher = causePattern.matcher(error);
        int pos = 0;
        while (pos < error.length()) {
            if (!regExpMatcher.find(pos)) {
                break;
            }
            int start = regExpMatcher.end();
            String cause;
            if (regExpMatcher.find(start)) {
                cause = error.substring(start, regExpMatcher.start());
                pos = regExpMatcher.start();
            } else {
                cause = error.substring(start);
                pos = error.length();
            }
            if (matcher.matches(cause)) {
                return this;
            }
        }
        fail(String.format("No matching cause found in '%s'", error));
        return this;
    }

    public ExecutionFailure assertHasNoCause() {
        assertThat(getError(), not(matchesRegexp(causePattern)));
        return this;
    }

    public ExecutionFailure assertHasDescription(String context) {
        assertThatDescription(startsWith(context));
        return this;
    }

    public ExecutionFailure assertThatDescription(Matcher<String> matcher) {
        assertThat(getError(), containsLine(matcher));
        return this;
    }

    public ExecutionFailure assertTestsFailed() {
        new DetailedExecutionFailure(this).assertTestsFailed();
        return this;
    }

    public DependencyResolutionFailure getDependencyResolutionFailure() {
        return new DependencyResolutionFailure(this);
    }
}