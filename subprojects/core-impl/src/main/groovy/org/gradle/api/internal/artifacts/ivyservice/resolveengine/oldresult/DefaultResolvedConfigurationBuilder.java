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
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.internal.Factory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.io.File;
import java.util.*;

public class DefaultResolvedConfigurationBuilder implements
        ResolvedConfigurationBuilder, ResolvedConfigurationResults, ResolvedContentsMapping {

    private final Map<Long, ResolvedArtifact> artifacts = new LinkedHashMap<Long, ResolvedArtifact>();
    private final Set<UnresolvedDependency> unresolvedDependencies = new LinkedHashSet<UnresolvedDependency>();
    private final IdGenerator<Long> idGenerator = new LongIdGenerator();
    private final Map<ResolvedConfigurationIdentifier, ModuleDependency> modulesMap = new HashMap<ResolvedConfigurationIdentifier, ModuleDependency>();

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

    public void addParentSpecificArtifacts(ResolvedConfigurationIdentifier child, ResolvedConfigurationIdentifier parent, Set<ResolvedArtifact> artifacts) {
        for (ResolvedArtifact a : artifacts) {
            builder.parentSpecificArtifact(child, parent, ((DefaultResolvedArtifact) a).getId());
        }
    }

    public void newResolvedDependency(ResolvedConfigurationIdentifier id) {
        builder.resolvedDependency(id);
    }

    public ResolvedArtifact newArtifact(ResolvedConfigurationIdentifier owner, ComponentMetaData compnent, ComponentArtifactMetaData artifact, ArtifactResolver artifactResolver) {
        Factory<File> artifactSource = new LazyArtifactSource(artifact, compnent.getSource(), artifactResolver);
        Factory<ResolvedDependency> dependencySource = new LazyResolvedDependencySource(owner, builder, this);
        long id = idGenerator.generateId();
        ResolvedArtifact newArtifact = new DefaultResolvedArtifact(new DefaultResolvedModuleVersion(owner.getId()), dependencySource, artifact.getName(), artifactSource, id);
        artifacts.put(id, newArtifact);
        return newArtifact;
    }

    public boolean hasError() {
        return !unresolvedDependencies.isEmpty();
    }

    public TransientConfigurationResults more() {
        return builder.load(this);
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

    private static class LazyArtifactSource implements Factory<File> {
        private final ArtifactResolver artifactResolver;
        private final ModuleSource moduleSource;
        private final ComponentArtifactMetaData artifact;

        private LazyArtifactSource(ComponentArtifactMetaData artifact, ModuleSource moduleSource, ArtifactResolver artifactResolver) {
            this.artifact = artifact;
            this.artifactResolver = artifactResolver;
            this.moduleSource = moduleSource;
        }

        public File create() {
            DefaultBuildableArtifactResolveResult result = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifact, moduleSource, result);
            return result.getFile();
        }
    }

    private static class LazyResolvedDependencySource implements Factory<ResolvedDependency> {
        private final ResolvedConfigurationIdentifier owner;
        private TransientConfigurationResultsBuilder builder;
        private ResolvedContentsMapping mapping;

        public LazyResolvedDependencySource(ResolvedConfigurationIdentifier owner, TransientConfigurationResultsBuilder builder, ResolvedContentsMapping mapping) {
            this.owner = owner;
            this.builder = builder;
            this.mapping = mapping;
        }

        public ResolvedDependency create() {
            return builder.load(mapping).getResolvedDependency(owner);
        }
    }
}
