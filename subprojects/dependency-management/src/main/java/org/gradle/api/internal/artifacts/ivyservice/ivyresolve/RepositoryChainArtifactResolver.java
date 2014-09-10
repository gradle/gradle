/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentUsage;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import java.util.LinkedHashMap;
import java.util.Map;

class RepositoryChainArtifactResolver implements ArtifactResolver {
    private final Map<String, ModuleComponentRepository> repositories = new LinkedHashMap<String, ModuleComponentRepository>();

    void add(ModuleComponentRepository repository) {
        repositories.put(repository.getId(), repository);
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSource());
        ComponentResolveMetaData unpackedComponent = unpackSource(component);
        // First try to determine the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveModuleArtifacts(unpackedComponent, artifactType, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveModuleArtifacts(unpackedComponent, artifactType, result);
        }
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSource());
        ComponentResolveMetaData unpackedComponent = unpackSource(component);
        // First try to determine the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveModuleArtifacts(unpackedComponent, usage, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveModuleArtifacts(unpackedComponent, usage, result);
        }
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource source, BuildableArtifactResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(source);
        ModuleSource unpackedSource = unpackSource(source);

        // First try to resolve the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveArtifact(artifact, unpackedSource, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifact(artifact, unpackedSource, result);
        }
    }

    private ModuleComponentRepository findSourceRepository(ModuleSource originalSource) {
        ModuleComponentRepository moduleVersionRepository = repositories.get(repositorySource(originalSource).getRepositoryId());
        if (moduleVersionRepository == null) {
            throw new IllegalStateException("Attempting to resolve artifacts from invalid repository");
        }
        return moduleVersionRepository;
    }

    private RepositoryChainModuleSource repositorySource(ModuleSource original) {
        return Transformers.cast(RepositoryChainModuleSource.class).transform(original);
    }

    private ModuleSource unpackSource(ModuleSource original) {
        return repositorySource(original).getDelegate();
    }

    private ComponentResolveMetaData unpackSource(ComponentResolveMetaData component) {
        return component.withSource(repositorySource(component.getSource()).getDelegate());
    }
}
