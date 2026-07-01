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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of building a model in a {@link ToolingModelScope}: the {@link #getClientResult() result returned to the
 * client} and, when resilient model building did not throw a failure straight away, the failure that must still fail
 * the build. At most one of {@link #getModelBuilderFailure()} / {@link #getConfigurationFailure()} is set. Keeping
 * these here rather than on the client result keeps the client result free of build-lifecycle concerns.
 */
@NullMarked
public final class ToolingModelScopeResult {

    private final ToolingModelBuilderResultInternal clientResult;
    @Nullable
    private final Throwable modelBuilderFailure;
    @Nullable
    private final Throwable configurationFailure;

    private ToolingModelScopeResult(ToolingModelBuilderResultInternal clientResult, @Nullable Throwable modelBuilderFailure, @Nullable Throwable configurationFailure) {
        this.clientResult = clientResult;
        this.modelBuilderFailure = modelBuilderFailure;
        this.configurationFailure = configurationFailure;
    }

    /**
     * The model built cleanly.
     */
    public static ToolingModelScopeResult of(ToolingModelBuilderResultInternal clientResult) {
        return new ToolingModelScopeResult(clientResult, null, null);
    }

    /**
     * A tooling model builder threw after its target project had configured successfully.
     */
    public static ToolingModelScopeResult withModelBuilderFailure(ToolingModelBuilderResultInternal clientResult, Throwable failure) {
        return new ToolingModelScopeResult(clientResult, failure, null);
    }

    /**
     * A project or the build itself failed to configure.
     */
    public static ToolingModelScopeResult withConfigurationFailure(ToolingModelBuilderResultInternal clientResult, Throwable failure) {
        return new ToolingModelScopeResult(clientResult, null, failure);
    }

    public ToolingModelBuilderResultInternal getClientResult() {
        return clientResult;
    }

    /**
     * A model builder failure hidden behind {@link #getClientResult() the result}, or {@code null} if none.
     */
    @Nullable
    public Throwable getModelBuilderFailure() {
        return modelBuilderFailure;
    }

    /**
     * A configuration failure hidden behind {@link #getClientResult() the result}, or {@code null} if none.
     */
    @Nullable
    public Throwable getConfigurationFailure() {
        return configurationFailure;
    }
}
