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

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * By Szczepan Faber on 7/19/13
 */
public class DefaultResolvedConfigurationBuilder implements ResolvedConfigurationBuilder, ResolvedConfigurationResults {

    private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();

    private ResolvedArtifactFactory resolvedArtifactFactory;

    private final TransientResultsStore store = new TransientResultsStore();

    public DefaultResolvedConfigurationBuilder(ResolvedArtifactFactory resolvedArtifactFactory) {
        this.resolvedArtifactFactory = resolvedArtifactFactory;
    }

    public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency dependency) {
        store.firstLevelDependencies.put(moduleDependency, dependency);
    }

    //to streamline casting
    private DefaultResolvedDependency dep(ResolvedDependency d) {
        return (DefaultResolvedDependency) d;
    }

    public void addUnresolvedDependency(UnresolvedDependency unresolvedDependency) {
        unresolvedDependencies.add(unresolvedDependency);
    }

    public void done(ResolvedDependency root) {
        store.root = (DefaultResolvedDependency) root;
    }

    public void addChild(ResolvedDependency parent, ResolvedDependency child) {
        //this cast should be fine for now. The old results go away at some point plus after the refactorings are done,
        // this class is in control of instantiating the resolved dependencies.
        ((DefaultResolvedDependency) parent).addChild((DefaultResolvedDependency) child);
    }

    public void addParentSpecificArtifacts(ResolvedDependency child, ResolvedDependency parent, Set<ResolvedArtifact> artifacts) {
        ((DefaultResolvedDependency)child).addParentSpecificArtifacts(parent, artifacts);
    }

    public ResolvedDependency newResolvedDependency(ModuleVersionIdentifier id, String configurationName) {
        DefaultResolvedDependency d = new DefaultResolvedDependency(id, configurationName);
        store.allDependencies.put(d.getId(), d);
        return d;
    }

    public ResolvedArtifact newArtifact(final ResolvedDependency owner, Artifact artifact, ArtifactResolver artifactResolver) {
        Factory<File> artifactSource = resolvedArtifactFactory.artifactSource(artifact, artifactResolver);
        Factory<ResolvedDependency> dependencySource = new ResolvedDependencyFactory(owner, store);
        ResolvedArtifact newArtifact = new DefaultResolvedArtifact(owner.getModule(), dependencySource, artifact, artifactSource);
        artifacts.add(newArtifact);
        return newArtifact;
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public TransientConfigurationResults more() {
        return store;
    }

    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    public Set<UnresolvedDependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    private static class ResolvedDependencyFactory implements Factory<ResolvedDependency> {
        private final ResolvedDependency owner;
        private TransientResultsStore store;

        public ResolvedDependencyFactory(ResolvedDependency owner, TransientResultsStore store) {
            this.owner = owner;
            this.store = store;
        }

        public ResolvedDependency create() {
            return store.getResolvedDependency(new ResolvedConfigurationIdentifier(owner.getModule().getId(), owner.getConfiguration()));
        }
    }
}
