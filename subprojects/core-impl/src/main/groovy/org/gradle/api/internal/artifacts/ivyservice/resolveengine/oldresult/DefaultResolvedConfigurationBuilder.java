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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.*;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.io.File;
import java.util.*;

/**
 * By Szczepan Faber on 7/19/13
 */
public class DefaultResolvedConfigurationBuilder implements
        ResolvedConfigurationBuilder, ResolvedConfigurationResults, ResolvedContentsMapping {

    private final Map<Long, ResolvedArtifact> artifacts = new LinkedHashMap<Long, ResolvedArtifact>();
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final Map<ResolvedConfigurationIdentifier, ModuleDependency> modulesMap = new HashMap<ResolvedConfigurationIdentifier, ModuleDependency>();

    private ResolvedArtifactFactory resolvedArtifactFactory;

    private final TransientResultsStore store = new TransientResultsStore();

    public DefaultResolvedConfigurationBuilder(ResolvedArtifactFactory resolvedArtifactFactory) {
        this.resolvedArtifactFactory = resolvedArtifactFactory;
    }

    //to streamline casting
    private DefaultResolvedDependency dep(ResolvedDependency d) {
        return (DefaultResolvedDependency) d;
    }

    public void addUnresolvedDependency(UnresolvedDependency unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }

    public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency dependency) {
        ResolvedConfigurationIdentifier id = dep(dependency).getId();
        store.firstLevelDependency(id);
        //we don't serialise the module dependencies at this stage so we need to keep track
        //of the mapping module dependency <-> resolved dependency
        modulesMap.put(id, moduleDependency);
    }

    public void done(ResolvedDependency root) {
        store.done(dep(root).getId());
    }

    public void addChild(ResolvedDependency parent, ResolvedDependency child) {
        store.parentChildMapping(dep(parent).getId(), dep(child).getId());
    }

    public void addParentSpecificArtifacts(ResolvedDependency child, ResolvedDependency parent, Set<ResolvedArtifact> artifacts) {
        for (ResolvedArtifact a : artifacts) {
            store.parentSpecificArtifact(dep(child).getId(), dep(parent).getId(), ((DefaultResolvedArtifact)a).getId());
        }
    }

    public ResolvedDependency newResolvedDependency(ModuleVersionIdentifier id, String configurationName) {
        //TODO SF it should be possible to completely avoid creation of ResolvedDependency instances during resolution.
        //At this stage I'm pretty sure the DependencyGraphBuilder does not really need them
        // and could operate on ResolvedConfigurationIdentifier
        DefaultResolvedDependency d = new DefaultResolvedDependency(id, configurationName);
        store.resolvedDependency(new ResolvedConfigurationIdentifier(id, configurationName));
        return d;
    }

    public ResolvedArtifact newArtifact(final ResolvedDependency owner, Artifact artifact, ArtifactResolver artifactResolver) {
        Factory<File> artifactSource = resolvedArtifactFactory.artifactSource(artifact, artifactResolver);
        Factory<ResolvedDependency> dependencySource = new ResolvedDependencyFactory(owner, store, this);
        long id = idGenerator.generateId();
        ResolvedArtifact newArtifact = new DefaultResolvedArtifact(owner.getModule(), dependencySource, artifact, artifactSource, id);
        artifacts.put(id, newArtifact);
        return newArtifact;
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public TransientConfigurationResults more() {
        return store.load(this);
    }

    public Set<ResolvedArtifact> getArtifacts() {
        return new LinkedHashSet<ResolvedArtifact>(artifacts.values());
    }

    public ResolvedArtifact getArtifact(long artifactId) {
        ResolvedArtifact a = artifacts.get(artifactId);
        assert a != null : "Unable to find artifact for id: " + artifactId;
        return a;
    }

    public ModuleDependency getModuleDependency(ResolvedConfigurationIdentifier id) {
        ModuleDependency m = modulesMap.get(id);
        assert m != null : "Unable to find module dependency for id: " + id;
        return m;
    }

    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    private static class ResolvedDependencyFactory implements Factory<ResolvedDependency> {
        private final ResolvedDependency owner;
        private TransientResultsStore store;
        private ResolvedContentsMapping mapping;

        public ResolvedDependencyFactory(ResolvedDependency owner, TransientResultsStore store, ResolvedContentsMapping mapping) {
            this.owner = owner;
            this.store = store;
            this.mapping = mapping;
        }

        public ResolvedDependency create() {
            return store.load(mapping).getResolvedDependency(new ResolvedConfigurationIdentifier(owner.getModule().getId(), owner.getConfiguration()));
        }
    }
}
