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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.serialize.*;

import java.util.Map;

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class JarSnapshotDataSerializer implements Serializer<JarSnapshotData> {

    private final MapSerializer<String, HashValue> mapSerializer;
    private final Serializer<ClassSetAnalysisData> analysisSerializer;
    private final HashValueSerializer hashValueSerializer;

    public JarSnapshotDataSerializer() {
        hashValueSerializer = new HashValueSerializer();
        mapSerializer = new MapSerializer<String, HashValue>(STRING_SERIALIZER, hashValueSerializer);
        analysisSerializer = new ClassSetAnalysisData.Serializer();
    }

    @Override
    public JarSnapshotData read(Decoder decoder) throws Exception {
        HashValue hash = hashValueSerializer.read(decoder);
        Map<String, HashValue> hashes = mapSerializer.read(decoder);
        ClassSetAnalysisData data = analysisSerializer.read(decoder);
        return new JarSnapshotData(hash, hashes, data);
    }

    @Override
    public void write(Encoder encoder, JarSnapshotData value) throws Exception {
        hashValueSerializer.write(encoder, value.hash);
        mapSerializer.write(encoder, value.hashes);
        analysisSerializer.write(encoder, value.data);
    }
}
