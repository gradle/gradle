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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

/**
 * Takes a dependency->meta-data resolver and presents it as separate dependency->id and id->meta-data resolvers.
 *
 * Short-circuits the dependency->id resolution for static versions.
 */
public class RepositoryChainAdapter implements DependencyToComponentIdResolver, ComponentMetaDataResolver {
    private final DependencyToComponentIdResolver dynamicRevisionResolver;
    private final DependencyToComponentResolver metaDataResolver;
    private final VersionMatcher versionMatcher;

    public RepositoryChainAdapter(DependencyToComponentIdResolver dynamicRevisionResolver, DependencyToComponentResolver metaDataResolver, VersionMatcher versionMatcher) {
        this.dynamicRevisionResolver = dynamicRevisionResolver;
        this.metaDataResolver = metaDataResolver;
        this.versionMatcher = versionMatcher;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        ModuleVersionSelector requested = dependency.getRequested();
        if (versionMatcher.isDynamic(requested.getVersion())) {
            dynamicRevisionResolver.resolve(dependency, result);
        } else {
            DefaultModuleComponentIdentifier id = new DefaultModuleComponentIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());
            DefaultModuleVersionIdentifier mvId = new DefaultModuleVersionIdentifier(requested.getGroup(), requested.getName(), requested.getVersion());
            result.resolved(id, mvId);
        }
    }

    public void resolve(DependencyMetaData dependency, ComponentIdentifier identifier, BuildableComponentResolveResult result) {
        if (!(identifier instanceof ModuleComponentIdentifier)) {
            throw new UnsupportedOperationException("Can resolve meta-data for module components only.");
        }

        // Force the requested version
        ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) identifier;
        dependency = dependency.withRequestedVersion(new DefaultModuleVersionSelector(moduleId.getGroup(), moduleId.getModule(), moduleId.getVersion()));

        metaDataResolver.resolve(dependency, result);
    }
}
