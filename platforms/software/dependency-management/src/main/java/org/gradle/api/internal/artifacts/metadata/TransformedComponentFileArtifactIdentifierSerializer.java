/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.function.Supplier;

/**
 * A thread-safe and reusable serializer for {@link TransformedComponentFileArtifactIdentifier}.
 */
public class TransformedComponentFileArtifactIdentifierSerializer implements Serializer<TransformedComponentFileArtifactIdentifier> {
    private final Lazy<Serializer<ComponentArtifactIdentifier>> inputArtifactIdSerializer;

    public TransformedComponentFileArtifactIdentifierSerializer(Supplier<Serializer<ComponentArtifactIdentifier>> inputArtifactIdSerializer) {
        this.inputArtifactIdSerializer = Lazy.locking().of(inputArtifactIdSerializer);
    }

    @Override
    public void write(Encoder encoder, TransformedComponentFileArtifactIdentifier value) throws Exception {
        inputArtifactIdSerializer.get().write(encoder, value.getInputArtifactId());
        encoder.writeString(value.getFileName());
    }

    @Override
    public TransformedComponentFileArtifactIdentifier read(Decoder decoder) throws Exception {
        ComponentArtifactIdentifier inputArtifactId = inputArtifactIdSerializer.get().read(decoder);
        String fileName = decoder.readString();
        return new TransformedComponentFileArtifactIdentifier(inputArtifactId, fileName);
    }
}
