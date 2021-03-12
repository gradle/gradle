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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.util.List;

public class ClasspathSnapshotDataSerializer extends AbstractSerializer<ClasspathSnapshotData> {
    private final ListSerializer<HashCode> hashSerializer = new ListSerializer<>(new HashCodeSerializer());

    @Override
    public ClasspathSnapshotData read(Decoder decoder) throws Exception {
        List<HashCode> hashes = hashSerializer.read(decoder);
        return new ClasspathSnapshotData(hashes);
    }

    @Override
    public void write(Encoder encoder, ClasspathSnapshotData value) throws Exception {
        hashSerializer.write(encoder, value.getFileHashes());
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ClasspathSnapshotDataSerializer rhs = (ClasspathSnapshotDataSerializer) obj;
        return Objects.equal(hashSerializer, rhs.hashSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), hashSerializer);
    }
}
