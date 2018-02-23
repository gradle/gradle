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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class JarClasspathSnapshotDataSerializer extends AbstractSerializer<JarClasspathSnapshotData> {
    private final MapSerializer<File, HashCode> mapSerializer = new MapSerializer<File, HashCode>(FILE_SERIALIZER, new HashCodeSerializer());
    private final SetSerializer<String> setSerializer = new SetSerializer<String>(STRING_SERIALIZER, false);

    @Override
    public JarClasspathSnapshotData read(Decoder decoder) throws Exception {
        Set<String> duplicates = setSerializer.read(decoder);
        Map<File, HashCode> hashes = mapSerializer.read(decoder);
        return new JarClasspathSnapshotData(hashes, duplicates);
    }

    @Override
    public void write(Encoder encoder, JarClasspathSnapshotData value) throws Exception {
        setSerializer.write(encoder, value.getDuplicateClasses());
        mapSerializer.write(encoder, value.getJarHashes());
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        JarClasspathSnapshotDataSerializer rhs = (JarClasspathSnapshotDataSerializer) obj;
        return Objects.equal(mapSerializer, rhs.mapSerializer) && Objects.equal(setSerializer, rhs.setSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), mapSerializer, setSerializer);
    }
}
