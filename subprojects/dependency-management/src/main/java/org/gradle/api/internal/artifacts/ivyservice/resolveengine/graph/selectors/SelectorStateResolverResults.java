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
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SelectorStateResolverResults {
    public final Map<ResolvableSelectorState, ComponentIdResolveResult> results = Maps.newLinkedHashMap();

    public <T extends ComponentResolutionState> List<T> getResolved(ComponentStateFactory<T> componentFactory) {
        ModuleVersionResolveException failure = null;
        Set<ComponentIdResolveResult> processedResolveResults = Sets.newHashSet();
        List<T> resolved = Lists.newArrayList();
        for (ResolvableSelectorState selectorState : results.keySet()) {
            ComponentIdResolveResult idResolveResult = results.get(selectorState);

            if (selectorState.isForce()) {
                T forcedComponent = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                forcedComponent.addCause(VersionSelectionReasons.FORCED);
                return Collections.singletonList(forcedComponent);
            }

            if (processedResolveResults.add(idResolveResult)) {
                if (idResolveResult.getFailure() == null) {
                    T componentState = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                    resolved.add(componentState);
                } else {
                    if (failure == null) {
                        failure = idResolveResult.getFailure();
                    }
                }
            }
        }

        if (resolved.isEmpty() && failure != null) {
            throw failure;
        }

        return resolved;
    }

    public static <T extends ComponentResolutionState> T componentForIdResolveResult(ComponentStateFactory<T> componentFactory, ComponentIdResolveResult idResolveResult, ResolvableSelectorState selector) {
        T component = componentFactory.getRevision(idResolveResult.getId(), idResolveResult.getModuleVersionId(), idResolveResult.getMetadata());
        component.selectedBy(selector);
        if (idResolveResult.isRejected()) {
            component.reject();
        }
        return component;
    }

    /**
     * Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
     */
    boolean alreadyHaveResolution(ResolvableSelectorState dep) {
        for (ComponentIdResolveResult discovered : results.values()) {
            if (included(dep, discovered)) {
                results.put(dep, discovered);
                return true;
            }
        }
        return false;
    }

    void registerResolution(ResolvableSelectorState dep, ComponentIdResolveResult resolveResult) {
        if (resolveResult.getFailure() != null) {
            results.put(dep, resolveResult);
            return;
        }

        // Check already-resolved dependencies and use this version if it's compatible
        for (ResolvableSelectorState other : results.keySet()) {
            if (included(other, resolveResult)) {
                results.put(other, resolveResult);
            }
        }

        results.put(dep, resolveResult);
    }

    private boolean included(ResolvableSelectorState dep, ComponentIdResolveResult candidate) {
        if (candidate.getFailure() != null) {
            return false;
        }
        ResolvedVersionConstraint versionConstraint = dep.getVersionConstraint();
        VersionSelector preferredSelector = versionConstraint == null ? null : versionConstraint.getPreferredSelector();
        if (preferredSelector == null || !preferredSelector.canShortCircuitWhenVersionAlreadyPreselected()) {
            return false;
        }
        return preferredSelector.accept(candidate.getModuleVersionId().getVersion());
    }
}
