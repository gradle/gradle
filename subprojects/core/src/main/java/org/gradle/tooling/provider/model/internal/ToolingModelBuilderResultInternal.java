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

package org.gradle.tooling.provider.model.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.buildtree.ResilientModelFailure;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Internal type for a model builder result that contains additional data beyond the model itself if needed.
 * <p>
 * {@link ToolingModelBuilder} can optionally return a {@link ToolingModelBuilderResultInternal} instead of a raw model.
 * {@link ToolingModelBuilderResultInternal} is then unpacked before the model is returned to the client.
 * <p>
 * A partial result produced by resilient model building can also carry a {@link #getResilientFailure() resilient
 * failure}: a failure that was hidden from the client but must still fail the build once model building finishes.
 * Carrying it with the result keeps the data-flow explicit in the types instead of in shared state.
 * <p>
 * <strong>This class is intentionally not {@code Serializable}</strong>. It must not travel over the Tooling API serialization boundary.
 */
@NullMarked
public class ToolingModelBuilderResultInternal {

    @Nullable
    private final Object model;
    private final List<Failure> failures;
    @Nullable
    private final ResilientModelFailure resilientFailure;

    private ToolingModelBuilderResultInternal(@Nullable Object model, List<Failure> failures, @Nullable ResilientModelFailure resilientFailure) {
        this.model = model;
        this.failures = failures;
        this.resilientFailure = resilientFailure;
    }

    public void throwFailureIfPresent() {
        if (failures.isEmpty()) {
            return;
        }
        Throwable firstOriginalException = failures.get(0).getOriginal();
        if (firstOriginalException instanceof RuntimeException) {
            throw (RuntimeException) firstOriginalException;
        }
        throw new RuntimeException(firstOriginalException);
    }

    @Nullable
    public Object getModel() {
        return model;
    }

    public List<Failure> getFailures() {
        return failures;
    }

    /**
     * A failure hidden behind this partial result that must still fail the build, or {@code null} if there is none.
     */
    @Nullable
    public ResilientModelFailure getResilientFailure() {
        return resilientFailure;
    }

    /**
     * Returns a copy of this result that also carries the given resilient failure.
     */
    public ToolingModelBuilderResultInternal withResilientFailure(ResilientModelFailure resilientFailure) {
        return new ToolingModelBuilderResultInternal(model, failures, resilientFailure);
    }

    public static ToolingModelBuilderResultInternal attachFailures(@Nullable Object model, List<Failure> additionalFailures) {
        if (model instanceof ToolingModelBuilderResultInternal) {
            ToolingModelBuilderResultInternal result = (ToolingModelBuilderResultInternal) model;
            List<Failure> merged = ImmutableList.<Failure>builder()
                .addAll(additionalFailures)
                .addAll(result.getFailures())
                .build();
            return new ToolingModelBuilderResultInternal(result.getModel(), merged, result.resilientFailure);
        }
        return of(model, ImmutableList.copyOf(additionalFailures));
    }

    public static ToolingModelBuilderResultInternal of(@Nullable Object model) {
        return new ToolingModelBuilderResultInternal(model, ImmutableList.of(), null);
    }

    public static ToolingModelBuilderResultInternal of(@Nullable Object model, List<Failure> failures) {
        return new ToolingModelBuilderResultInternal(model, ImmutableList.copyOf(failures), null);
    }

    public static ToolingModelBuilderResultInternal of(List<Failure> failures) {
        return new ToolingModelBuilderResultInternal(null, ImmutableList.copyOf(failures), null);
    }
}
