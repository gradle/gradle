/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.processing;

import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.EOFException;

public class GeneratedResourceSerializer extends AbstractSerializer<GeneratedResource> {
    private static final Serializer<GeneratedResource.Location> LOCATION_SERIALIZER = new BaseSerializerFactory().getSerializerFor(GeneratedResource.Location.class);
    private final Serializer<String> stringSerializer;

    public GeneratedResourceSerializer(Serializer<String> stringSerializer) {
        this.stringSerializer = stringSerializer;
    }

    @Override
    public GeneratedResource read(Decoder decoder) throws EOFException, Exception {
        return new GeneratedResource(LOCATION_SERIALIZER.read(decoder), stringSerializer.read(decoder));
    }

    @Override
    public void write(Encoder encoder, GeneratedResource value) throws Exception {
        LOCATION_SERIALIZER.write(encoder, value.getLocation());
        stringSerializer.write(encoder, value.getPath());
    }
}
