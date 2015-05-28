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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.util.List;

public class CompositeComponentMetaDataResolver implements ComponentMetaDataResolver {
    private final List<ComponentMetaDataResolver> resolvers;

    public CompositeComponentMetaDataResolver(List<ComponentMetaDataResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        for (ComponentMetaDataResolver resolver : resolvers) {
            if (result.hasResult()) {
                return;
            }
            resolver.resolve(identifier, componentOverrideMetadata, result);
        }
    }
}
