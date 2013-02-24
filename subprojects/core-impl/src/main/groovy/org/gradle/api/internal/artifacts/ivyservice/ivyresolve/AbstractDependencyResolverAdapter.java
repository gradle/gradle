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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.resolver.ResolverSettings;
import org.gradle.api.internal.artifacts.repositories.cachemanager.LocalFileRepositoryCacheManager;

public abstract class AbstractDependencyResolverAdapter implements IvyAwareModuleVersionRepository {
    private final DependencyResolverIdentifier identifier;
    protected final DependencyResolver resolver;

    public AbstractDependencyResolverAdapter(DependencyResolver resolver) {
        this.identifier = new DependencyResolverIdentifier(resolver);
        this.resolver = resolver;
    }

    public String getId() {
        return identifier.getUniqueId();
    }

    public String getName() {
        return identifier.getName();
    }

    @Override
    public String toString() {
        return String.format("Repository '%s'", resolver.getName());
    }

    public void setSettings(ResolverSettings settings) {
        resolver.setSettings(settings);
    }

    public boolean isLocal() {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }
}
