/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.buildtree.DeferredBuildFailure;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of building a model in a {@link ToolingModelScope}: the {@link #getClientResult() result returned to the
 * client} and, when resilient model building hid a failure behind a partial result, the {@link #getDeferredFailure()
 * failure} that must still fail the build. Keeping the deferred failure here rather than on the client result keeps the
 * client result free of build-lifecycle concerns.
 */
@NullMarked
public final class ToolingModelScopeResult {

    private final ToolingModelBuilderResultInternal clientResult;
    @Nullable
    private final DeferredBuildFailure deferredFailure;

    private ToolingModelScopeResult(ToolingModelBuilderResultInternal clientResult, @Nullable DeferredBuildFailure deferredFailure) {
        this.clientResult = clientResult;
        this.deferredFailure = deferredFailure;
    }

    public static ToolingModelScopeResult of(ToolingModelBuilderResultInternal clientResult) {
        return new ToolingModelScopeResult(clientResult, null);
    }

    public static ToolingModelScopeResult of(ToolingModelBuilderResultInternal clientResult, DeferredBuildFailure deferredFailure) {
        return new ToolingModelScopeResult(clientResult, deferredFailure);
    }

    public ToolingModelScopeResult withDeferredFailure(DeferredBuildFailure deferredFailure) {
        return new ToolingModelScopeResult(clientResult, deferredFailure);
    }

    public ToolingModelBuilderResultInternal getClientResult() {
        return clientResult;
    }

    /**
     * The failure hidden behind {@link #getClientResult() the result}, or {@code null} if the model built cleanly.
     */
    @Nullable
    public DeferredBuildFailure getDeferredFailure() {
        return deferredFailure;
    }
}
