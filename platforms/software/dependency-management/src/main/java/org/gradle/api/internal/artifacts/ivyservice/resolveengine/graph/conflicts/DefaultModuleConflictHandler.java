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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Describables;
import org.gradle.internal.UncheckedException;

import java.util.Set;

public class DefaultModuleConflictHandler implements ModuleConflictHandler {

    private final static Logger LOGGER = Logging.getLogger(DefaultModuleConflictHandler.class);

    private final ModuleConflictResolver<ComponentState> resolver;
    private final ConflictContainer<ModuleIdentifier, ComponentState> conflicts = new ConflictContainer<>();
    private final ImmutableModuleReplacements moduleReplacements;
    private final ResolveState resolveState;

    public DefaultModuleConflictHandler(ModuleConflictResolver<ComponentState> resolver, ImmutableModuleReplacements moduleReplacements, ResolveState resolveState) {
        this.resolver = resolver;
        this.moduleReplacements = moduleReplacements;
        this.resolveState = resolveState;
    }

    @Override
    public ModuleConflictResolver<ComponentState> getResolver() {
        return resolver;
    }

    @Override
    public boolean registerCandidate(CandidateModule candidate) {
        ImmutableModuleReplacements.Replacement replacement = moduleReplacements.getReplacementFor(candidate.getId());
        ModuleIdentifier replacedBy = replacement == null ? null : replacement.getTarget();
        ConflictContainer<ModuleIdentifier, ComponentState>.Conflict conflict = conflicts.newElement(candidate.getId(), candidate.getVersions(), replacedBy);
        if (conflict != null) {
            // For each module participating in the conflict, deselect the currently selection, and remove all outgoing edges from the version.
            // This will propagate through the graph and prune configurations that are no longer required.
            for (ModuleIdentifier participant : conflict.participants) {
                resolveState.getModule(participant).clearSelection();
            }
            return true;
        }
        return false;
    }

    /**
     * Informs if there are any batched up conflicts.
     */
    @Override
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    /**
     * Resolves the conflict by delegating to the conflict resolver who selects single version from given candidates.
     */
    @Override
    public void resolveNextConflict() {
        assert hasConflicts();
        ConflictContainer<ModuleIdentifier, ComponentState>.Conflict conflict = conflicts.popConflict();
        ConflictResolverDetails<ComponentState> details = new DefaultConflictResolverDetails<>(conflict.candidates);
        resolver.select(details);
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        }

        ComponentState selected = details.getSelected();
        if (selected == null) {
            throw new IllegalArgumentException("Module conflict resolver " + resolver + " did not select any module from " + conflict.candidates);
        }

        // Visit the winning module first so that when we visit unattached dependencies of
        // losing modules, the winning module always has a selected component.
        ModuleResolveState winningModule = selected.getModule();
        resolveState.getModule(winningModule.getId()).replaceWith(selected);

        for (ModuleIdentifier moduleId : conflict.participants) {
            if (!moduleId.equals(winningModule.getId())) {
                resolveState.getModule(moduleId).replaceWith(selected);
            }
        }

        maybeSetReason(conflict.participants, details.getSelected());
        LOGGER.debug("Selected {} from conflicting modules {}.", details.getSelected(), conflict.candidates);
    }

    private void maybeSetReason(Set<ModuleIdentifier> participants, ComponentResolutionState selected) {
        for (ModuleIdentifier identifier : participants) {
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
