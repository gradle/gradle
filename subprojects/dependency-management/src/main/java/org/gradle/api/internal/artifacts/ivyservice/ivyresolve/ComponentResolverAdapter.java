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

import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;

/**
 * Takes separate dependency->id and id->meta-data resolvers and presents them as a single dependency->meta-data resolver.
 */
public class ComponentResolverAdapter implements DependencyToComponentResolver {
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;

    public ComponentResolverAdapter(DependencyToComponentIdResolver idResolver, ComponentMetaDataResolver metaDataResolver) {
        this.idResolver = idResolver;
        this.metaDataResolver = metaDataResolver;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        DefaultBuildableComponentIdResolveResult idResult = new DefaultBuildableComponentIdResolveResult();
        idResolver.resolve(dependency, idResult);
        if (idResult.getFailure() != null) {
            idResult.applyTo(result);
            result.failed(idResult.getFailure());
            return;
        }
        if (idResult.getMetaData() != null) {
            result.resolved(idResult.getMetaData());
            return;
        }
        metaDataResolver.resolve(dependency, idResult.getId(), result);
    }
}
