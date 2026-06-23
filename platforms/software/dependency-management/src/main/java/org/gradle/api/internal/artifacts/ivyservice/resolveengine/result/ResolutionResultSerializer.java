/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A serializer for {@link ResolutionResult} that is not thread-safe and not reusable.
 */
@NotThreadSafe
public class ResolutionResultSerializer implements Serializer<ResolutionResult> {

    private final Serializer<ResolvedComponentResult> componentResultSerializer;
    private final Serializer<AttributeContainer> attributeContainerSerializer;

    public ResolutionResultSerializer(
        Serializer<ResolvedComponentResult> componentResultSerializer,
        Serializer<AttributeContainer> attributeContainerSerializer
    ) {
        this.componentResultSerializer = componentResultSerializer;
        this.attributeContainerSerializer = attributeContainerSerializer;
    }

    @Override
    public ResolutionResult read(Decoder decoder) throws Exception {
        throw new UnsupportedOperationException("This serializer is only intended for input snapshotting.");
    }

    @Override
    public void write(Encoder encoder, ResolutionResult value) throws Exception {
        componentResultSerializer.write(encoder, value.getRootComponent().get());
        attributeContainerSerializer.write(encoder, value.getRequestedAttributes());
    }

}
