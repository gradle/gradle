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
import org.gradle.tooling.provider.model.internal.ToolingModelScopeResult;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects failures that resilient model building did not throw straight away, so that partial models could still be
 * returned to the client. Model results are collected as they are built and their failures are raised when the build
 * finishes, so the build still fails. The two kinds are kept apart because they are reported differently at finish
 * (see {@code DefaultBuildTreeLifecycleController}).
 * <p>
 * Thread-safe: models may be queried in parallel.
 */
@NullMarked
public final class ResilientBuildTreeFailureCollector {

    private final List<Throwable> modelBuilderFailures = new ArrayList<>();
    private final List<Throwable> configurationFailures = new ArrayList<>();

    /**
     * Collects any failure held behind the given result, so the build fails when it finishes even though the (partial)
     * client result is returned.
     */
    public synchronized void collectFrom(ToolingModelScopeResult result) {
        Throwable modelBuilderFailure = result.getModelBuilderFailure();
        if (modelBuilderFailure != null) {
            modelBuilderFailures.add(modelBuilderFailure);
        }
        Throwable configurationFailure = result.getConfigurationFailure();
        if (configurationFailure != null) {
            configurationFailures.add(configurationFailure);
        }
    }

    public synchronized List<Throwable> getModelBuilderFailures() {
        return ImmutableList.copyOf(modelBuilderFailures);
    }

    public synchronized List<Throwable> getConfigurationFailures() {
        return ImmutableList.copyOf(configurationFailures);
    }
}
