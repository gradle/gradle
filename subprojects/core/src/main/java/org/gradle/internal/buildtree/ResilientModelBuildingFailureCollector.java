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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects failures observed during resilient model building so the build still fails when it finishes, while
 * partial models are returned to the client. Two kinds of failures are tracked:
 *
 * <ul>
 *     <li><em>Model builder failures</em> - thrown by a tooling model builder after the target project configured
 *     successfully. These are always propagated.</li>
 *     <li><em>Configuration failures</em> - captured while configuring projects leniently. When no tasks run these
 *     would otherwise be swallowed; they are propagated unless the build is already failing because of the same
 *     configuration failure (e.g. tasks were requested and configuration failed).</li>
 * </ul>
 */
@NullMarked
@ServiceScope(Scope.BuildTree.class)
public class ResilientModelBuildingFailureCollector {

    private final List<Throwable> modelBuilderFailures = new ArrayList<>();
    private final List<Throwable> configurationFailures = new ArrayList<>();

    public synchronized void addModelBuilderFailure(Throwable failure) {
        modelBuilderFailures.add(failure);
    }

    /**
     * Records a configuration failure. The same failure instance may be observed while building a model for each
     * project that failed to configure, so duplicates are ignored.
     */
    public synchronized void addConfigurationFailure(Throwable failure) {
        for (Throwable existing : configurationFailures) {
            if (existing == failure) {
                return;
            }
        }
        configurationFailures.add(failure);
    }

    /**
     * Returns the collected model builder failures and clears them, so they are not reported again if the build tree
     * is reused.
     */
    public synchronized List<Throwable> getAndClearModelBuilderFailures() {
        List<Throwable> collected = ImmutableList.copyOf(modelBuilderFailures);
        modelBuilderFailures.clear();
        return collected;
    }

    /**
     * Returns the collected configuration failures and clears them, so they are not reported again if the build tree
     * is reused.
     */
    public synchronized List<Throwable> getAndClearConfigurationFailures() {
        List<Throwable> collected = ImmutableList.copyOf(configurationFailures);
        configurationFailures.clear();
        return collected;
    }
}
