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
import org.gradle.api.internal.artifacts.VersionConstraintInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;

public class RepositoryChainDependencyToComponentIdResolver implements DependencyToComponentIdResolver {
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

    public void resolve(DependencyMetadata dependency, ModuleIdentifier targetModuleId, BuildableComponentIdResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        VersionConstraintInternal constraint = (VersionConstraintInternal) requested.getVersionConstraint();
        VersionSelector preferredSelector = constraint.getPreferredSelector(versionSelectorScheme);
        if (preferredSelector.isDynamic()) {
            dynamicRevisionResolver.resolve(dependency, preferredSelector, result);
        } else {
            String version;
            if (preferredSelector instanceof ExactVersionSelector) {
                version = ((ExactVersionSelector) preferredSelector).getSelector();
            } else {
                version = requested.getVersion();
            }
            DefaultModuleComponentIdentifier id = new DefaultModuleComponentIdentifier(requested.getGroup(), requested.getName(), version);
            ModuleVersionIdentifier mvId = moduleIdentifierFactory.moduleWithVersion(targetModuleId, requested.getVersion());
            result.resolved(id, mvId);
        }
        if (result.hasResult()) {
            VersionSelector rejectSelector = constraint.getRejectionSelector(versionSelectorScheme);
            result.setSelectors(preferredSelector, rejectSelector);
        }
    }

}
