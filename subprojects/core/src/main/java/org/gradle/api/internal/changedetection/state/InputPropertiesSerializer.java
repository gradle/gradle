/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.SnapshotSerializer;

import java.util.Map;

class InputPropertiesSerializer implements Serializer<ImmutableMap<String, ValueSnapshot>> {

    private final Serializer<ValueSnapshot> snapshotSerializer = new SnapshotSerializer();

    public ImmutableSortedMap<String, ValueSnapshot> read(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableSortedMap.of();
        }
        if (size == 1) {
            return ImmutableSortedMap.of(decoder.readString(), readSnapshot(decoder));
        }

        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < size; i++) {
            builder.put(decoder.readString(), readSnapshot(decoder));
        }
        return builder.build();
    }

    private ValueSnapshot readSnapshot(Decoder decoder) throws Exception {
        return snapshotSerializer.read(decoder);
    }

    public void write(Encoder encoder, ImmutableMap<String, ValueSnapshot> properties) throws Exception {
        encoder.writeSmallInt(properties.size());
        for (Map.Entry<String, ValueSnapshot> entry : properties.entrySet()) {
            encoder.writeString(entry.getKey());
            writeSnapshot(encoder, entry.getValue());
        }
    }

    private void writeSnapshot(Encoder encoder, ValueSnapshot snapshot) throws Exception {
        snapshotSerializer.write(encoder, snapshot);
    }
}
