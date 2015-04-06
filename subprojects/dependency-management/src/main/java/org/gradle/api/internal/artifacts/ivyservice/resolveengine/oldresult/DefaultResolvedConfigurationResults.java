/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.UnresolvedDependency;

import java.util.LinkedHashSet;
import java.util.Set;

class DefaultResolvedConfigurationResults implements ResolvedConfigurationResults {
    private final ResolvedContentsMapping resolvedContentsMapping;
    private final Set<UnresolvedDependency> unresolvedDependencies;
    private final TransientConfigurationResultsBuilder builder;
    private final Set<ResolvedArtifact> artifacts;

    DefaultResolvedConfigurationResults(Set<UnresolvedDependency> unresolvedDependencies, Set<ResolvedArtifact> artifacts, TransientConfigurationResultsBuilder builder, ResolvedContentsMapping resolvedContentsMapping) {
        this.unresolvedDependencies = unresolvedDependencies;
        this.artifacts = artifacts;
        this.builder = builder;
        this.resolvedContentsMapping = resolvedContentsMapping;
    }

    @Override
    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    @Override
    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @Override
    public Set<ResolvedArtifact> getArtifacts() {
        return new LinkedHashSet<ResolvedArtifact>(artifacts);
    }

    @Override
    public TransientConfigurationResults more() {
        return builder.load(resolvedContentsMapping);
    }
}
