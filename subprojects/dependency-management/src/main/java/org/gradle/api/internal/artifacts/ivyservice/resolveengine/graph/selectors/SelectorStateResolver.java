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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleSelectors;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveOptimizations;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class SelectorStateResolver<T extends ComponentResolutionState> {
    private final ModuleConflictResolver<T> conflictResolver;
    private final ComponentStateFactory<T> componentFactory;
    private final T rootComponent;
    private final ModuleIdentifier rootModuleId;
    private final ResolveOptimizations resolveOptimizations;
    private final Comparator<Version> versionComparator;

    public SelectorStateResolver(ModuleConflictResolver<T> conflictResolver, ComponentStateFactory<T> componentFactory, T rootComponent, ResolveOptimizations resolveOptimizations, Comparator<Version> versionComparator) {
        this.conflictResolver = conflictResolver;
        this.componentFactory = componentFactory;
        this.rootComponent = rootComponent;
        this.rootModuleId = rootComponent.getId().getModule();
        this.resolveOptimizations = resolveOptimizations;
        this.versionComparator = versionComparator;
    }

    public T selectBest(ModuleIdentifier moduleId, ModuleSelectors<? extends ResolvableSelectorState> selectors) {
        VersionSelector allRejects = createAllRejects(selectors);
        List<T> candidates = resolveSelectors(selectors, allRejects);
        assert !candidates.isEmpty();

        // If the module matches, add the root component into the mix
        if (moduleId.equals(rootModuleId) && !candidates.contains(rootComponent)) {
            candidates = new ArrayList<>(candidates);
            candidates.add(rootComponent);
        }

        // If we have a single common resolution, no conflicts to resolve
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        if (resolveOptimizations.mayHaveForcedPlatforms()) {
            List<T> allowed = candidates
                    .stream()
                    .filter(SelectorStateResolverResults::isVersionAllowedByPlatform)
                    .collect(Collectors.toList());
            if (!allowed.isEmpty()) {
                if (allowed.size() == 1) {
                    return allowed.get(0);
                }
                candidates = allowed;
            }
        }

        // Perform conflict resolution
        return resolveConflicts(candidates);
    }

    private List<T> resolveSelectors(ModuleSelectors<? extends ResolvableSelectorState> selectors, VersionSelector allRejects) {
        if (selectors.size() == 1) {
            ResolvableSelectorState selectorState = selectors.first();
            // Short-circuit selector merging for single selector without 'prefer'
            if (selectorState.getVersionConstraint() == null || selectorState.getVersionConstraint().getPreferredSelector() == null) {
                return resolveSingleSelector(selectorState, allRejects);
            }
        }

        List<T> results = buildResolveResults(selectors, allRejects);
        if (results.isEmpty()) {
            // Every selector was empty: simply 'resolve' one of them
            return resolveSingleSelector(selectors.first(), allRejects);
        }
        return results;
    }

    private List<T> resolveSingleSelector(ResolvableSelectorState selectorState, VersionSelector allRejects) {
        assert selectorState.getVersionConstraint() == null || selectorState.getVersionConstraint().getPreferredSelector() == null;
        ComponentIdResolveResult resolved = selectorState.resolve(allRejects);
        T selected = SelectorStateResolverResults.componentForIdResolveResult(componentFactory, resolved, selectorState);
        return Collections.singletonList(selected);
    }

    /**
     * Resolves a set of dependency selectors to component identifiers, making an attempt to find best matches.
     * If a single version can satisfy all of the selectors, the result will reflect this.
     * If not, a minimal set of versions will be provided in the result, and conflict resolution will be required to choose.
     */
    private List<T> buildResolveResults(ModuleSelectors<? extends ResolvableSelectorState> selectors, VersionSelector allRejects) {
        SelectorStateResolverResults results = new SelectorStateResolverResults(versionComparator, selectors.size());
        TreeSet<ComponentIdResolveResult> preferResults = null; // Created only on demand

        for (ResolvableSelectorState selector : selectors) {
            resolveRequireConstraint(results, selector, allRejects);
            preferResults = maybeResolvePreferConstraint(preferResults, selector, allRejects);
        }

        integratePreferResults(selectors, results, preferResults);
        return results.getResolved(componentFactory);
    }

    /**
     * Resolve the 'require' constraint of the selector.
     * A version will be registered for this selector, and it will participate in conflict resolution.
     */
    private void resolveRequireConstraint(SelectorStateResolverResults results, ResolvableSelectorState selector, VersionSelector allRejects) {
        // Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
        if (results.alreadyHaveResolutionForSelector(selector)) {
            return;
        }

        // Need to perform the actual resolve
        ComponentIdResolveResult result = selector.resolve(allRejects);

        if (result.getFailure() != null) {
            results.register(selector, result);
            return;
        }

        results.replaceExistingResolutionsWithBetterResult(result, selector.isFromLock());
        results.register(selector, result);
    }

    /**
     * Collect the result of the 'prefer' constraint of the selector, if present and not failing.
     * These results are integrated with the 'require' results in the second phase.
     */
    private TreeSet<ComponentIdResolveResult> maybeResolvePreferConstraint(TreeSet<ComponentIdResolveResult> previousResults, ResolvableSelectorState selector, VersionSelector allRejects) {

        TreeSet<ComponentIdResolveResult> preferResults = previousResults;
        ComponentIdResolveResult resolvedPreference = selector.resolvePrefer(allRejects);
        if (resolvedPreference != null && resolvedPreference.getFailure() == null) {
            if (preferResults == null) {
                preferResults = Sets.newTreeSet(new DescendingResolveResultComparator());
            }
            preferResults.add(resolvedPreference);
        }

        return preferResults;
    }

    /**
     * Given the result of resolving any 'prefer' constraints, see if these can be used to further refine the results
     *  of resolving the 'require' constraints.
     */
    private void integratePreferResults(ModuleSelectors<? extends ResolvableSelectorState> selectors, SelectorStateResolverResults results, TreeSet<ComponentIdResolveResult> preferResults) {

        if (preferResults == null) {
            return;
        }

        // If no result from 'require', just use the highest preferred version (no range merging)
        if (results.isEmpty()) {
            ComponentIdResolveResult highestPreferredVersion = preferResults.first();
            results.register(selectors.first(), highestPreferredVersion);
            return;
        }

        for (ComponentIdResolveResult preferResult : preferResults) {
            // Use the highest preferred version that refines the chosen 'require' selector
            if (results.replaceExistingResolutionsWithBetterResult(preferResult, false)) {
                break;
            }
        }
    }

    private VersionSelector createAllRejects(ModuleSelectors<? extends ResolvableSelectorState> selectors) {
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
        ConflictResolverDetails<T> details = new DefaultConflictResolverDetails<>(candidates);
        conflictResolver.select(details);
        T selected = details.getSelected();
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.getFailure());
        } else {
            ComponentSelectionDescriptorInternal desc = ComponentSelectionReasons.CONFLICT_RESOLUTION;
            selected.addCause(desc.withDescription(new VersionConflictResolutionDetails(candidates)));
        }
        return selected;
    }

    private static class DescendingResolveResultComparator implements Comparator<ComponentIdResolveResult> {
        @Override
        public int compare(ComponentIdResolveResult o1, ComponentIdResolveResult o2) {
            return o2.getModuleVersionId().getVersion().compareTo(o1.getModuleVersionId().getVersion());
        }
    }
}
