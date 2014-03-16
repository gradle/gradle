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

import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableMultipleArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class RepositoryChainArtifactResolver implements ArtifactResolver {
    private final List<ModuleVersionRepository> repositories = new ArrayList<ModuleVersionRepository>();

    void add(ModuleVersionRepository repository) {
        repositories.add(repository);
    }

    public void resolve(ModuleVersionMetaData moduleMetaData, ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        findSourceRepository(moduleMetaData).resolve(artifact, result, unpackModuleSource(moduleMetaData));
    }

    public void resolve(ModuleVersionMetaData moduleMetadata, Class<? extends SoftwareArtifact> artifactType, BuildableMultipleArtifactResolveResult result) {
        ModuleVersionRepository sourceRepository = findSourceRepository(moduleMetadata);
        ModuleSource repositoryModuleSource = unpackModuleSource(moduleMetadata);
        Set<ModuleVersionArtifactMetaData> artifacts = sourceRepository.getCandidateArtifacts(moduleMetadata, artifactType);
        for (ModuleVersionArtifactMetaData artifact : artifacts) {
            DefaultBuildableArtifactResolveResult singleResult = new DefaultBuildableArtifactResolveResult();
            try {
                sourceRepository.resolve(artifact, singleResult, repositoryModuleSource);
            } catch(Throwable t) {
                // can't call up to ErrorHandlingArtifactResolver#resolve, so we'll have to handle errors ourselves
                singleResult.failed(new ArtifactResolveException(artifact.getId(), t));
            }
            result.addResult(artifact.getId(), singleResult);
        }
    }

    private ModuleVersionRepository findSourceRepository(ModuleVersionMetaData moduleMetaData) {
        RepositoryChainModuleSource source = (RepositoryChainModuleSource) moduleMetaData.getSource();
        for (ModuleVersionRepository repository : repositories) {
            if (source.getRepositoryId().equals(repository.getId())) {
                return repository;
            }
        }
        // This should never happen
        throw new IllegalStateException("No repository found for id: " + source.getRepositoryId());
    }

    private ModuleSource unpackModuleSource(ModuleVersionMetaData moduleMetaData) {
        RepositoryChainModuleSource source = (RepositoryChainModuleSource) moduleMetaData.getSource();
        return source.getDelegate();
    }
}
