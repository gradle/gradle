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
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ArtifactSet;

import java.util.*;

public class DefaultResolvedConfigurationBuilder implements
        ResolvedConfigurationBuilder, ResolvedConfigurationResults, ResolvedContentsMapping {

    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();
    private final Map<ResolvedConfigurationIdentifier, ModuleDependency> modulesMap = new HashMap<ResolvedConfigurationIdentifier, ModuleDependency>();
    private List<ArtifactSet> artifactSets = new ArrayList<ArtifactSet>();
    private Map<Long, Set<ResolvedArtifact>> resolvedArtifactsById;
    private Set<ResolvedArtifact> allResolvedArtifacts;

    private final TransientConfigurationResultsBuilder builder;

    public DefaultResolvedConfigurationBuilder(TransientConfigurationResultsBuilder builder) {
        this.builder = builder;
    }

    public void addUnresolvedDependency(UnresolvedDependency unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }

    public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedConfigurationIdentifier dependency) {
        builder.firstLevelDependency(dependency);
        //we don't serialise the module dependencies at this stage so we need to keep track
        //of the mapping module dependency <-> resolved dependency
        modulesMap.put(dependency, moduleDependency);
    }

    public void done(ResolvedConfigurationIdentifier root) {
        builder.done(root);
    }

    public void addChild(ResolvedConfigurationIdentifier parent, ResolvedConfigurationIdentifier child) {
        builder.parentChildMapping(parent, child);
    }

    public void addArtifacts(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, ArtifactSet artifactSet) {
        builder.parentSpecificArtifacts(child, parent, artifactSet.getId());
        artifactSets.add(artifactSet);
    }

    public void newResolvedDependency(ResolvedConfigurationIdentifier id) {
        builder.resolvedDependency(id);
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public TransientConfigurationResults more() {
        return builder.load(this);
    }

    // TODO:DAZ Move the artifact-related stuff off ResolvedConfigurationBuilder into a new builder
    public Set<ResolvedArtifact> getArtifacts() {
        assertArtifactsResolved();
        return new LinkedHashSet<ResolvedArtifact>(allResolvedArtifacts);
    }

    public Set<ResolvedArtifact> getArtifacts(long artifactId) {
        assertArtifactsResolved();
        Set<ResolvedArtifact> a = resolvedArtifactsById.get(artifactId);
        assert a != null : "Unable to find artifacts for id: " + artifactId;
        return a;
    }

    public void resolveArtifacts() {
        if (allResolvedArtifacts == null) {
            allResolvedArtifacts = new LinkedHashSet<ResolvedArtifact>();
            resolvedArtifactsById = new LinkedHashMap<Long, Set<ResolvedArtifact>>();
            for (ArtifactSet artifactSet : artifactSets) {
                Set<ResolvedArtifact> resolvedArtifacts = artifactSet.getArtifacts();
                allResolvedArtifacts.addAll(resolvedArtifacts);
                resolvedArtifactsById.put(artifactSet.getId(), resolvedArtifacts);
            }

            // Release ResolvedArtifactSet instances so we're not holding onto state
            artifactSets = null;
        }
    }

    private void assertArtifactsResolved() {
        if (allResolvedArtifacts == null) {
            throw new IllegalStateException("Cannot access artifacts before they are explicitly resolved.");
        }
    }

    public ModuleDependency getModuleDependency(ResolvedConfigurationIdentifier id) {
        ModuleDependency m = modulesMap.get(id);
        assert m != null : "Unable to find module dependency for id: " + id;
        return m;
    }

    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }
}
