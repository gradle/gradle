/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.legacy;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyResolverBackedModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleVersionRepository;
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;

public class FixedResolverArtifactRepository extends AbstractArtifactRepository {
    protected final DependencyResolver resolver;

    public FixedResolverArtifactRepository(DependencyResolver resolver) {
        this.resolver = resolver;
    }

    public String getName() {
        return resolver.getName();
    }

    public void setName(String name) {
        resolver.setName(name);

        // We are doing this because we are relying on the deprecation warning that
        // AbstractArtifactRepository (super) issues. This is a bit awkward.
        super.setName(name);
    }

    public ModuleVersionPublisher createPublisher() {
        return new IvyResolverBackedModuleVersionPublisher(resolver);
    }

    public ConfiguredModuleVersionRepository createResolver() {
        // Handle a repository wrapped in a resolver for backwards compatibility
        if (resolver instanceof ResolutionAwareRepository) {
            ResolutionAwareRepository resolutionAwareRepository = (ResolutionAwareRepository) resolver;
            return resolutionAwareRepository.createResolver();
        }
        return new IvyDependencyResolverAdapter(resolver);
    }

    public DependencyResolver createLegacyDslObject() {
        return resolver;
    }
}
