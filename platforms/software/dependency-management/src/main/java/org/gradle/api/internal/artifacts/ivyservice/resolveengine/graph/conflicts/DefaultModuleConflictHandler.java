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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Describables;

import java.util.Set;

public class DefaultModuleConflictHandler implements ModuleConflictHandler {

    private final static Logger LOGGER = Logging.getLogger(DefaultModuleConflictHandler.class);

    private final ConflictContainer<ModuleIdentifier> conflicts = new ConflictContainer<>();
    private final ImmutableModuleReplacements moduleReplacements;
    private final ResolveState resolveState;

    public DefaultModuleConflictHandler(ImmutableModuleReplacements moduleReplacements, ResolveState resolveState) {
        this.moduleReplacements = moduleReplacements;
        this.resolveState = resolveState;
    }

    @Override
    public boolean registerCandidate(CandidateModule candidate) {
        ImmutableModuleReplacements.Replacement replacement = moduleReplacements.getReplacementFor(candidate.getId());
        ModuleIdentifier replacedBy = replacement == null ? null : replacement.getTarget();
        Set<ModuleIdentifier> participants = conflicts.newElement(candidate.getId(), replacedBy);
        if (participants != null) {
            // For each module participating in the conflict, deselect the currently selection, and remove all outgoing edges from the version.
            // This will propagate through the graph and prune configurations that are no longer required.
            for (ModuleIdentifier participant : participants) {
                resolveState.getModule(participant).markInModuleConflict();
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
     * Resolves the conflict by selecting the current selection of the module that replaces
     * all other participants.
     */
    @Override
    public void resolveNextConflict() {
        assert hasConflicts();
        Set<ModuleIdentifier> participants = conflicts.popConflict();
        ModuleIdentifier winningModuleId = findReplacementTarget(participants);
        ModuleResolveState winningModule = resolveState.getModule(winningModuleId);

        ComponentState selected = winningModule.getSelected();
        if (selected == null) {
            throw new IllegalStateException("Module " + winningModuleId + " has no selection while resolving the conflict between " + participants);
        }

        // Visit the winning module first so that when we visit unattached dependencies of
        // losing modules, the winning module always has a selected component.
        winningModule.resolveModuleConflict(selected);
        for (ModuleIdentifier moduleId : participants) {
            if (!moduleId.equals(winningModuleId)) {
                resolveState.getModule(moduleId).resolveModuleConflict(selected);
            }
        }

        maybeSetReason(participants, selected);
        LOGGER.debug("Selected {} from conflicting modules {}.", selected, participants);
    }

    private ModuleIdentifier findReplacementTarget(Set<ModuleIdentifier> participants) {
        for (ModuleIdentifier participant : participants) {
            ImmutableModuleReplacements.Replacement replacement = moduleReplacements.getReplacementFor(participant);
            if (replacement == null || !participants.contains(replacement.getTarget())) {
                return participant;
            }
        }
        throw new IllegalStateException("Module replacements form a cycle between " + participants);
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
