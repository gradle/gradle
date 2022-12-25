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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Serializes and de-serializes {@link IvyArtifactName}s.
 */
public class IvyArtifactNameSerializer extends AbstractSerializer<IvyArtifactName>  {

    public static final IvyArtifactNameSerializer INSTANCE = new IvyArtifactNameSerializer();
    private IvyArtifactNameSerializer() {
        // Private to enforce singleton.
    }

    @Override
    public IvyArtifactName read(Decoder decoder) throws IOException {
        String artifactName = decoder.readString();
        String type = decoder.readString();
        String extension = decoder.readNullableString();
        String classifier = decoder.readNullableString();
        return new DefaultIvyArtifactName(artifactName, type, extension, classifier);
    }

    @Override
    public void write(Encoder encoder, IvyArtifactName value) throws IOException {
        encoder.writeString(value.getName());
        encoder.writeString(value.getType());
        encoder.writeNullableString(value.getExtension());
        encoder.writeNullableString(value.getClassifier());
    }

    public void writeNullable(Encoder encoder, @Nullable IvyArtifactName value) throws IOException {
        if (value == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            write(encoder, value);
        }
    }

    @Nullable
    public IvyArtifactName readNullable(Decoder decoder) throws IOException {
        boolean hasArtifact = decoder.readBoolean();
        if (hasArtifact) {
            return read(decoder);
        }
        return null;
    }
}
