/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;

import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyConversionResult implements IvyConversionResult {
    private final ResolvedDependency root;
    private final Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies;
    private final Set<ResolvedArtifact> resolvedArtifacts;

    public DefaultIvyConversionResult(ResolvedDependency root, Map<Dependency, Set<ResolvedDependency>> firstLevelResolvedDependencies, Set<ResolvedArtifact> resolvedArtifacts) {
        this.root = root;
        this.firstLevelResolvedDependencies = firstLevelResolvedDependencies;
        this.resolvedArtifacts = resolvedArtifacts;
    }

    public ResolvedDependency getRoot() {
        return root;
    }

    public Map<Dependency, Set<ResolvedDependency>> getFirstLevelResolvedDependencies() {
        return firstLevelResolvedDependencies;
    }

    public Set<ResolvedArtifact> getResolvedArtifacts() {
        return resolvedArtifacts;
    }
}
