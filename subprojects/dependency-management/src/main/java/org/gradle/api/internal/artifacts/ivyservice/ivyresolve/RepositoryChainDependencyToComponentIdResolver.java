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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.cache.internal.ProducerGuard;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public class RepositoryChainDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
    private final ProducerGuard<ModuleIdentifier> producerGuard = ProducerGuard.adaptive();
    private final VersionSelectorScheme versionSelectorScheme;
    private final DynamicVersionResolver dynamicRevisionResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    public RepositoryChainDependencyToComponentIdResolver(VersionSelectorScheme versionSelectorScheme, VersionedComponentChooser componentChooser, Transformer<ModuleComponentResolveMetadata, RepositoryChainModuleResolution> metaDataFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.versionSelectorScheme = versionSelectorScheme;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.dynamicRevisionResolver = new DynamicVersionResolver(componentChooser, metaDataFactory);
    }

    public void add(ModuleComponentRepository repository) {
        dynamicRevisionResolver.add(repository);
    }

    public void resolve(final DependencyMetadata dependency, final BuildableComponentIdResolveResult result) {
        ModuleIdentifier identifier = moduleIdentifierFactory.module(dependency.getRequested().getGroup(), dependency.getRequested().getName());
        producerGuard.guardByKey(identifier, new Factory<Void>() {
            @Override
            public Void create() {
                ModuleVersionSelector requested = dependency.getRequested();
                if (versionSelectorScheme.parseSelector(requested.getVersion()).isDynamic()) {
                    dynamicRevisionResolver.resolve(dependency, result);
                } else {
                    DefaultModuleComponentIdentifier id = new DefaultModuleComponentIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());
                    ModuleVersionIdentifier mvId = moduleIdentifierFactory.moduleWithVersion(requested.getGroup(), requested.getName(), requested.getVersion());
                    result.resolved(id, mvId);
                }
                return null;
            }
        });
    }
}
