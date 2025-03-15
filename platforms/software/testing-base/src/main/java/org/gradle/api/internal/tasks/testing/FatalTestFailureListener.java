/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.util.Collections;
import java.util.List;

/**
 * Detects fatal failures to initialize test workers and records the result.
 * <p>
 * Fatal failures can later be thrown eagerly instead of included within binary test results.
 */
public class FatalTestFailureListener implements TestListenerInternal {

    private List<TestFailure> allFailures = Collections.emptyList();

    @Override
    public void started(TestDescriptorInternal testDescriptor, TestStartEvent startEvent) {

    }

    @Override
    public void completed(TestDescriptorInternal testDescriptor, TestResult testResult, TestCompleteEvent completeEvent) {
        TestDescriptorInternal unwrapped = unwrapDescriptor(testDescriptor);
        if (!(unwrapped instanceof WorkerTestClassProcessor.WorkerTestSuiteDescriptor)) {
            return;
        }

        List<TestFailure> failures = testResult.getFailures();
        if (failures != null && !failures.isEmpty()) {
            // A worker has failed to initialize.
            this.allFailures = ImmutableList.<TestFailure>builder()
                .addAll(this.allFailures)
                .add(failures.get(0))
                .build();
        }
    }

    @Override
    public void output(TestDescriptorInternal testDescriptor, TestOutputEvent event) {

    }

    @Override
    public void metadata(TestDescriptorInternal testDescriptor, TestMetadataEvent event) {

    }

    /**
     * Throw a fatal failure, if one occurred.
     */
    public void rethrowFatalFailure() {
        if (allFailures.size() == 1) {
            Throwable rawFailure = extractFailure(allFailures.get(0));
            throw UncheckedException.throwAsUncheckedException(rawFailure);
        } else if (allFailures.size() > 1) {
            throw new DefaultMultiCauseException(
                "Failed to initialize tests",
                allFailures.stream()
                    .map(this::extractFailure)
                    .collect(ImmutableList.toImmutableList())
            );
        }
    }

    private Throwable extractFailure(TestFailure failure) {
        Throwable f = failure.getRawFailure();
        if (f instanceof TestSuiteExecutionException && f.getCause() != null) {
            return f.getCause();
        }
        return f;
    }

    TestDescriptorInternal unwrapDescriptor(TestDescriptorInternal testDescriptor) {
        TestDescriptorInternal current = testDescriptor;

        while(current instanceof DecoratingTestDescriptor) {
            current = ((DecoratingTestDescriptor) current).getDescriptor();
        }

        return current;
    }
}
