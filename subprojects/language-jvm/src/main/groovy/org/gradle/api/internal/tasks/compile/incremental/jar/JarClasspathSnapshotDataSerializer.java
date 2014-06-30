package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.messaging.serialize.*;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static org.gradle.messaging.serialize.BaseSerializerFactory.*;

public class JarClasspathSnapshotDataSerializer implements Serializer<JarClasspathSnapshotData> {

    private final MapSerializer<File, byte[]> mapSerializer = new MapSerializer<File, byte[]>(FILE_SERIALIZER, BYTE_ARRAY_SERIALIZER);
    private final SetSerializer<String> setSerializer = new SetSerializer<String>(STRING_SERIALIZER);

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
