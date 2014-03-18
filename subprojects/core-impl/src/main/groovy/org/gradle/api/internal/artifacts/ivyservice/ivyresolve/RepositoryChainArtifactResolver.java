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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

import java.util.ArrayList;
import java.util.List;

// TODO:DAZ Unit test
class RepositoryChainArtifactResolver implements ArtifactResolver {
    private final List<ModuleVersionRepository> repositories = new ArrayList<ModuleVersionRepository>();

    void add(ModuleVersionRepository repository) {
        repositories.add(repository);
    }

    public void resolveModuleArtifacts(ModuleVersionMetaData moduleMetadata, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        ModuleVersionRepository sourceRepository = findSourceRepository(moduleMetadata);
        sourceRepository.resolveModuleArtifacts(moduleMetadata, context, result);
    }

    public void resolveArtifact(ModuleVersionMetaData moduleMetaData, ModuleVersionArtifactMetaData artifact, BuildableArtifactResolveResult result) {
        findSourceRepository(moduleMetaData).resolveArtifact(moduleMetaData.withSource(unpackModuleSource(moduleMetaData)), artifact, result);
    }

    private ModuleVersionRepository findSourceRepository(ModuleVersionMetaData moduleMetaData) {
        String repositoryId = getSourceRepositoryId(moduleMetaData);
        for (ModuleVersionRepository repository : repositories) {
            if (repository.getId().equals(repositoryId)) {
                return repository;
            }
        }
        // This should never happen
        throw new IllegalStateException("No repository found for id: " + repositoryId);
    }

    private String getSourceRepositoryId(ModuleVersionMetaData moduleMetaData) {
        if (moduleMetaData.getSource() instanceof RepositoryChainModuleSource) {
            RepositoryChainModuleSource source = (RepositoryChainModuleSource) moduleMetaData.getSource();
            return source.getRepositoryId();
        }
        throw new IllegalStateException(String.format("Repository source not set for %s", moduleMetaData.getId()));
    }

    private ModuleSource unpackModuleSource(ModuleVersionMetaData moduleMetaData) {
        RepositoryChainModuleSource source = (RepositoryChainModuleSource) moduleMetaData.getSource();
        return source.getDelegate();
    }
}
