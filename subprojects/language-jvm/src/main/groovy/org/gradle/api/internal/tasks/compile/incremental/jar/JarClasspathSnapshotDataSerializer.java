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

import org.gradle.messaging.serialize.*;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.gradle.messaging.serialize.BaseSerializerFactory.*;

public class JarClasspathSnapshotDataSerializer implements Serializer<JarClasspathSnapshotData> {

    private final MapSerializer<File, byte[]> mapSerializer = new MapSerializer<File, byte[]>(FILE_SERIALIZER, BYTE_ARRAY_SERIALIZER);
    private final SetSerializer<String> setSerializer = new SetSerializer<String>(STRING_SERIALIZER, false);

    public JarClasspathSnapshotData read(Decoder decoder) throws Exception {
        Set<String> duplicates = setSerializer.read(decoder);
        Map<File, byte[]> hashes = mapSerializer.read(decoder);
        return new JarClasspathSnapshotData(hashes, duplicates);
    }

    public void write(Encoder encoder, JarClasspathSnapshotData value) throws Exception {
        setSerializer.write(encoder, value.getDuplicateClasses());
        mapSerializer.write(encoder, value.getJarHashes());
    }
}
