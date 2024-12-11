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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.VirtualPlatformState;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

class SelectorStateResolverResults {
    private final Comparator<Version> versionComparator;
    private final VersionParser versionParser;
    private final List<Registration> results;

    public SelectorStateResolverResults(Comparator<Version> versionComparator, VersionParser versionParser, int size) {
        this.versionParser = versionParser;
        this.results = new ArrayList<>(size);
        this.versionComparator = versionComparator;
    }

    public <T extends ComponentResolutionState> List<T> getResolved(ComponentStateFactory<T> componentFactory) {
        ModuleVersionResolveException failure = null;
        List<T> resolved = null;
        boolean hasSoftForce = hasSoftForce();

        int size = results.size();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            Registration entry = results.get(i);
            ResolvableSelectorState selectorState = entry.selector;
            ComponentIdResolveResult idResolveResult = entry.result;

            if (selectorState.isForce() && !hasSoftForce) {
                T forcedComponent = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                return Collections.singletonList(forcedComponent);
            }

            if (idResolveResult.mark(this)) {
                if (idResolveResult.getFailure() == null) {
                    T componentState = componentForIdResolveResult(componentFactory, idResolveResult, selectorState);
                    if (resolved == null) {
                        resolved = new ArrayList<>();
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

        return resolved == null ? Collections.emptyList() : resolved;
    }

    static <T extends ComponentResolutionState> boolean isVersionAllowedByPlatform(T componentState) {
        Set<VirtualPlatformState> platformOwners = componentState.getPlatformOwners();
        if (!platformOwners.isEmpty()) {
            for (VirtualPlatformState platformOwner : platformOwners) {
                if (platformOwner.isGreaterThanForcedVersion(componentState.getVersion())) {
                    return false;
                }
            }
        } else {
            VirtualPlatformState platform = componentState.getPlatformState();
            // the platform itself is greater than the forced version
            return platform == null || !platform.isGreaterThanForcedVersion(componentState.getVersion());
        }
        return true;
    }

    private boolean hasSoftForce() {
        int size = results.size();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            Registration entry = results.get(i);
            ResolvableSelectorState selectorState = entry.selector;
            if (selectorState.isSoftForce()) {
                return true;
            }
        }
        return false;
    }

    public static <T extends ComponentResolutionState> T componentForIdResolveResult(ComponentStateFactory<T> componentFactory, ComponentIdResolveResult idResolveResult, ResolvableSelectorState selector) {
        T component = componentFactory.getRevision(idResolveResult.getId(), idResolveResult.getModuleVersionId(), idResolveResult.getState(), idResolveResult.getGraphState());
        if (idResolveResult.isRejected()) {
            component.reject();
        }
        return component;
    }

    /**
     * Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
     */
    boolean alreadyHaveResolutionForSelector(ResolvableSelectorState selector) {
        int size = results.size();
        ComponentIdResolveResult found = null;
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            Registration registration = results.get(i);
            ComponentIdResolveResult discovered = registration.result;
            if (selectorAcceptsCandidate(selector, discovered, registration.selector.isFromLock())) {
                found = discovered;
                selector.markResolved();
                break;
            }
        }
        if (found!=null) {
            register(selector, found);
            return true;
        }
        return false;
    }

    boolean replaceExistingResolutionsWithBetterResult(ComponentIdResolveResult candidate, boolean isFromLock) {
        // Check already-resolved dependencies and use this version if it's compatible
        boolean replaces = false;
        int size = results.size();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            Registration registration = results.get(i);
            ComponentIdResolveResult previous = registration.result;
            ResolvableSelectorState previousSelector = registration.selector;
            if (emptyVersion(previous) || sameVersion(previous, candidate) ||
                (selectorAcceptsCandidate(previousSelector, candidate, isFromLock) && lowerVersion(previous, candidate))) {
                registration.result = candidate;
                replaces = true;
            }
        }
        return replaces;
    }

    void register(ResolvableSelectorState selector, ComponentIdResolveResult resolveResult) {
        results.add(new Registration(selector, resolveResult));
    }

    private static boolean emptyVersion(ComponentIdResolveResult existing) {
        if (existing.getFailure() == null) {
            return existing.getModuleVersionId().getVersion().isEmpty();
        }
        return false;
    }

    private static boolean sameVersion(ComponentIdResolveResult existing, ComponentIdResolveResult resolveResult) {
        if (existing.getFailure() == null && resolveResult.getFailure() == null) {
            return existing.getId().equals(resolveResult.getId());
        }
        return false;
    }

    private boolean lowerVersion(ComponentIdResolveResult existing, ComponentIdResolveResult resolveResult) {
        if (existing.getFailure() == null && resolveResult.getFailure() == null) {
            Version existingVersion = versionParser.transform(existing.getModuleVersionId().getVersion());
            Version candidateVersion = versionParser.transform(resolveResult.getModuleVersionId().getVersion());

            int comparison = versionComparator.compare(candidateVersion, existingVersion);
            return comparison < 0;
        }
        return false;
    }

    private static boolean selectorAcceptsCandidate(ResolvableSelectorState dep, ComponentIdResolveResult candidate, boolean candidateIsFromLock) {
        if (hasFailure(candidate)) {
            return false;
        }
        ResolvedVersionConstraint versionConstraint = dep.getVersionConstraint();
        if (versionConstraint == null) {
            return dep.getComponentSelector().matchesStrictly(candidate.getId());
        }
        VersionSelector versionSelector = versionConstraint.getRequiredSelector();
        if (versionSelector != null &&
            (candidateIsFromLock || versionSelector.canShortCircuitWhenVersionAlreadyPreselected())) {

            if (candidateIsFromLock && versionSelector instanceof LatestVersionSelector) {
                // Always assume a candidate from a lock will satisfy the latest version selector
                return true;
            }

            String version = candidate.getModuleVersionId().getVersion();
            if (StringUtils.isEmpty(version)) {
                return false;
            }
            return versionSelector.accept(version);
        }
        return false;
    }

    private static boolean hasFailure(ComponentIdResolveResult candidate) {
        return candidate.getFailure() != null;
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

    private static class Registration {
        private final ResolvableSelectorState selector;
        private ComponentIdResolveResult result;

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
