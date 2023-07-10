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

package org.gradle.internal.component.local.model;

import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;

    public LocalComponentGraphResolveStateFactory(AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        this.attributeDesugaring = attributeDesugaring;
        this.idGenerator = idGenerator;
    }

    public LocalComponentGraphResolveState stateFor(LocalComponentMetadata metadata) {
        return new DefaultLocalComponentGraphResolveState(idGenerator.nextComponentId(), metadata, attributeDesugaring, idGenerator);
    }
}
