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

import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;

public class DelegatingComponentResolvers<T extends ArtifactResolver, U extends DependencyToComponentIdResolver, V extends ComponentMetaDataResolver> implements ComponentResolvers {
    private final T artifactResolver;
    private final U componentIdResolver;
    private final V componentResolver;

    public static <T extends ArtifactResolver, U extends DependencyToComponentIdResolver, V extends ComponentMetaDataResolver> ComponentResolvers of(
        T artifactResolver,
        U componentIdResolver,
        V componentResolver
    ) {
        return new DelegatingComponentResolvers<T, U, V>(artifactResolver, componentIdResolver, componentResolver);
    }

    public static <E extends ArtifactResolver & DependencyToComponentIdResolver & ComponentMetaDataResolver> ComponentResolvers of(E delegate) {
        return new DelegatingComponentResolvers<E, E, E>(delegate, delegate, delegate);
    }

    public DelegatingComponentResolvers(T artifactResolver, U componentIdResolver, V componentResolver) {
        this.artifactResolver = artifactResolver;
        this.componentIdResolver = componentIdResolver;
        this.componentResolver = componentResolver;
    }

    @Override
    public T getArtifactResolver() {
        return artifactResolver;
    }

    @Override
    public U getComponentIdResolver() {
        return componentIdResolver;
    }

    @Override
    public V getComponentResolver() {
        return componentResolver;
    }
}
