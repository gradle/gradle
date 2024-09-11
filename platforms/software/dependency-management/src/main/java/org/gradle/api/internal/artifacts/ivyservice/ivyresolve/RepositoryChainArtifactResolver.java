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

import org.gradle.api.internal.artifacts.DefaultResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueFactory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactFileResolveResult;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

class RepositoryChainArtifactResolver implements ArtifactResolver {
    private final Map<String, ModuleComponentRepository<?>> repositories = new LinkedHashMap<>();
    private final CalculatedValueFactory calculatedValueFactory;

    RepositoryChainArtifactResolver(CalculatedValueFactory calculatedValueFactory) {
        this.calculatedValueFactory = calculatedValueFactory;
    }

    void add(ModuleComponentRepository<?> repository) {
        repositories.put(repository.getId(), repository);
    }

    @Override
    public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository<?> sourceRepository = findSourceRepository(component.getSources());
        // First try to determine the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveArtifactsWithType(component, artifactType, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result);
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
        ModuleComponentRepository<?> sourceRepository = findSourceRepository(component.getSources());
        ResolvableArtifact resolvableArtifact = sourceRepository.getArtifactCache().computeIfAbsent(artifact.getId(), id -> {
            CalculatedValue<File> artifactSource = calculatedValueFactory.create(Describables.of(artifact.getId()), () -> resolveArtifactLater(artifact, component.getSources(), sourceRepository));
            return new DefaultResolvableArtifact(component.getModuleVersionId(), artifact.getName(), artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueFactory);
        });

        result.resolved(resolvableArtifact);
    }

    private File resolveArtifactLater(ComponentArtifactMetadata artifact, ModuleSources sources, ModuleComponentRepository<?> sourceRepository) {
        // First try to resolve the artifacts locally before going remote
        BuildableArtifactFileResolveResult artifactFile = new DefaultBuildableArtifactFileResolveResult();
        sourceRepository.getLocalAccess().resolveArtifact(artifact, sources, artifactFile);
        if (!artifactFile.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifact(artifact, sources, artifactFile);
        }
        return artifactFile.getResult();
    }

    private ModuleComponentRepository<?> findSourceRepository(ModuleSources sources) {
        RepositoryChainModuleSource repositoryChainModuleSource =
            sources.getSource(RepositoryChainModuleSource.class)
                   .orElseThrow(() -> new IllegalArgumentException("No sources provided for artifact resolution"));

        ModuleComponentRepository<?> moduleVersionRepository = repositories.get(repositoryChainModuleSource.getRepositoryId());
        if (moduleVersionRepository == null) {
            throw new IllegalStateException("Attempting to resolve artifacts from invalid repository");
        }
        return moduleVersionRepository;
    }

}
