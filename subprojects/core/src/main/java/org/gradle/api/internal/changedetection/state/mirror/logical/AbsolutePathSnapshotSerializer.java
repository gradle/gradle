/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.Map;

public class AbsolutePathSnapshotSerializer extends AbstractSerializer<Map<String, FileContentSnapshot>> {
    private final StringInterner stringInterner;
    private final ContentSnapshotSerializer contentSnapshotSerializer;

    public AbsolutePathSnapshotSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
        this.contentSnapshotSerializer = new ContentSnapshotSerializer();
    }

    @Override
    public Map<String, FileContentSnapshot> read(Decoder decoder) throws IOException {
        int snapshotsCount = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, FileContentSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < snapshotsCount; i++) {
            String absolutePath = stringInterner.intern(decoder.readString());
            FileContentSnapshot snapshot = contentSnapshotSerializer.read(decoder);
            builder.put(absolutePath, snapshot);
        }
        return builder.build();
    }

    @Override
    public void write(Encoder encoder, Map<String, FileContentSnapshot> value) throws IOException {
        encoder.writeSmallInt(value.size());
        for (Map.Entry<String, FileContentSnapshot> entry : value.entrySet()) {
            encoder.writeString(entry.getKey());
            contentSnapshotSerializer.write(encoder, entry.getValue());
        }
    }
}
