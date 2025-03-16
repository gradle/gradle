/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.external.model;

import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.external.model.ivy.DefaultIvyComponentGraphResolveState;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.MavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DefaultExternalModuleComponentGraphResolveState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.BuildTree.class)
public class ModuleComponentGraphResolveStateFactory {
    private final ComponentIdGenerator idGenerator;
    private final AttributeDesugaring attributeDesugaring;

    public ModuleComponentGraphResolveStateFactory(ComponentIdGenerator idFactory, AttributeDesugaring attributeDesugaring) {
        this.idGenerator = idFactory;
        this.attributeDesugaring = attributeDesugaring;
    }

    public ExternalModuleComponentGraphResolveState stateFor(ModuleComponentResolveMetadata metadata) {
        if (metadata instanceof IvyModuleResolveMetadata) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metadata;
            return new DefaultIvyComponentGraphResolveState(idGenerator.nextComponentId(), ivyMetadata, attributeDesugaring, idGenerator);
        } else if (metadata instanceof MavenModuleResolveMetadata) {
            return new DefaultExternalModuleComponentGraphResolveState<>(idGenerator.nextComponentId(), metadata, metadata, attributeDesugaring, idGenerator);
        }

        throw new IllegalArgumentException("Unsupported module component metadata type: " + metadata);
    }

    /**
     * @deprecated only used for client module components.
     */
    @Deprecated
    public ComponentGraphResolveState stateFor(ExternalModuleComponentGraphResolveMetadata graphMetadata, ExternalComponentResolveMetadata artifactMetadata) {
        return new DefaultExternalModuleComponentGraphResolveState<>(idGenerator.nextComponentId(), graphMetadata, artifactMetadata, attributeDesugaring, idGenerator);
    }
}

