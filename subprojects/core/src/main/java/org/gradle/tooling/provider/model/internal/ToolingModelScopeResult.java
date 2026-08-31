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

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The outcome of building a model in a {@link ToolingModelScope}: the {@link #getClientResult() result returned to the
 * client} and, when resilient model building did not throw a failure straight away, the failures that must still fail
 * the build. Only one of the two failure kinds is present. Keeping these here rather than on the client result keeps
 * the client result free of build-lifecycle concerns.
 */
@NullMarked
public final class ToolingModelScopeResult {

    private final List<Throwable> configurationFailures;
    @Nullable
    private final Throwable modelBuilderFailure;
    private final ToolingModelBuilderResultInternal clientResult;

    private ToolingModelScopeResult(List<Throwable> configurationFailures, @Nullable Throwable modelBuilderFailure, ToolingModelBuilderResultInternal clientResult) {
        this.configurationFailures = ImmutableList.copyOf(configurationFailures);
        this.modelBuilderFailure = modelBuilderFailure;
        this.clientResult = clientResult;
    }

    /**
     * The model built cleanly.
     */
    public static ToolingModelScopeResult of(ToolingModelBuilderResultInternal clientResult) {
        return new ToolingModelScopeResult(ImmutableList.of(), null, clientResult);
    }

    /**
     * A project or the build itself failed to configure.
     */
    public static ToolingModelScopeResult withConfigurationFailure(ToolingModelBuilderResultInternal clientResult, Throwable failure) {
        return new ToolingModelScopeResult(ImmutableList.of(failure), null, clientResult);
    }

    /**
     * One or more builds visited by a build-scoped builder failed to configure.
     */
    public static ToolingModelScopeResult withConfigurationFailures(ToolingModelBuilderResultInternal clientResult, List<Throwable> failures) {
        return new ToolingModelScopeResult(failures, null, clientResult);
    }

    /**
     * A tooling model builder threw after its target project had configured successfully.
     */
    public static ToolingModelScopeResult withModelBuilderFailure(ToolingModelBuilderResultInternal clientResult, Throwable failure) {
        return new ToolingModelScopeResult(ImmutableList.of(), failure, clientResult);
    }

    /**
     * Configuration failures hidden behind {@link #getClientResult() the result}, empty if none.
     */
    public List<Throwable> getConfigurationFailures() {
        return configurationFailures;
    }

    /**
     * A model builder failure hidden behind {@link #getClientResult() the result}, or {@code null} if none.
     */
    @Nullable
    public Throwable getModelBuilderFailure() {
        return modelBuilderFailure;
    }

    public ToolingModelBuilderResultInternal getClientResult() {
        return clientResult;
    }
}
