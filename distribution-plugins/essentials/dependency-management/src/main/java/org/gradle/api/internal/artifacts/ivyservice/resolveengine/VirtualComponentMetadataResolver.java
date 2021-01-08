/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

/**
 * A component metadata resolver which should be used first in chain of resolvers as it will make sure
 * that we never "find" a virtual component, by shortcutting resolution if we find the marker interface
 * for virtual components.
 */
class VirtualComponentMetadataResolver implements ComponentMetaDataResolver {

    public static final ComponentMetaDataResolver INSTANCE = new VirtualComponentMetadataResolver();

    private VirtualComponentMetadataResolver() {

    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (identifier instanceof VirtualComponentIdentifier && identifier instanceof ModuleComponentIdentifier) {
            result.notFound((ModuleComponentIdentifier) identifier);
        }
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        return true;
    }
}
