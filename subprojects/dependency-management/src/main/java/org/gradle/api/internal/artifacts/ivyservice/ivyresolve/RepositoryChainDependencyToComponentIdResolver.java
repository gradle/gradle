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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadataWrapper;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public class RepositoryChainDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
    private final DynamicVersionResolver dynamicRevisionResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final VersionSelectorScheme versionSelectorScheme;

    public RepositoryChainDependencyToComponentIdResolver(VersionedComponentChooser componentChooser, Transformer<ModuleComponentResolveMetadata, RepositoryChainModuleResolution> metaDataFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory, VersionSelectorScheme versionSelectorScheme) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.dynamicRevisionResolver = new DynamicVersionResolver(componentChooser, metaDataFactory);
    }

    public void add(ModuleComponentRepository repository) {
        dynamicRevisionResolver.add(repository);
    }

    public void resolve(DependencyMetadata dependency, BuildableComponentIdResolveResult result) {
        ComponentSelector componentSelector = dependency.getSelector();
        if (componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector module = (ModuleComponentSelector) componentSelector;
            VersionConstraint raw = module.getVersionConstraint();
            ResolvedVersionConstraint resolvedVersionConstraint = new DefaultResolvedVersionConstraint(raw, versionSelectorScheme);
            VersionSelector preferredSelector = resolvedVersionConstraint.getPreferredSelector();
            if (preferredSelector.isDynamic()) {
                dynamicRevisionResolver.resolve(toModuleDependencyMetadata(dependency), preferredSelector, result);
            } else {
                String version = raw.getPreferredVersion();
                ModuleComponentIdentifier id = new DefaultModuleComponentIdentifier(module.getGroup(), module.getModule(), version);
                ModuleVersionIdentifier mvId = moduleIdentifierFactory.moduleWithVersion(module.getGroup(), module.getModule(), version);
                result.resolved(id, mvId);
            }
            if (result.hasResult()) {
                result.setResolvedVersionConstraint(resolvedVersionConstraint);
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
