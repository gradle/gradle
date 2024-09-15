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

import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

public class TestModuleSelectorState implements ResolvableSelectorState {

    private static final VersionParser VERSION_PARSER = new VersionParser();
    private static final DefaultVersionComparator VERSION_COMPARATOR = new DefaultVersionComparator();
    private static final VersionSelectorScheme VERSION_SELECTOR_SCHEME = new DefaultVersionSelectorScheme(VERSION_COMPARATOR, VERSION_PARSER);

    private final DependencyToComponentIdResolver resolver;
    private final DefaultResolvedVersionConstraint resolvedVersionConstraint;
    private final VersionConstraint versionConstraint;
    public ComponentIdResolveResult requireResult;
    public ComponentIdResolveResult preferResult;

    public TestModuleSelectorState(DependencyToComponentIdResolver resolver, VersionConstraint versionConstraint) {
        this.resolver = resolver;
        this.resolvedVersionConstraint = new DefaultResolvedVersionConstraint(versionConstraint, VERSION_SELECTOR_SCHEME);
        this.versionConstraint = versionConstraint;
    }

    @Override
    public ResolvedVersionConstraint getVersionConstraint() {
        return resolvedVersionConstraint;
    }

    @Override
    public ComponentSelector getComponentSelector() {
        return new TestComponentSelector();
    }

    @Override
    public String toString() {
        return versionConstraint.toString();
    }

    @Override
    public ComponentIdResolveResult resolve(VersionSelector allRejects) {
        requireResult = doResolve(resolvedVersionConstraint.getRequiredSelector(), allRejects, requireResult);
        return requireResult;
    }

    @Override
    public ComponentIdResolveResult resolvePrefer(VersionSelector allRejects) {
        VersionSelector preferredSelector = resolvedVersionConstraint.getPreferredSelector();
        if (preferredSelector == null) {
            return null;
        }
        preferResult = doResolve(preferredSelector, allRejects, preferResult);
        return preferResult;
    }

    private ComponentIdResolveResult doResolve(VersionSelector acceptor, VersionSelector rejector, ComponentIdResolveResult previousResult) {
        if (previousResult != null) {
            return previousResult;
        }

        BuildableComponentIdResolveResult result = new DefaultBuildableComponentIdResolveResult();
        resolver.resolve(null, DefaultComponentOverrideMetadata.EMPTY, acceptor, rejector, result);
        return result;
    }

    @Override
    public void markResolved() {
    }

    @Override
    public boolean isForce() {
        return false;
    }

    @Override
    public boolean isSoftForce() {
        return false;
    }

    @Override
    public boolean isFromLock() {
        return false;
    }

    @Override
    public boolean hasStrongOpinion() {
        return false;
    }

    @Override
    public IvyArtifactName getFirstDependencyArtifact() {
        return null;
    }

    @Override
    public ClientModule getClientModule() {
        return null;
    }

    @Override
    public boolean isChanging() {
        return false;
    }
}
