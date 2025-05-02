/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Executor for batches of intermediate build actions that can be initiated
 * from the top-level build action or from model builders.
 * <p>
 * All actions are always executed, and <b>may run in {@link #isParallel() parallel}</b>.
 * An action failure does not prevent the execution of the rest of the actions.
 * <p>
 * Action failures (if any) are aggregated into {@link MultipleBuildOperationFailures}.
 * Batch execution succeeds only if all action finish without exceptions.
 */
public class IntermediateBuildActionRunner {

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildModelParameters buildModelParameters;
    private final String buildOperationDescription;

    public IntermediateBuildActionRunner(
        BuildOperationExecutor buildOperationExecutor,
        BuildModelParameters buildModelParameters,
        String buildOperationDescription
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildModelParameters = buildModelParameters;
        this.buildOperationDescription = buildOperationDescription;
    }

    public boolean isParallel() {
        return buildModelParameters.isParallelToolingApiActions();
    }

    public <T> List<T> run(List<Supplier<T>> actions) {
        List<NestedAction<T>> wrappers = new ArrayList<>(actions.size());
        for (Supplier<T> action : actions) {
            wrappers.add(new NestedAction<>(buildOperationDescription, action));
        }
        runActions(wrappers);

        List<T> results = new ArrayList<>(actions.size());
        List<Throwable> failures = new ArrayList<>();
        for (NestedAction<T> wrapper : wrappers) {
            Try<T> value = wrapper.value();
            if (value.isSuccessful()) {
                results.add(value.get());
            } else {
                failures.add(value.getFailure().get());
            }
        }
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, null);
        }
        return results;
    }

    private <T> void runActions(Collection<NestedAction<T>> actions) {
        if (isParallel()) {
            buildOperationExecutor.runAllWithAccessToProjectState(buildOperationQueue -> {
                for (RunnableBuildOperation action : actions) {
                    buildOperationQueue.add(action);
                }
            });
        } else {
            for (RunnableBuildOperation action : actions) {
                try {
                    action.run(null);
                } catch (Exception e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }

    @NullMarked
    private static class NestedAction<T> implements RunnableBuildOperation {
        private final String displayName;
        private final Supplier<T> action;
        private Try<T> result;

        public NestedAction(String displayName, Supplier<T> action) {
            this.displayName = displayName;
            this.action = action;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                T value = action.get();
                result = Try.successful(value);
            } catch (Throwable t) {
                result = Try.failure(t);
            }
        }

        public Try<T> value() {
            return result;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(displayName);
        }
    }

}
