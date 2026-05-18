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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class ImmutableCapabilitiesSerializer implements Serializer<ImmutableCapabilities> {

    private final CapabilitySerializer capabilitySerializer = new CapabilitySerializer();

    @Override
    public ImmutableCapabilities read(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        ImmutableSet.Builder<ImmutableCapability> builder = ImmutableSet.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            builder.add(capabilitySerializer.read(decoder));
        }
        return new ImmutableCapabilities(builder.build());
    }

    @Override
    public void write(Encoder encoder, ImmutableCapabilities value) throws Exception {
        ImmutableSet<ImmutableCapability> set = value.asSet();
        encoder.writeSmallInt(set.size());
        for (ImmutableCapability capability : set) {
            capabilitySerializer.write(encoder, capability);
        }
    }

}
