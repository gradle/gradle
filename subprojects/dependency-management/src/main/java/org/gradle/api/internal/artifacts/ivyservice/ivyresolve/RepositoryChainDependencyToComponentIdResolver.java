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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadataWrapper;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

import javax.annotation.Nullable;

public class RepositoryChainDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
    private final DynamicVersionResolver dynamicRevisionResolver;
    private final AttributeContainer consumerAttributes;

    public RepositoryChainDependencyToComponentIdResolver(VersionedComponentChooser componentChooser, Transformer<ModuleComponentResolveMetadata, RepositoryChainModuleResolution> metaDataFactory, VersionParser versionParser, AttributeContainer consumerAttributes, ImmutableAttributesFactory attributesFactory, ComponentMetadataProcessorFactory componentMetadataProcessorFactory, ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor, CachePolicy cachePolicy) {
        this.dynamicRevisionResolver = new DynamicVersionResolver(componentChooser, versionParser, metaDataFactory, attributesFactory, componentMetadataProcessorFactory, componentMetadataSupplierRuleExecutor, cachePolicy);
        this.consumerAttributes = consumerAttributes;
    }

    public void add(ModuleComponentRepository repository) {
        dynamicRevisionResolver.add(repository);
    }

    @Override
    public void resolve(DependencyMetadata dependency, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
        ComponentSelector componentSelector = dependency.getSelector();
        if (componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) componentSelector;
            if (acceptor.isDynamic()) {
                dynamicRevisionResolver.resolve(toModuleDependencyMetadata(dependency), acceptor, rejector, consumerAttributes, result);
            } else {
                String version = acceptor.getSelector();
                ModuleIdentifier moduleId = module.getModuleIdentifier();
                ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId(moduleId, version);
                ModuleVersionIdentifier mvId = DefaultModuleVersionIdentifier.newId(moduleId, version);
                if (rejector != null && rejector.accept(version)) {
                    result.rejected(id, mvId);
                } else {
                    result.resolved(id, mvId);
                }
            }
        }
    }

    private ModuleDependencyMetadata toModuleDependencyMetadata(DependencyMetadata dependency) {
        if (dependency instanceof ModuleDependencyMetadata) {
            return (ModuleDependencyMetadata) dependency;
        }
        if (dependency.getSelector() instanceof ModuleComponentSelector) {
            return new ModuleDependencyMetadataWrapper(dependency);
        }
        throw new IllegalArgumentException("Not a module dependency: " + dependency);

    }
}
