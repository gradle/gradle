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

public class DefaultResolvedArtifactSet implements ResolvedArtifactSet {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ComponentResolveMetaData component;
    private final Set<ComponentArtifactMetaData> artifacts;
    private final ArtifactResolver artifactResolver;
    private final long id;
    private Set<ResolvedArtifact> resolvedArtifacts;

    public DefaultResolvedArtifactSet(ModuleVersionIdentifier ownerId, ComponentResolveMetaData component, Set<ComponentArtifactMetaData> artifacts, ArtifactResolver artifactResolver, long id) {
        this.moduleVersionIdentifier = ownerId;
        this.component = component;
        this.artifacts = artifacts;
        this.artifactResolver = artifactResolver;
        this.id = id;
    }
    
    public long getId() {
        return id;
    }

    public Set<ResolvedArtifact> getArtifacts() {
        if (resolvedArtifacts == null) {
            resolvedArtifacts = new LinkedHashSet<ResolvedArtifact>(artifacts.size());
            for (ComponentArtifactMetaData artifact : artifacts) {
                resolvedArtifacts.add(createResolvedArtifact(artifact));
            }
        }
        // TODO:DAZ Clear state that is no longer required to build the set of artifacts
        return resolvedArtifacts;
    }

    private ResolvedArtifact createResolvedArtifact(ComponentArtifactMetaData artifact) {
        Factory<File> artifactSource = new LazyArtifactSource(artifact, component.getSource(), artifactResolver);
        return new DefaultResolvedArtifact(new DefaultResolvedModuleVersion(moduleVersionIdentifier), artifact.getName(), artifactSource, id);
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
}
