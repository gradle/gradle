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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.Serializer;

import java.util.Map;

public class ClasspathEntrySnapshotDataSerializer extends AbstractSerializer<ClasspathEntrySnapshotData> {

    private final MapSerializer<String, HashCode> mapSerializer;
    private final Serializer<ClassSetAnalysisData> analysisSerializer;
    private final HashCodeSerializer hashCodeSerializer;

    public ClasspathEntrySnapshotDataSerializer(StringInterner interner) {
        hashCodeSerializer = new HashCodeSerializer();
        mapSerializer = new MapSerializer<String, HashCode>(new InterningStringSerializer(interner), hashCodeSerializer);
        analysisSerializer = new ClassSetAnalysisData.Serializer(interner);
    }

    @Override
    public ClasspathEntrySnapshotData read(Decoder decoder) throws Exception {
        HashCode hash = hashCodeSerializer.read(decoder);
        Map<String, HashCode> hashes = mapSerializer.read(decoder);
        ClassSetAnalysisData data = analysisSerializer.read(decoder);
        return new ClasspathEntrySnapshotData(hash, hashes, data);
    }

    @Override
    public void write(Encoder encoder, ClasspathEntrySnapshotData value) throws Exception {
        hashCodeSerializer.write(encoder, value.getHash());
        mapSerializer.write(encoder, value.getHashes());
        analysisSerializer.write(encoder, value.getClassAnalysis());
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ClasspathEntrySnapshotDataSerializer rhs = (ClasspathEntrySnapshotDataSerializer) obj;
        return Objects.equal(mapSerializer, rhs.mapSerializer)
            && Objects.equal(analysisSerializer, rhs.analysisSerializer)
            && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mapSerializer, analysisSerializer, hashCodeSerializer);
    }
}
