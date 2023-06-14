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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactFileResolveResult;

import javax.annotation.Nullable;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet.NO_ARTIFACTS;

class RepositoryChainArtifactResolver implements ArtifactResolver, OriginArtifactSelector {
    private final Map<String, ModuleComponentRepository<?>> repositories = new LinkedHashMap<>();
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    RepositoryChainArtifactResolver(CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    void add(ModuleComponentRepository<?> repository) {
        repositories.put(repository.getId(), repository);
    }

    @Override
    public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        ModuleComponentRepository<?> sourceRepository = findSourceRepository(component.getSources());
        // First try to determine the artifacts locally before going remote
        sourceRepository.getLocalAccess().resolveArtifactsWithType(component.getMetadata(), artifactType, result);
        if (!result.hasResult()) {
            sourceRepository.getRemoteAccess().resolveArtifactsWithType(component.getMetadata(), artifactType, result);
        }
    }

    @Nullable
    @Override
    public ArtifactSet resolveArtifacts(ComponentArtifactResolveMetadata component, ComponentArtifactResolveVariantState allVariants, Set<ResolvedVariant> legacyVariants, ImmutableAttributes overriddenAttributes) {
        if (component.getSources() == null) {
            // virtual components have no source
            return NO_ARTIFACTS;
        }
        return new DefaultArtifactSet(component.getId(), component.getAttributesSchema(), overriddenAttributes, allVariants, legacyVariants);
    }

    @Override
    public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
        ModuleComponentRepository<?> sourceRepository = findSourceRepository(component.getSources());
        ResolvableArtifact resolvableArtifact = sourceRepository.getArtifactCache().computeIfAbsent(artifact.getId(), id -> {
            CalculatedValue<File> artifactSource = calculatedValueContainerFactory.create(Describables.of(artifact.getId()), (Supplier<File>)() -> resolveArtifactLater(artifact, component.getSources(), sourceRepository));
            return new DefaultResolvableArtifact(component.getModuleVersionId(), artifact.getName(), artifact.getId(), context -> context.add(artifact.getBuildDependencies()), artifactSource, calculatedValueContainerFactory);
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
        RepositoryChainModuleSource repositoryChainModuleSource = sources.getSource(RepositoryChainModuleSource.class).get();
        ModuleComponentRepository<?> moduleVersionRepository = repositories.get(repositoryChainModuleSource.getRepositoryId());
        if (moduleVersionRepository == null) {
            throw new IllegalStateException("Attempting to resolve artifacts from invalid repository");
        }
        return moduleVersionRepository;
    }

}
