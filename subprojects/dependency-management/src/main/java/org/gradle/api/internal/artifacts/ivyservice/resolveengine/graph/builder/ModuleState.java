/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

/**
 * Describes the possible states of a module.
 */
enum ModuleState {
    /**
     * A selectable module is a module which is either new to the graph, or has
     * been visited before, but wasn't selected because another compatible version was
     * used.
     */
    Selectable(true, true),

    /**
     * A selected module is a module which has been chosen, at some point, as the version
     * to use. This is not for a lifetime: a module can be evicted through conflict resolution,
     * or another compatible module can be chosen instead if more constraints arise.
     */
    Selected(true, false),

    /**
     * An evicted module is a module which has been evicted and will never, ever be chosen starting
     * from the moment it is evicted. Either because it has been excluded, or because conflict resolution
     * selected a different version.
     */
    Evicted(false, false);

    private final boolean candidateForConflictResolution;
    private final boolean canSelect;

    ModuleState(boolean candidateForConflictResolution, boolean canSelect) {
        this.candidateForConflictResolution = candidateForConflictResolution;
        this.canSelect = canSelect;
    }

    boolean isCandidateForConflictResolution() {
        return candidateForConflictResolution;
    }

    public boolean isSelectable() {
        return canSelect;
    }
}
