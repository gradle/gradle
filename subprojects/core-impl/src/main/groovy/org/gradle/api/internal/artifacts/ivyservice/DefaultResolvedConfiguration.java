/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.*;
import org.gradle.api.specs.Spec;

import java.util.*;

public class DefaultResolvedConfiguration extends AbstractResolvedConfiguration implements ResolvedConfigurationBuilder {
    private final ResolvedDependency root;
    private final Configuration configuration;
    private final Map<ModuleDependency, ResolvedDependency> firstLevelDependencies = new LinkedHashMap<ModuleDependency, ResolvedDependency>();
    private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();

    public DefaultResolvedConfiguration(Configuration configuration, ResolvedDependency root) {
        this.configuration = configuration;
        this.root = root;
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public void rethrowFailure() throws ResolveException {
        if (!unresolvedDependencies.isEmpty()) {
            List<Throwable> failures = new ArrayList<Throwable>();
            for (UnresolvedDependency unresolvedDependency : unresolvedDependencies) {
                failures.add(unresolvedDependency.getProblem());
            }
            throw new ResolveException(configuration, Collections.<String>emptyList(), failures);
        }
    }

    @Override
    protected Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @Override
    protected Set<ResolvedDependency> doGetFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
        Set<ResolvedDependency> matches = new LinkedHashSet<ResolvedDependency>();
        for (Map.Entry<ModuleDependency, ResolvedDependency> entry : firstLevelDependencies.entrySet()) {
            if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                matches.add(entry.getValue());
            }
        }
        return matches;
    }

    @Override
    public ResolvedDependency getRoot() {
        return root;
    }

    public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
        return artifacts;
    }

    public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency refersTo) {
        firstLevelDependencies.put(moduleDependency, refersTo);
    }

    public void addArtifact(ResolvedArtifact artifact) {
        artifacts.add(artifact);
    }

    public void addUnresolvedDependency(UnresolvedDependency unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }
}
