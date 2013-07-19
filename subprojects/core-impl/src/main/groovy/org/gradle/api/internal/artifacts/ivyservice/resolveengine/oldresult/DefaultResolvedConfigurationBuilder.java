/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * By Szczepan Faber on 7/19/13
 */
public class DefaultResolvedConfigurationBuilder implements ResolvedConfigurationBuilder, ResolvedConfigurationResults {

    private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();

    private final Map<ModuleDependency, ResolvedDependency> firstLevelDependencies = new LinkedHashMap<ModuleDependency, ResolvedDependency>();
    private DefaultResolvedDependency root;

    public void addArtifact(ResolvedArtifact artifact) {
        artifacts.add(artifact);
    }

    public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency dependency) {
        firstLevelDependencies.put(moduleDependency, dependency);
    }

    public void addUnresolvedDependency(UnresolvedDependency unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }

    public void addChild(ResolvedDependency parent, ResolvedDependency child) {
        //this cast should be fine for now. The old results go away at some point plus after the refactorings are done,
        // this class will be in control of instantiating the resolved dependencies.
        ((DefaultResolvedDependency) parent).addChild((DefaultResolvedDependency) child);
    }

    public void start(DefaultResolvedDependency root) {
        this.root = root;
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public Map<ModuleDependency, ResolvedDependency> getFirstLevelDependencies() {
        return firstLevelDependencies;
    }

    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    public DefaultResolvedDependency getRoot() {
        return root;
    }
}
