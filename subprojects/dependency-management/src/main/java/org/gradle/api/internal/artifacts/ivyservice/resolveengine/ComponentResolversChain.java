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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ArtifactSelector;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DefaultArtifactSelector;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A factory for the various resolver services backed by a chain of repositories.
 */
public class ComponentResolversChain {
    private final DependencyToComponentIdResolverChain dependencyToComponentIdResolver;
    private final ComponentMetaDataResolverChain componentMetaDataResolver;
    private final ArtifactResolver artifactResolverChain;
    private final DefaultArtifactSelector artifactSelector;

    public ComponentResolversChain(List<ComponentResolvers> providers, ArtifactTypeRegistry artifactTypeRegistry, CalculatedValueContainerFactory calculatedValueContainerFactory, ResolvedVariantCache resolvedVariantCache) {
        List<DependencyToComponentIdResolver> depToComponentIdResolvers = new ArrayList<>(providers.size());
        List<ComponentMetaDataResolver> componentMetaDataResolvers = new ArrayList<>(1 + providers.size());
        componentMetaDataResolvers.add(VirtualComponentMetadataResolver.INSTANCE);
        List<ArtifactResolver> artifactResolvers = new ArrayList<>(providers.size());
        for (ComponentResolvers provider : providers) {
            depToComponentIdResolvers.add(provider.getComponentIdResolver());
            componentMetaDataResolvers.add(provider.getComponentResolver());
            artifactResolvers.add(provider.getArtifactResolver());
        }
        dependencyToComponentIdResolver = new DependencyToComponentIdResolverChain(depToComponentIdResolvers);
        componentMetaDataResolver = new ComponentMetaDataResolverChain(componentMetaDataResolvers);
        artifactResolverChain = new ErrorHandlingArtifactResolver(new ArtifactResolverChain(artifactResolvers));
        artifactSelector = new DefaultArtifactSelector(artifactResolverChain, artifactTypeRegistry, calculatedValueContainerFactory, resolvedVariantCache);
    }

    public ArtifactSelector getArtifactSelector() {
        return artifactSelector;
    }

    public DependencyToComponentIdResolver getComponentIdResolver() {
        return dependencyToComponentIdResolver;
    }

    public ComponentMetaDataResolver getComponentResolver() {
        return componentMetaDataResolver;
    }

    public ArtifactResolver getArtifactResolver() {
        return artifactResolverChain;
    }

    private static class ComponentMetaDataResolverChain implements ComponentMetaDataResolver {
        private final List<ComponentMetaDataResolver> resolvers;

        public ComponentMetaDataResolverChain(List<ComponentMetaDataResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
            for (ComponentMetaDataResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolve(identifier, componentOverrideMetadata, result);
            }
        }

        @Override
        public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
            for (ComponentMetaDataResolver resolver : resolvers) {
                if (!resolver.isFetchingMetadataCheap(identifier)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class ArtifactResolverChain implements ArtifactResolver {
        private final List<ArtifactResolver> resolvers;

        private ArtifactResolverChain(List<ArtifactResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifact(component, artifact, result);
            }
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifactsWithType(component, artifactType, result);
            }
        }
    }

    private static class DependencyToComponentIdResolverChain implements DependencyToComponentIdResolver {
        // Using an array here because we're going to iterate pretty often and it avoids the creation of an iterator
        // that checks for concurrent modification
        private final DependencyToComponentIdResolver[] resolvers;

        public DependencyToComponentIdResolverChain(List<DependencyToComponentIdResolver> resolvers) {
            this.resolvers = resolvers.toArray(new DependencyToComponentIdResolver[0]);
        }

        @Override
        public void resolve(DependencyMetadata dependency, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
            for (DependencyToComponentIdResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolve(dependency, acceptor, rejector, result);
            }
        }
    }
}
