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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.jspecify.annotations.Nullable;

public class RepositoryChainDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
    private final DynamicVersionResolver dynamicRevisionResolver;
    private final AttributeContainer consumerAttributes;

    public RepositoryChainDependencyToComponentIdResolver(VersionedComponentChooser componentChooser, VersionParser versionParser, AttributeContainer consumerAttributes, AttributesFactory attributesFactory, ComponentMetadataProcessorFactory componentMetadataProcessorFactory, ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor, CacheExpirationControl cacheExpirationControl) {
        this.dynamicRevisionResolver = new DynamicVersionResolver(componentChooser, versionParser, attributesFactory, componentMetadataProcessorFactory, componentMetadataSupplierRuleExecutor, cacheExpirationControl);
        this.consumerAttributes = consumerAttributes;
    }

    public void add(ModuleComponentRepository<ExternalModuleComponentGraphResolveState> repository) {
        dynamicRevisionResolver.add(repository);
    }

    @Override
    public void resolve(ComponentSelector selector, ComponentOverrideMetadata overrideMetadata, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
        if (selector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) selector;
            if (acceptor.isDynamic()) {
                dynamicRevisionResolver.resolve(module, overrideMetadata, acceptor, rejector, consumerAttributes, result);
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
}
