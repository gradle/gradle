/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.hash.Hasher;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStore;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

public class CachingFileSnapshotter implements FileSnapshotter, FileTreeElementSnapshotter {
    private final PersistentIndexedCache<String, FileInfo> cache;
    private final Hasher hasher;
    private final FileInfoSerializer serializer = new FileInfoSerializer();
    private final StringInterner stringInterner;

    public CachingFileSnapshotter(Hasher hasher, PersistentStore store, StringInterner stringInterner) {
        this.hasher = hasher;
        this.cache = store.createCache("fileHashes", String.class, serializer);
        this.stringInterner = stringInterner;
    }

    public FileInfo snapshot(File file) {
        return snapshot(file, file.length(), file.lastModified());
    }

    public FileInfo snapshot(FileTreeElement file) {
        return snapshot(file.getFile(), file.getSize(), file.getLastModified());
    }

    private FileInfo snapshot(File file, long length, long timestamp) {
        String absolutePath = file.getAbsolutePath();
        FileInfo info = cache.get(absolutePath);

        if (info != null && length == info.length && timestamp == info.timestamp) {
            return info;
        }

        byte[] hash = hasher.hash(file);
        info = new FileInfo(hash, length, timestamp);
        cache.put(stringInterner.intern(absolutePath), info);
        return info;
    }

    public static class FileInfo implements FileSnapshot {
        private final byte[] hash;
        private final long timestamp;
        private final long length;

        public FileInfo(byte[] hash, long length, long timestamp) {
            this.hash = hash;
            this.length = length;
            this.timestamp = timestamp;
        }

        public byte[] getHash() {
            return hash;
        }
    }

    private static class FileInfoSerializer implements Serializer<FileInfo> {
        public FileInfo read(Decoder decoder) throws Exception {
            byte[] hash = decoder.readBinary();
            long timestamp = decoder.readLong();
            long length = decoder.readLong();
            return new FileInfo(hash, length, timestamp);
        }

        public void write(Encoder encoder, FileInfo value) throws Exception {
            encoder.writeBinary(value.hash);
            encoder.writeLong(value.timestamp);
            encoder.writeLong(value.length);
        }
    }
}
