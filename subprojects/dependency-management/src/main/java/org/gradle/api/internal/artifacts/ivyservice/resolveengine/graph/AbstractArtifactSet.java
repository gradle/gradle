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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.DefaultResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.internal.Factory;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

abstract class AbstractArtifactSet implements ArtifactSet {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ComponentResolveMetaData component;
    private final ArtifactResolver artifactResolver;
    private Set<ResolvedArtifact> resolvedArtifacts;

    public AbstractArtifactSet(ModuleVersionIdentifier ownerId, ComponentResolveMetaData component, ArtifactResolver artifactResolver) {
        this.moduleVersionIdentifier = ownerId;
        this.component = component;
        this.artifactResolver = artifactResolver;
    }

    public Set<ResolvedArtifact> getArtifacts() {
        if (resolvedArtifacts == null) {
            // TODO:DAZ Cut the state that we hold to just what is absolutely required for artifact resolution
            Set<ComponentArtifactMetaData> componentArtifacts = resolveComponentArtifacts(component);
            resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>(componentArtifacts.size());
            for (ComponentArtifactMetaData artifact : componentArtifacts) {
                Factory<File> artifactSource = new LazyArtifactSource(artifact, component.getSource(), artifactResolver);
                ResolvedArtifact resolvedArtifact = new DefaultResolvedArtifact(new DefaultResolvedModuleVersion(moduleVersionIdentifier), artifact.getName(), artifactSource);
                resolvedArtifacts.add(resolvedArtifact);
            }
        }
        // TODO:DAZ Once artifacts are built, clear all state that is no longer required
        // TODO:DAZ Need to avoid hanging onto state when artifacts are not used: for now maybe resolve artifact sets explicitly even when not required
        // TODO:DAZ ArtifactResolver should be provided when resolving, not when constructing
        return resolvedArtifacts;
    }

    protected ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    protected abstract Set<ComponentArtifactMetaData> resolveComponentArtifacts(ComponentResolveMetaData component);

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
}
