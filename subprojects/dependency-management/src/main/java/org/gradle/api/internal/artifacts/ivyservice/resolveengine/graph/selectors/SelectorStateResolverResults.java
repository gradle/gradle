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
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.Collections;
import java.util.List;

class SelectorStateResolverResults {
    private final List<Registration> results;

    public SelectorStateResolverResults(int size) {
        results = Lists.newArrayListWithCapacity(size);
    }

    public <T extends ComponentResolutionState> List<T> getResolved(ComponentStateFactory<T> componentFactory) {
        ModuleVersionResolveException failure = null;
        List<T> resolved = null;
        for (Registration entry : results) {
            if (entry.superceded) {
                continue;
            }

            ResolvableSelectorState selectorState = entry.selector;
            ComponentIdResolveResult idResolveResult = entry.result;

            if (selectorState.isForce()) {
                T forcedComponent = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                forcedComponent.addCause(VersionSelectionReasons.FORCED);
                return Collections.singletonList(forcedComponent);
            }

            if (idResolveResult.mark(this)) {
                if (idResolveResult.getFailure() == null) {
                    T componentState = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                    if (resolved == null) {
                        resolved = Lists.newArrayList();
                    }
                    resolved.add(componentState);
                } else {
                    if (failure == null) {
                        failure = idResolveResult.getFailure();
                    }
                }
            }
        }

        if (resolved == null && failure != null) {
            throw failure;
        }

        return resolved == null ? Collections.<T>emptyList() : resolved;
    }

    public static <T extends ComponentResolutionState> T componentForIdResolveResult(ComponentStateFactory<T> componentFactory, ComponentIdResolveResult idResolveResult, ResolvableSelectorState selector) {
        T component = componentFactory.getRevision(idResolveResult.getId(), idResolveResult.getModuleVersionId(), idResolveResult.getMetadata());
        component.selectedBy(selector);
        component.unmatched(idResolveResult.getUnmatchedVersions());
        component.rejected(idResolveResult.getRejectedVersions());
        if (idResolveResult.isRejected()) {
            component.reject();
        }
        return component;
    }

    /**
     * Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
     */
    boolean alreadyHaveResolutionForSelector(ResolvableSelectorState selector) {
        for (Registration registration : results) {
            ComponentIdResolveResult discovered = registration.result;
            if (included(selector, discovered)) {
                register(selector, discovered);
                return true;
            }
        }
        return false;
    }

    boolean replaceExistingResolutionsWithBetterResult(ComponentIdResolveResult resolveResult) {
        // Check already-resolved dependencies and use this version if it's compatible
        boolean replaces = false;
        for (Registration registration : results) {
            if (included(registration.selector, resolveResult)) {
                registration.result = resolveResult;
                replaces = true;
            }
        }
        return replaces;
    }

    void register(ResolvableSelectorState selector, ComponentIdResolveResult resolveResult) {
        results.add(new Registration(selector, resolveResult));
    }

    private boolean included(ResolvableSelectorState dep, ComponentIdResolveResult candidate) {
        if (candidate.getFailure() != null) {
            return false;
        }
        ResolvedVersionConstraint versionConstraint = dep.getVersionConstraint();
        if (versionConstraint == null) {
            return dep.getSelector().matchesStrictly(candidate.getId());
        }
        VersionSelector preferredSelector = versionConstraint.getPreferredSelector();
        if (preferredSelector == null || !preferredSelector.canShortCircuitWhenVersionAlreadyPreselected()) {
            return false;
        }
        return preferredSelector.accept(candidate.getModuleVersionId().getVersion());
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    private static class Registration {
        private final ResolvableSelectorState selector;
        public boolean superceded = false;
        private ComponentIdResolveResult result;
        public boolean preferred = false;

        private Registration(ResolvableSelectorState selector, ComponentIdResolveResult result) {
            this.selector = selector;
            this.result = result;
        }

        @Override
        public String toString() {
            return selector.toString() + " -> " + result.getModuleVersionId();
        }
    }
}
