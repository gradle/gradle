/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.clientmodule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.ExternalComponentGraphResolveMetadata;
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata;
import org.gradle.internal.component.external.model.ExternalVariantGraphResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadataWrapper;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@NonNullApi
public class ClientModuleResolver implements ComponentMetaDataResolver {
    private final ComponentMetaDataResolver resolver;
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final ModuleComponentGraphResolveStateFactory resolveStateFactory;

    public ClientModuleResolver(ComponentMetaDataResolver resolver, DependencyMetadataFactory dependencyMetadataFactory, ModuleComponentGraphResolveStateFactory resolveStateFactory) {
        this.resolver = resolver;
        this.dependencyMetadataFactory = dependencyMetadataFactory;
        this.resolveStateFactory = resolveStateFactory;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        resolver.resolve(identifier, componentOverrideMetadata, result);

        if (result.getFailure() != null) {
            return;
        }
        org.gradle.api.artifacts.ClientModule clientModule = componentOverrideMetadata.getClientModule();
        if (clientModule != null) {
            ModuleComponentResolveMetadata originalMetadata = (ModuleComponentResolveMetadata) result.getState().getMetadata();
            List<ModuleDependencyMetadata> clientModuleDependencies = createClientModuleDependencies(clientModule);
            ModuleComponentArtifactMetadata clientModuleArtifact = createClientModuleArtifact(originalMetadata);
            ClientModuleComponentResolveMetadata clientModuleMetaData = new ClientModuleComponentResolveMetadata(originalMetadata, clientModuleArtifact, clientModuleDependencies);

            result.setResult(resolveStateFactory.stateFor(clientModuleMetaData, clientModuleMetaData));
        }
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        return resolver.isFetchingMetadataCheap(identifier);
    }

    @SuppressWarnings("deprecation")
    private List<ModuleDependencyMetadata> createClientModuleDependencies(org.gradle.api.artifacts.ClientModule clientModule) {
        List<ModuleDependencyMetadata> dependencies = new ArrayList<>();
        for (ModuleDependency moduleDependency : clientModule.getDependencies()) {
            ModuleDependencyMetadata dependencyMetadata = createDependencyMetadata(moduleDependency);
            dependencies.add(dependencyMetadata);
        }
        return dependencies;
    }

    private ModuleComponentArtifactMetadata createClientModuleArtifact(ModuleComponentResolveMetadata metadata) {
        return metadata.artifact("jar", "jar", null);
    }

    private ModuleDependencyMetadata createDependencyMetadata(ModuleDependency moduleDependency) {
        LocalOriginDependencyMetadata dependencyMetadata = dependencyMetadataFactory.createDependencyMetadata(moduleDependency);
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            return new ClientModuleDependencyMetadataWrapper((DslOriginDependencyMetadata) dependencyMetadata);
        }
        return new ModuleDependencyMetadataWrapper(dependencyMetadata);
    }

    private static class ClientModuleComponentResolveMetadata implements ExternalComponentResolveMetadata, ExternalComponentGraphResolveMetadata {
        private final ModuleComponentResolveMetadata delegate;
        private final ModuleComponentArtifactMetadata clientModuleArtifact;
        private final List<ModuleDependencyMetadata> clientModuleDependencies;

        private ClientModuleComponentResolveMetadata(ModuleComponentResolveMetadata delegate, ModuleComponentArtifactMetadata clientModuleArtifact, List<ModuleDependencyMetadata> clientModuleDependencies) {
            this.delegate = delegate;
            this.clientModuleArtifact = clientModuleArtifact;
            this.clientModuleDependencies = clientModuleDependencies;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return delegate.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return delegate.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return delegate.getSources();
        }

        @Override
        public ImmutableAttributesSchema getAttributesSchema() {
            return delegate.getAttributesSchema();
        }

        @Override
        public Set<String> getConfigurationNames() {
            return delegate.getConfigurationNames();
        }

        @Override
        @Nullable
        public ModuleConfigurationMetadata getConfiguration(String name) {
            return new ClientModuleConfigurationMetadata(delegate.getId(), name, clientModuleArtifact, clientModuleDependencies);
        }

        @Override
        public List<? extends ExternalVariantGraphResolveMetadata> getVariantsForGraphTraversal() {
            return Collections.emptyList();
        }

        @Override
        public boolean isMissing() {
            return delegate.isMissing();
        }

        @Override
        public boolean isChanging() {
            return delegate.isChanging();
        }

        @Override
        public String getStatus() {
            return delegate.getStatus();
        }

        @Override
        public List<String> getStatusScheme() {
            return delegate.getStatusScheme();
        }

        @Override
        public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
            return ImmutableList.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return delegate.getAttributes();
        }
    }

    private static class ClientModuleConfigurationMetadata extends DefaultConfigurationMetadata {
        ClientModuleConfigurationMetadata(ModuleComponentIdentifier componentId, String name, ModuleComponentArtifactMetadata artifact, List<ModuleDependencyMetadata> dependencies) {
            super(componentId, name, true, true, ImmutableSet.of(), ImmutableList.of(artifact), VariantMetadataRules.noOp(), ImmutableList.of(), ImmutableAttributes.EMPTY, false);
            setDependencies(dependencies);
        }
    }

    private static class ClientModuleDependencyMetadataWrapper extends ModuleDependencyMetadataWrapper implements DslOriginDependencyMetadata {
        private final DslOriginDependencyMetadata delegate;

        private ClientModuleDependencyMetadataWrapper(DslOriginDependencyMetadata delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public Dependency getSource() {
            return delegate.getSource();
        }
    }
}
