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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;

/**
 * Default implementation of {@link ComponentDetailsSerializer}.
 */
@ThreadSafe
public class DefaultComponentDetailsSerializer implements ComponentDetailsSerializer {

    private final ComponentIdentifierSerializer componentIdSerializer;
    private final ModuleVersionIdentifierSerializer moduleVersionIdSerializer;

    public DefaultComponentDetailsSerializer(
        ComponentIdentifierSerializer componentIdSerializer,
        ModuleVersionIdentifierSerializer moduleVersionIdSerializer
    ) {
        this.componentIdSerializer = componentIdSerializer;
        this.moduleVersionIdSerializer = moduleVersionIdSerializer;
    }

    @Override
    public void writeComponentDetails(ComponentGraphResolveState component, Encoder encoder) throws IOException {
        componentIdSerializer.write(encoder, component.getId());
        moduleVersionIdSerializer.write(encoder, component.getMetadata().getModuleVersionId());
    }

    @Override
    public void readComponentDetails(Decoder decoder, ResolvedComponentVisitor visitor) throws IOException {
        ComponentIdentifier componentId = componentIdSerializer.read(decoder);
        ModuleVersionIdentifier moduleVersionId = moduleVersionIdSerializer.read(decoder);

        visitor.visitComponentDetails(componentId, moduleVersionId);
    }
}
