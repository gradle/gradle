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

import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;

import java.util.Map;

import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class JarSnapshotDataSerializer extends AbstractSerializer<JarSnapshotData> {

    private final MapSerializer<String, HashCode> mapSerializer;
    private final Serializer<ClassSetAnalysisData> analysisSerializer;
    private final HashCodeSerializer hashCodeSerializer;

    public JarSnapshotDataSerializer() {
        hashCodeSerializer = new HashCodeSerializer();
        mapSerializer = new MapSerializer<String, HashCode>(STRING_SERIALIZER, hashCodeSerializer);
        analysisSerializer = new ClassSetAnalysisData.Serializer();
    }

    @Override
    public JarSnapshotData read(Decoder decoder) throws Exception {
        HashCode hash = hashCodeSerializer.read(decoder);
        Map<String, HashCode> hashes = mapSerializer.read(decoder);
        ClassSetAnalysisData data = analysisSerializer.read(decoder);
        return new JarSnapshotData(hash, hashes, data);
    }

    @Override
    public void write(Encoder encoder, JarSnapshotData value) throws Exception {
        hashCodeSerializer.write(encoder, value.hash);
        mapSerializer.write(encoder, value.hashes);
        analysisSerializer.write(encoder, value.data);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        JarSnapshotDataSerializer rhs = (JarSnapshotDataSerializer) obj;
        return Objects.equal(mapSerializer, rhs.mapSerializer)
            && Objects.equal(analysisSerializer, rhs.analysisSerializer)
            && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mapSerializer, analysisSerializer, hashCodeSerializer);
    }
}
