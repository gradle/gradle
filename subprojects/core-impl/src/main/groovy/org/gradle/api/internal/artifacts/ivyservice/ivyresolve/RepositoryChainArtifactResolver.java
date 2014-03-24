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
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.internal.Transformers;

import java.util.LinkedHashMap;
import java.util.Map;

// TODO:DAZ Unit test
class RepositoryChainArtifactResolver implements ArtifactResolver {
    private final Map<String, ModuleVersionRepository> repositories = new LinkedHashMap<String, ModuleVersionRepository>();

    void add(ModuleVersionRepository repository) {
        repositories.put(repository.getId(), repository);
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        findSourceRepository(component.getSource()).resolveModuleArtifacts(unpackSource(component), context, result);
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource source, BuildableArtifactResolveResult result) {
        findSourceRepository(source).resolveArtifact(artifact, unpackSource(source), result);
    }

    private ModuleVersionRepository findSourceRepository(ModuleSource originalSource) {
        ModuleVersionRepository moduleVersionRepository = repositories.get(repositorySource(originalSource).getRepositoryId());
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

    private ComponentMetaData unpackSource(ComponentMetaData component) {
        return component.withSource(repositorySource(component.getSource()).getDelegate());
    }
}
