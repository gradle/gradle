/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.NamedVariantIdentifier;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

/**
 * A thread-safe and reusable serializer for {@link VariantIdentifier}.
 */
public class VariantIdentifierSerializer extends AbstractSerializer<VariantIdentifier> {

    private final Serializer<ComponentIdentifier> componentIdentifierSerializer;

    public VariantIdentifierSerializer(Serializer<ComponentIdentifier> componentIdentifierSerializer) {
        this.componentIdentifierSerializer = componentIdentifierSerializer;
    }

    @Override
    public VariantIdentifier read(Decoder decoder) throws Exception {
        ComponentIdentifier componentId = componentIdentifierSerializer.read(decoder);
        String name = decoder.readString();
        return new NamedVariantIdentifier(componentId, name);
    }

    @Override
    public void write(Encoder encoder, VariantIdentifier value) throws Exception {
        componentIdentifierSerializer.write(encoder, value.getComponentId());
        if (!(value instanceof NamedVariantIdentifier)) {
            throw new IllegalArgumentException("Unsupported variant identifier type: " + value.getClass());
        }
        encoder.writeString(((NamedVariantIdentifier) value).getName());
    }

}
