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

import org.gradle.api.NonNullApi;
import org.gradle.internal.Try;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

@NonNullApi
public class BuildModelActionRunner {

    private final BuildOperationExecutor buildOperationExecutor;
    private final boolean parallel;
    private final String buildOperationDescription;

    public BuildModelActionRunner(
        BuildOperationExecutor buildOperationExecutor,
        BuildModelParameters buildModelParameters,
        String buildOperationDescription
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.parallel = buildModelParameters.isParallelToolingApiActions();
        this.buildOperationDescription = buildOperationDescription;
    }

    public boolean isParallel() {
        return parallel;
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

    @NonNullApi
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
