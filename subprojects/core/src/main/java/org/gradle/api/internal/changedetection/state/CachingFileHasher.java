/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.File;
import java.io.InputStream;

public class CachingFileHasher implements FileHasher {
    private final PersistentIndexedCache<String, FileInfo> cache;
    private final FileHasher delegate;
    private final StringInterner stringInterner;
    private final FileTimeStampInspector timestampInspector;

    public CachingFileHasher(FileHasher delegate, TaskHistoryStore store, StringInterner stringInterner, FileTimeStampInspector timestampInspector, String cacheName) {
        this.delegate = delegate;
        this.cache = store.createCache(cacheName, String.class, new FileInfoSerializer(), 400000, true);
        this.stringInterner = stringInterner;
        this.timestampInspector = timestampInspector;
    }

    public static boolean isLog() {
        return System.getProperty("org.gradle.internal.changes.log", "false").equalsIgnoreCase("true");
    }

    @Override
    public HashCode hash(TextResource resource) {
        File file = resource.getFile();
        if (file != null) {
            return hash(file);
        }
        return delegate.hash(resource);
    }

    @Override
    public HashCode hash(InputStream inputStream) {
        return delegate.hash(inputStream);
    }

    @Override
    public HashCode hash(File file) {
        return snapshot(file).getHash();
    }

    @Override
    public HashCode hash(FileTreeElement fileDetails) {
        return snapshot(fileDetails).getHash();
    }

    @Override
    public HashCode hash(File file, FileMetadataSnapshot fileDetails) {
        return snapshot(file, fileDetails.getLength(), fileDetails.getLastModified()).getHash();
    }

    private FileInfo snapshot(File file) {
        return snapshot(file, file.length(), file.lastModified());
    }

    private FileInfo snapshot(FileTreeElement file) {
        return snapshot(file.getFile(), file.getSize(), file.getLastModified());
    }

    private FileInfo snapshot(File file, long length, long timestamp) {
        String absolutePath = file.getAbsolutePath();
        if (timestampInspector.timestampCanBeUsedToDetectFileChange(absolutePath, timestamp)) {
            FileInfo info = cache.get(absolutePath);

            if (info != null && length == info.length && timestamp == info.timestamp) {
                return info;
            }
        }

        HashCode hash = delegate.hash(file);
        FileInfo info = new FileInfo(hash, length, timestamp);
        cache.put(stringInterner.intern(absolutePath), info);
        return info;
    }

    public void discard(String path) {
        cache.remove(path);
    }

    @VisibleForTesting
    static class FileInfo {
        private final HashCode hash;
        private final long timestamp;
        private final long length;

        public FileInfo(HashCode hash, long length, long timestamp) {
            this.hash = hash;
            this.length = length;
            this.timestamp = timestamp;
        }

        public HashCode getHash() {
            return hash;
        }
    }

    private static class FileInfoSerializer extends AbstractSerializer<FileInfo> {
        private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

        public FileInfo read(Decoder decoder) throws Exception {
            HashCode hash = hashCodeSerializer.read(decoder);
            long timestamp = decoder.readLong();
            long length = decoder.readLong();
            return new FileInfo(hash, length, timestamp);
        }

        public void write(Encoder encoder, FileInfo value) throws Exception {
            hashCodeSerializer.write(encoder, value.hash);
            encoder.writeLong(value.timestamp);
            encoder.writeLong(value.length);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            FileInfoSerializer rhs = (FileInfoSerializer) obj;
            return Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), hashCodeSerializer);
        }
    }
}
