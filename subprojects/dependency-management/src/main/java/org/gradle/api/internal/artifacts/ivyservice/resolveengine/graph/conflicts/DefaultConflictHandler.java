/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.PotentialConflictFactory.potentialConflict;

public class DefaultConflictHandler implements ConflictHandler {

    private final static Logger LOGGER = Logging.getLogger(DefaultConflictHandler.class);

    private final CompositeConflictResolver compositeResolver = new CompositeConflictResolver();
    private final ConflictContainer<ModuleIdentifier, ComponentResolutionState> conflicts = new ConflictContainer<ModuleIdentifier, ComponentResolutionState>();
    private final ModuleReplacementsData moduleReplacements;

    public DefaultConflictHandler(ModuleConflictResolver conflictResolver, ModuleReplacementsData moduleReplacements) {
        this.moduleReplacements = moduleReplacements;
        this.compositeResolver.addFirst(conflictResolver);
    }

    /**
     * Registers new newModule and returns an instance of a conflict if conflict exists.
     */
    @Nullable
    public PotentialConflict registerModule(CandidateModule newModule) {
        ModuleIdentifier replacedBy = moduleReplacements.getReplacementFor(newModule.getId());
        return potentialConflict(conflicts.newElement(newModule.getId(), newModule.getVersions(), replacedBy));
    }

    /**
     * Informs if there are any batched up conflicts.
     */
    public boolean hasConflicts() {
        return conflicts.getSize() > 0;
    }

    /**
     * Resolves the conflict by delegating to the conflict resolver who selects single version from given candidates. Executes provided action against the conflict resolution result object.
     */
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        assert hasConflicts();
        ConflictContainer.Conflict conflict = conflicts.popConflict();
        ComponentResolutionState selected = compositeResolver.select(conflict.candidates);
        ConflictResolutionResult result = new DefaultConflictResolutionResult(potentialConflict(conflict), selected);
        resolutionAction.execute(result);
        LOGGER.debug("Selected {} from conflicting modules {}.", selected, conflict.candidates);
    }

    public void registerResolver(ModuleConflictResolver conflictResolver) {
        compositeResolver.addFirst(conflictResolver);
    }
}
