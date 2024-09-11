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

import com.google.common.base.Joiner;
import junit.framework.AssertionFailedError;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.internal.exceptions.LocationAwareException;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.normalizeLambdaIds;
import static org.gradle.util.Matchers.normalizedLineSeparators;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * An execution failure that wraps another failure, but also carries a throwable that caused the failure.
 * <p>
 * This failure may be obtained in a variety of ways, for example when running the build in-process, or
 * when using the tooling API. In any case, the throwable is used to provide additional context to the failure
 * when performing assertions.
 */
public class ExecutionFailureWithThrowable implements DelegatingExecutionFailure {

    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?m)^((\\w+ )+'.+') line: (\\d+)$");

    private final ExecutionFailure delegate;
    private final Throwable failure;

    private final List<String> fileNames = new ArrayList<>();
    private final List<String> lineNumbers = new ArrayList<>();
    private final List<FailureDetails> failures = new ArrayList<>();

    public ExecutionFailureWithThrowable(ExecutionFailure delegate, Throwable failure) {
        this.delegate = delegate;
        this.failure = failure;

        if (failure instanceof MultipleBuildFailures) {
            for (Throwable cause : ((MultipleBuildFailures) failure).getCauses()) {
                extractDetails(cause);
            }
        } else {
            extractDetails(failure);
        }
    }

    private void extractDetails(Throwable failure) {
        List<String> causes = new ArrayList<>();
        extractCauses(failure, causes);

        String failureMessage = failure.getMessage() == null ? "" : normalizeLambdaIds(failure.getMessage());
        java.util.regex.Matcher matcher = LOCATION_PATTERN.matcher(failureMessage);
        if (matcher.find()) {
            fileNames.add(matcher.group(1));
            lineNumbers.add(matcher.group(3));
            failures.add(new FailureDetails(failure, failureMessage.substring(matcher.end()).trim(), causes));
        } else {
            failures.add(new FailureDetails(failure, failureMessage.trim(), causes));
        }
    }

    private static void extractCauses(Throwable failure, List<String> causes) {
        if (failure instanceof MultipleBuildFailures) {
            MultipleBuildFailures exception = (MultipleBuildFailures) failure;
            for (Throwable componentFailure : exception.getCauses()) {
                extractCauses(componentFailure, causes);
            }
        } else if (failure instanceof LocationAwareException) {
            for (Throwable cause : ((LocationAwareException) failure).getReportableCauses()) {
                causes.add(cause.getMessage());
            }
        } else {
            causes.add(failure.getMessage());
        }
    }

    @Override
    public ExecutionFailure getDelegate() {
        return delegate;
    }

    @Override
    public ExecutionFailure getIgnoreBuildSrc() {
        return new ExecutionFailureWithThrowable(delegate.getIgnoreBuildSrc(), failure);
    }

    @Override
    public ExecutionFailure assertHasLineNumber(int lineNumber) {
        delegate.assertHasLineNumber(lineNumber);
        assertThat(this.lineNumbers, hasItem(equalTo(String.valueOf(lineNumber))));
        return this;
    }

    @Override
    public ExecutionFailure assertHasFileName(String filename) {
        delegate.assertHasFileName(filename);
        assertThat(this.fileNames, hasItem(equalTo(filename)));
        return this;
    }

    @Override
    public ExecutionFailure assertHasFailures(int count) {
        delegate.assertHasFailures(count);

        if (failures.size() != count) {
            throw new AssertionFailedError(String.format("Expected %s failures, but found %s", count, failures.size()));
        }

        return this;
    }

    @Override
    public ExecutionFailure assertThatCause(Matcher<? super String> matcher) {
        delegate.assertThatCause(matcher);

        Set<String> seen = new LinkedHashSet<>();
        Matcher<String> messageMatcher = normalizedLineSeparators(matcher);
        for (FailureDetails failure : failures) {
            for (String cause : failure.causes) {
                if (messageMatcher.matches(cause)) {
                    return this;
                }
                seen.add(cause);
            }
        }

        throw new AssertionError(String.format("Could not find matching cause in: %s%nFailure is: %s", seen, failure));
    }

    @Override
    public ExecutionFailure assertHasNoCause(String description) {
        delegate.assertHasNoCause(description);

        Matcher<String> matcher = containsString(description);
        for (FailureDetails failure : failures) {
            for (String cause : failure.causes) {
                if (matcher.matches(cause)) {
                    throw new AssertionFailedError(String.format("Expected no failure with description '%s', found: %s", description, cause));
                }
            }
        }

        return this;
    }

    @Override
    public ExecutionFailure assertHasNoCause() {
        delegate.assertHasNoCause();

        for (FailureDetails failure : failures) {
            assertEquals(0, failure.causes.size());
        }

        return this;
    }

    @Override
    public ExecutionFailure assertThatDescription(Matcher<? super String> matcher) {
        delegate.assertThatDescription(matcher);
        assertHasFailure(matcher, f -> {});
        return this;
    }

    @Override
    public ExecutionFailure assertThatAllDescriptions(Matcher<? super String> matcher) {
        delegate.assertThatAllDescriptions(matcher);
        assertHasFailure(matcher, f -> {});
        return this;
    }

    @Override
    public ExecutionFailure assertHasFailure(String description, Consumer<? super Failure> action) {
        delegate.assertHasFailure(description, action);
        assertHasFailure(startsWith(description), action);
        return this;
    }

    private void assertHasFailure(Matcher<? super String> matcher, Consumer<? super Failure> action) {
        Matcher<String> normalized = normalizedLineSeparators(matcher);
        for (FailureDetails failure : failures) {
            if (normalized.matches(failure.description)) {
                action.accept(failure);
                return;
            }
        }
        StringDescription description = new StringDescription();
        matcher.describeTo(description);
        throw new AssertionFailedError(String.format("Could not find any failure with description %s, failures:%s\n", description, Joiner.on("\n").join(failures)));
    }

    private static class FailureDetails extends AbstractFailure {
        final Throwable failure;

        public FailureDetails(Throwable failure, String description, List<String> causes) {
            super(description, causes);
            this.failure = failure;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
