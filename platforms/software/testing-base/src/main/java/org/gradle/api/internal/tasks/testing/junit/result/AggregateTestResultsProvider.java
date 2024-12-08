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

package org.gradle.api.internal.tasks.testing.junit.result;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.concurrent.CompositeStoppable;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Objects;

public class AggregateTestResultsProvider implements TestResultsProvider {

    private static TestResult.ResultType mergeResultTypes(TestResult.ResultType ours, TestResult.ResultType theirs) {
        // Equivalent types give the same result.
        if (ours == theirs) {
            return ours;
        }
        if ((ours == TestResult.ResultType.SKIPPED && theirs == TestResult.ResultType.SUCCESS) ||
            (ours == TestResult.ResultType.SUCCESS && theirs == TestResult.ResultType.SKIPPED)) {
            return TestResult.ResultType.SUCCESS;
        }
        if (ours != TestResult.ResultType.FAILURE && theirs != TestResult.ResultType.FAILURE) {
            // This is unreachable. At least one of the remaining must be a FAILURE.
            // SUCCESS + SKIPPED is handled above.
            // SUCCESS + SUCCESS is handled above.
            // SUCCESS + FAILURE is the only remaining case with SUCCESS.
            // SKIPPED + SKIPPED is handled above.
            // SKIPPED + FAILURE is the only remaining case with SKIPPED.
            // FAILURE + FAILURE is handled above.
            throw new AssertionError("At least one of the results must be a FAILURE");
        }
        // When there is at least one FAILURE, the result is a FAILURE.
        return TestResult.ResultType.FAILURE;
    }

    private static PersistentTestResult mergeUsingFirstResultNames(PersistentTestResult first, PersistentTestResult other) {
        TestResult.ResultType resultType = mergeResultTypes(first.getResultType(), other.getResultType());
        long startTime = Math.min(first.getStartTime(), other.getStartTime());
        long endTime = Math.max(first.getEndTime(), other.getEndTime());
        ImmutableList.Builder<PersistentTestFailure> failures = ImmutableList.builderWithExpectedSize(first.getFailures().size() + other.getFailures().size());
        failures.addAll(first.getFailures());
        failures.addAll(other.getFailures());
        if (!Objects.equals(first.getLegacyProperties(), other.getLegacyProperties())) {
            throw new IllegalArgumentException("Results have differing legacy properties");
        }
        return new PersistentTestResult(first.getName(), first.getDisplayName(), resultType, startTime, endTime, failures.build(), first.getLegacyProperties());
    }

    private final Iterable<TestResultsProvider> delegates;
    private final PersistentTestResult result;

    /**
     * Creates a new provider that aggregates the results of the given providers.
     *
     * <p>
     * A new root name and display name must be given, as a single {@link PersistentTestResult} that summarizes the root result of each delegate must be created.
     * </p>
     *
     * @param newRootName the name of the new root result
     * @param newRootDisplayName the display name of the new root result
     * @param delegates the providers to aggregate
     */
    public AggregateTestResultsProvider(String newRootName, String newRootDisplayName, Iterable<TestResultsProvider> delegates) {
        Iterator<TestResultsProvider> iterator = delegates.iterator();
        Preconditions.checkArgument(iterator.hasNext(), "At least one delegate is required");
        this.delegates = delegates;
        PersistentTestResult result = iterator.next().getResult().toBuilder().name(newRootName).displayName(newRootDisplayName).build();
        while (iterator.hasNext()) {
            PersistentTestResult nextResult = iterator.next().getResult();
            result = mergeUsingFirstResultNames(result, nextResult);
        }
        this.result = result;
    }

    @Override
    public void visitChildren(Action<? super TestResultsProvider> visitor) {
        for (TestResultsProvider delegate : delegates) {
            delegate.visitChildren(visitor);
        }
    }

    @Override
    public PersistentTestResult getResult() {
        return result;
    }

    @Override
    public void copyOutput(TestOutputEvent.Destination destination, Writer writer) {
        for (TestResultsProvider delegate : delegates) {
            delegate.copyOutput(destination, writer);
        }
    }

    @Override
    public boolean hasOutput(TestOutputEvent.Destination destination) {
        for (TestResultsProvider delegate : delegates) {
            if (delegate.hasOutput(destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasAllOutput(TestOutputEvent.Destination destination) {
        for (TestResultsProvider delegate : delegates) {
            if (delegate.hasAllOutput(destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasChildren() {
        for (TestResultsProvider delegate : delegates) {
            if (delegate.hasChildren()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(delegates).stop();
    }
}
