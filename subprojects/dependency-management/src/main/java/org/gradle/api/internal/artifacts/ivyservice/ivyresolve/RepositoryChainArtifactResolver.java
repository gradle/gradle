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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet.NO_ARTIFACTS;

class RepositoryChainArtifactResolver implements ArtifactResolver, OriginArtifactSelector {
    private final Map<String, ModuleComponentRepository> repositories = new LinkedHashMap<>();
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ArtifactTypeRegistry artifactTypeRegistry;

    public RepositoryChainArtifactResolver(CalculatedValueContainerFactory calculatedValueContainerFactory, ArtifactTypeRegistry artifactTypeRegistry) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.artifactTypeRegistry = artifactTypeRegistry;
    }

    void add(ModuleComponentRepository repository) {
        repositories.put(repository.getId(), repository);
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSources());
        // First try to determine the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveArtifactsWithType(component, artifactType, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result);
        }
    }

    @Nullable
    @Override
    public ArtifactSet resolveArtifacts(ComponentResolveMetadata component, ConfigurationMetadata configuration, ArtifactTypeRegistry artifactTypeRegistry, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
        if (component.getSources() == null) {
            // virtual components have no source
            return NO_ARTIFACTS;
        }
        ModuleComponentRepository sourceRepository = findSourceRepository(component.getSources());
        Set<? extends VariantResolveMetadata> variants = configuration.getVariants();
        return DefaultArtifactSet.createFromVariantMetadata(component.getId(), component.getModuleVersionId(), component.getSources(), exclusions, variants, component.getAttributesSchema(), this, sourceRepository.getArtifactCache(), artifactTypeRegistry, overriddenAttributes, calculatedValueContainerFactory);
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources sources, BuildableArtifactResolveResult result) {
        ModuleComponentRepository sourceRepository = findSourceRepository(sources);

        // First try to resolve the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveArtifact(artifact, sources, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifact(artifact, sources, result);
        }
    }

    private ModuleComponentRepository findSourceRepository(ModuleSources sources) {
        RepositoryChainModuleSource repositoryChainModuleSource = sources.getSource(RepositoryChainModuleSource.class).get();
        ModuleComponentRepository moduleVersionRepository = repositories.get(repositoryChainModuleSource.getRepositoryId());
        if (moduleVersionRepository == null) {
            throw new IllegalStateException("Attempting to resolve artifacts from invalid repository");
        }
        return moduleVersionRepository;
    }

}
