/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyAwareModuleVersionRepository;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;

/**
 * A wrapper over a {@link org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver} that is exposed through the DSL,
 * for backwards compatibility.
 */
public class LegacyDependencyResolver extends ExternalResourceResolverDependencyResolver implements ResolutionAwareRepository {

    private final IvyAwareModuleVersionRepository repository;
    // TODO:DAZ This isn't required except for a unit test: fix this
    private final ExternalResourceResolver resolver;

    public LegacyDependencyResolver(ExternalResourceResolver resolver, IvyAwareModuleVersionRepository repository) {
        super(resolver);
        this.resolver = resolver;
        this.repository = repository;
    }

    public IvyAwareModuleVersionRepository createResolver() {
        return repository;
    }

    public ArtifactOrigin locate(Artifact artifact) {
        // This is never used
        throw new UnsupportedOperationException();
    }
}
