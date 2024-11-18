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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Describables;
import org.gradle.internal.UncheckedException;

import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.PotentialConflictFactory.potentialConflict;

public class DefaultConflictHandler implements ModuleConflictHandler {

    private final static Logger LOGGER = Logging.getLogger(DefaultConflictHandler.class);

    private final ModuleConflictResolver<ComponentState> resolver;
    private final ConflictContainer<ModuleIdentifier, ComponentState> conflicts = new ConflictContainer<>();
    private final ImmutableModuleReplacements moduleReplacements;

    public DefaultConflictHandler(ModuleConflictResolver<ComponentState> resolver, ImmutableModuleReplacements moduleReplacements) {
        this.resolver = resolver;
        this.moduleReplacements = moduleReplacements;
    }

    @Override
    public ModuleConflictResolver<ComponentState> getResolver() {
        return resolver;
    }

    /**
     * Registers new newModule and returns an instance of a conflict if conflict exists.
     */
    @Override
    public PotentialConflict registerCandidate(CandidateModule candidate) {
        ImmutableModuleReplacements.Replacement replacement = moduleReplacements.getReplacementFor(candidate.getId());
        ModuleIdentifier replacedBy = replacement == null ? null : replacement.getTarget();
        return potentialConflict(conflicts.newElement(candidate.getId(), candidate.getVersions(), replacedBy));
    }

    /**
     * Informs if there are any batched up conflicts.
     */
    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    /**
     * Resolves the conflict by delegating to the conflict resolver who selects single version from given candidates. Executes provided action against the conflict resolution result object.
     */
    @Override
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        assert hasConflicts();
        ConflictContainer<ModuleIdentifier, ComponentState>.Conflict conflict = conflicts.popConflict();
        ConflictResolverDetails<ComponentState> details = new DefaultConflictResolverDetails<>(conflict.candidates);
        resolver.select(details);
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        }
        ComponentResolutionState selected = details.getSelected();
        ConflictResolutionResult result = new DefaultConflictResolutionResult(conflict.participants, selected);
        resolutionAction.execute(result);
        if (selected != null) {
            maybeSetReason(conflict.participants, selected);
        }
        LOGGER.debug("Selected {} from conflicting modules {}.", selected, conflict.candidates);
    }

    private void maybeSetReason(Set<ModuleIdentifier> partifipants, ComponentResolutionState selected) {
        for (ModuleIdentifier identifier : partifipants) {
            ImmutableModuleReplacements.Replacement replacement = moduleReplacements.getReplacementFor(identifier);
            if (replacement != null) {
                String reason = replacement.getReason();
                ComponentSelectionDescriptorInternal moduleReplacement = ComponentSelectionReasons.SELECTED_BY_RULE.withDescription(Describables.of(identifier, "replaced with", replacement.getTarget()));
                if (reason != null) {
                    moduleReplacement = moduleReplacement.withDescription(Describables.of(reason));
                }
                selected.addCause(moduleReplacement);
            }
        }
    }
}
