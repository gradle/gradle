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

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

public class TestSelectorState implements ResolvableSelectorState {

    private static final DefaultVersionComparator VERSION_COMPARATOR = new DefaultVersionComparator();
    private static final VersionSelectorScheme VERSION_SELECTOR_SCHEME = new DefaultVersionSelectorScheme(VERSION_COMPARATOR);

    private final DependencyToComponentIdResolver resolver;
    private ResolvedVersionConstraint versionConstraint;
    public ComponentIdResolveResult resolved;

    public TestSelectorState(DependencyToComponentIdResolver resolver, VersionConstraint versionConstraint) {
        this.resolver = resolver;
        this.versionConstraint = new DefaultResolvedVersionConstraint(versionConstraint, VERSION_SELECTOR_SCHEME);
    }

    @Override
    public ResolvedVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    private ComponentIdResolveResult resolveVersion(ResolvedVersionConstraint mergedConstraint) {
        BuildableComponentIdResolveResult result = new DefaultBuildableComponentIdResolveResult();
        resolver.resolve(null, mergedConstraint, result);
        return result;
    }

    @Override
    public String toString() {
        return versionConstraint.toString();
    }

    @Override
    public ComponentIdResolveResult resolve(VersionSelector allRejects) {
        if (resolved != null) {
            return resolved;
        }

        ResolvedVersionConstraint mergedConstraint = new DefaultResolvedVersionConstraint(versionConstraint.getPreferredSelector(), allRejects);
        resolved = resolveVersion(mergedConstraint);
        return resolved;
    }

    @Override
    public void markResolved() {
    }

    @Override
    public boolean isForce() {
        return false;
    }

}
