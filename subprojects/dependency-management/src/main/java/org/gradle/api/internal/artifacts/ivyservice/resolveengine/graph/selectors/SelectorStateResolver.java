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

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SelectorStateResolver<T extends ComponentResolutionState> {
    private final ModuleConflictResolver conflictResolver;
    private final ComponentStateFactory<T> componentFactory;
    private final T rootComponent;
    private final ModuleIdentifier rootModuleId;

    public SelectorStateResolver(ModuleConflictResolver conflictResolver, ComponentStateFactory<T> componentFactory, T rootComponent) {
        this.conflictResolver = conflictResolver;
        this.componentFactory = componentFactory;
        this.rootComponent = rootComponent;
        this.rootModuleId = rootComponent.getId().getModule();
    }

    public T selectBest(ModuleIdentifier moduleId, List<? extends ResolvableSelectorState> selectors) {
        VersionSelector allRejects = createAllRejects(selectors);
        List<T> candidates = resolveSelectors(selectors, allRejects);
        assert !candidates.isEmpty();

        // If the module matches, add the root component into the mix
        if (moduleId.equals(rootModuleId) && !candidates.contains(rootComponent)) {
            candidates = new ArrayList<T>(candidates);
            candidates.add(rootComponent);
        }

        // If we have a single common resolution, no conflicts to resolve
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Perform conflict resolution
        return resolveConflicts(candidates);
    }

    private List<T> resolveSelectors(List<? extends ResolvableSelectorState> selectors, VersionSelector allRejects) {
        if (selectors.size() == 1) {
            ResolvableSelectorState selectorState = selectors.get(0);
            return resolveSingleSelector(selectorState, allRejects);
        }

        List<T> results = buildResolveResults(selectors, allRejects);
        if (results.isEmpty()) {
            // Every selector was empty: simply 'resolve' one of them
            return resolveSingleSelector(selectors.get(0), allRejects);
        }
        return results;
    }

    private List<T> resolveSingleSelector(ResolvableSelectorState selectorState, VersionSelector allRejects) {
        ComponentIdResolveResult resolved = selectorState.resolve(allRejects);
        T selected = SelectorStateResolverResults.componentForIdResolveResult(componentFactory, resolved, selectorState);
        return Collections.singletonList(selected);
    }

    /**
     * Resolves a set of dependency selectors to component identifiers, making an attempt to find best matches.
     * If a single version can satisfy all of the selectors, the result will reflect this.
     * If not, a minimal set of versions will be provided in the result, and conflict resolution will be required to choose.
     */
    private List<T> buildResolveResults(List<? extends ResolvableSelectorState> selectors, VersionSelector allRejects) {
        SelectorStateResolverResults results = new SelectorStateResolverResults(selectors.size());
        for (ResolvableSelectorState selector : selectors) {
            // If this selector doesn't specify a prefer/require version, then ignore it here.
            // This will avoid resolving 'reject' selectors, too.
            if (isEmpty(selector)) {
                selector.markResolved();
                continue;
            }

            // Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
            if (results.alreadyHaveResolution(selector)) {
                selector.markResolved();
                continue;
            }

            // Need to perform the actual resolve
            ComponentIdResolveResult resolved = selector.resolve(allRejects);

            results.registerResolution(selector, resolved);
        }

        return results.getResolved(componentFactory);
    }

    private boolean isEmpty(ResolvableSelectorState selector) {
        ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
        if (versionConstraint != null) {
            return versionConstraint.getPreferredSelector().getSelector().isEmpty();
        }
        return false;
    }

    private VersionSelector createAllRejects(List<? extends ResolvableSelectorState> selectors) {
        List<VersionSelector> rejectSelectors = null;
        for (ResolvableSelectorState selector : selectors) {
            ResolvedVersionConstraint versionConstraint = selector.getVersionConstraint();
            if (versionConstraint != null && versionConstraint.getRejectedSelector() != null) {
                if (rejectSelectors == null) {
                    rejectSelectors = Lists.newArrayListWithCapacity(selectors.size());
                }
                rejectSelectors.add(versionConstraint.getRejectedSelector());
            }
        }
        if (rejectSelectors == null) {
            return null;
        }
        if (rejectSelectors.size() == 1) {
            return rejectSelectors.get(0);
        }
        return new UnionVersionSelector(rejectSelectors);
    }

    private T resolveConflicts(Collection<T> candidates) {
        // Do conflict resolution to choose the best out of current selection and candidate.
        ConflictResolverDetails<T> details = new DefaultConflictResolverDetails<T>(candidates);
        conflictResolver.select(details);
        T selected = details.getSelected();
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        } else {
            ComponentSelectionDescriptorInternal desc = VersionSelectionReasons.CONFLICT_RESOLUTION;
            selected.addCause(desc.withReason(new VersionConflictResolutionDetails(candidates)));
        }
        return selected;
    }
}
