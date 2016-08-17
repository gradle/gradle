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
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.util.ArrayList;
import java.util.List;

public class ComponentResolversChain implements ComponentResolvers {
    private final DependencyToComponentIdResolverChain dependencyToComponentIdResolver;
    private final ComponentMetaDataResolverChain componentMetaDataResolver;
    private final ArtifactResolverChain artifactResolverChain;

    public ComponentResolversChain(List<ComponentResolvers> providers) {
        List<DependencyToComponentIdResolver> depToComponentIdResolvers = new ArrayList<DependencyToComponentIdResolver>(providers.size());
        List<ComponentMetaDataResolver> componentMetaDataResolvers = new ArrayList<ComponentMetaDataResolver>(providers.size());
        List<ArtifactResolver> artifactResolvers = new ArrayList<ArtifactResolver>(providers.size());
        for (ComponentResolvers provider : providers) {
            depToComponentIdResolvers.add(provider.getComponentIdResolver());
            componentMetaDataResolvers.add(provider.getComponentResolver());
            artifactResolvers.add(provider.getArtifactResolver());
        }
        dependencyToComponentIdResolver = new DependencyToComponentIdResolverChain(depToComponentIdResolvers);
        componentMetaDataResolver = new ComponentMetaDataResolverChain(componentMetaDataResolvers);
        artifactResolverChain = new ArtifactResolverChain(artifactResolvers);
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return dependencyToComponentIdResolver;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return componentMetaDataResolver;
    }

    @Override
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
    }

    private static class ArtifactResolverChain implements ArtifactResolver {
        private final List<ArtifactResolver> resolvers;

        private ArtifactResolverChain(List<ArtifactResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifact(artifact, moduleSource, result);
            }
        }

        @Override
        public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifacts(component, result);
            }
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifactsWithType(component, artifactType, result);
            }
        }
    }

    private static class DependencyToComponentIdResolverChain implements DependencyToComponentIdResolver {
        private final List<DependencyToComponentIdResolver> resolvers;

        public DependencyToComponentIdResolverChain(List<DependencyToComponentIdResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
            for (DependencyToComponentIdResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolve(dependency, result);
            }
        }
    }

}
