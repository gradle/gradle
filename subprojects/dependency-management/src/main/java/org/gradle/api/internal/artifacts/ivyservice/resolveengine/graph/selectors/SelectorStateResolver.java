/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.Collection;
import java.util.List;

public class SelectorStateResolver<T extends ComponentResolutionState> {
    private final ModuleConflictResolver conflictResolver;
    private final ComponentStateFactory<T> componentFactory;

    public SelectorStateResolver(ModuleConflictResolver conflictResolver, ComponentStateFactory<T> componentFactory) {
        this.conflictResolver = conflictResolver;
        this.componentFactory = componentFactory;
    }

    public T selectBest(List<? extends ResolvableSelectorState> selectors, ResolvableSelectorState selector, T currentSelection) {
        ComponentIdResolveResult idResolveResult = selector.resolve();

        if (idResolveResult.getFailure() != null) {
            // Resolve failure, nothing more to do.
            // TODO:DAZ Throw here
            return null;
        }

        T candidate = componentFactory.getRevision(idResolveResult.getId(), idResolveResult.getModuleVersionId(), idResolveResult.getMetadata());

        // If no current selection for module, just use the candidate.
        if (currentSelection == null) {
            return candidate;
        }

        // Handle 'force' on local dependencies
        if (selector.isForce()) {
            return candidate;
        }

        // Choose the best option from the current selection and the new candidate.
        // This choice is made considering _all_ selectors registered for this module.
        T selected = chooseBest(selectors, currentSelection, candidate);

        // TODO:DAZ It's wasteful to recheck every reject selector every time
        maybeMarkRejected(selected, selectors);
        return selected;
    }
    /**
     * Chooses the best out of 2 components for the module, considering all selectors for the module.
     */
    private T chooseBest(List<? extends ResolvableSelectorState> selectors, final T currentSelection, final T candidate) {
        if (currentSelection == candidate) {
            return candidate;
        }

        // See if the new selector agrees with the current selection. If so, keep the current selection.
        if (allSelectorsAgreeWith(selectors, currentSelection.getVersion())) {
            return currentSelection;
        }

        // See if all known selectors agree with the candidate selection. If so, use the candidate.
        if (!currentSelection.isRoot() && allSelectorsAgreeWith(selectors, candidate.getVersion())) {
            return candidate;
        }

        // Do conflict resolution to choose the best out of current selection and candidate.
        List<T> candidates = ImmutableList.of(currentSelection, candidate);
        ConflictResolverDetails<T> details = new DefaultConflictResolverDetails<T>(candidates);
        conflictResolver.select(details);
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        }
        return details.getSelected();
    }

    private void maybeMarkRejected(ComponentResolutionState selected, List<? extends ResolvableSelectorState> selectors) {
        if (selected.isRejected()) {
            return;
        }

        String version = selected.getVersion();
        for (ResolvableSelectorState selector : selectors) {
            if (selector.getVersionConstraint() != null && selector.getVersionConstraint().getRejectedSelector() != null && selector.getVersionConstraint().getRejectedSelector().accept(version)) {
                selected.reject();
                return;
            }
        }
    }

    /**
     * Check if all of the supplied selectors agree with the version chosen
     */
    private static boolean allSelectorsAgreeWith(Collection<? extends ResolvableSelectorState> allSelectors, String version) {
        for (ResolvableSelectorState selectorState : allSelectors) {
            ResolvedVersionConstraint versionConstraint = selectorState.getVersionConstraint();
            if (versionConstraint == null) {
                return false;
            }
            VersionSelector candidateSelector = versionConstraint.getPreferredSelector();
            if (candidateSelector == null || !candidateSelector.canShortCircuitWhenVersionAlreadyPreselected() || !candidateSelector.accept(version)) {
                return false;
            }
            candidateSelector = versionConstraint.getRejectedSelector();
            if (candidateSelector != null && candidateSelector.accept(version)) {
                return false;
            }
        }
        return true;
    }

}
