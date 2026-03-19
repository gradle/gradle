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

package org.gradle.execution.plan;

import org.gradle.api.internal.tasks.DeferredCrossProjectDependency;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;

/**
 * Groups deferred cross-project dependency items by the relationship type they originated from.
 * This ensures deferred items are merged back into the correct relationship set
 * (dependencies, finalizedBy, mustRunAfter, etc.) when resolved in a later wave.
 */
@NullMarked
class DeferredNodeRelationships {

    static final DeferredNodeRelationships EMPTY = new DeferredNodeRelationships(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
        Collections.emptyList(), Collections.emptyList()
    );

    final List<DeferredCrossProjectDependency> dependencies;
    final List<DeferredCrossProjectDependency> lifecycleDependencies;
    final List<DeferredCrossProjectDependency> finalizedBy;
    final List<DeferredCrossProjectDependency> mustRunAfter;
    final List<DeferredCrossProjectDependency> shouldRunAfter;

    DeferredNodeRelationships(
        List<DeferredCrossProjectDependency> dependencies,
        List<DeferredCrossProjectDependency> lifecycleDependencies,
        List<DeferredCrossProjectDependency> finalizedBy,
        List<DeferredCrossProjectDependency> mustRunAfter,
        List<DeferredCrossProjectDependency> shouldRunAfter
    ) {
        this.dependencies = dependencies;
        this.lifecycleDependencies = lifecycleDependencies;
        this.finalizedBy = finalizedBy;
        this.mustRunAfter = mustRunAfter;
        this.shouldRunAfter = shouldRunAfter;
    }

    boolean isEmpty() {
        return dependencies.isEmpty()
            && lifecycleDependencies.isEmpty()
            && finalizedBy.isEmpty()
            && mustRunAfter.isEmpty()
            && shouldRunAfter.isEmpty();
    }

    /**
     * Returns all deferred items across all relationship types, for grouping by target project.
     */
    @SuppressWarnings("MixedMutabilityReturnType")
    List<DeferredCrossProjectDependency> allItems() {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        int size = dependencies.size() + lifecycleDependencies.size() + finalizedBy.size()
            + mustRunAfter.size() + shouldRunAfter.size();
        List<DeferredCrossProjectDependency> all = new java.util.ArrayList<>(size);
        all.addAll(dependencies);
        all.addAll(lifecycleDependencies);
        all.addAll(finalizedBy);
        all.addAll(mustRunAfter);
        all.addAll(shouldRunAfter);
        return all;
    }
}
