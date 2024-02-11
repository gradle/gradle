/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link Capability}.
 */
public class CapabilitySerializer implements Serializer<Capability> {

    @Override
    public ImmutableCapability read(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String name = decoder.readString();
        String version = decoder.readNullableString();
        return new DefaultImmutableCapability(group, name, version);
    }

    @Override
    public void write(Encoder encoder, Capability value) throws IOException {
        encoder.writeString(value.getGroup());
        encoder.writeString(value.getName());
        encoder.writeNullableString(value.getVersion());
    }
}
