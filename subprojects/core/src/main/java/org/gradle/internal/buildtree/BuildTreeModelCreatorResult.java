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

package org.gradle.internal.buildtree;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The result of a {@link BuildTreeModelCreator} phase: the model built for the client (if any) together with the
 * failures that resilient model building held behind partial results. Those failures must still fail the build even
 * though the (partial) model was returned to the client.
 */
@NullMarked
public final class BuildTreeModelCreatorResult<T> {

    @Nullable
    private final T model;
    private final List<Throwable> configurationFailures;
    private final List<Throwable> modelBuilderFailures;

    public BuildTreeModelCreatorResult(@Nullable T model, List<Throwable> configurationFailures, List<Throwable> modelBuilderFailures) {
        this.model = model;
        this.configurationFailures = ImmutableList.copyOf(configurationFailures);
        this.modelBuilderFailures = ImmutableList.copyOf(modelBuilderFailures);
    }

    @Nullable
    public T getModel() {
        return model;
    }

    /**
     * Configuration failures that a project or the build itself hit while configuring, held behind a partial result.
     */
    public List<Throwable> getConfigurationFailures() {
        return configurationFailures;
    }

    /**
     * Failures thrown by tooling model builders after their target project configured successfully.
     */
    public List<Throwable> getModelBuilderFailures() {
        return modelBuilderFailures;
    }

    public boolean hasFailures() {
        return !configurationFailures.isEmpty() || !modelBuilderFailures.isEmpty();
    }

    /**
     * A result carrying the given model together with the failures the collector accumulated while it was built.
     */
    public static <T> BuildTreeModelCreatorResult<T> of(@Nullable T model, ResilientBuildTreeFailureCollector failures) {
        return new BuildTreeModelCreatorResult<>(model, failures.getConfigurationFailures(), failures.getModelBuilderFailures());
    }
}
