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

import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.hash.FileContentType;
import org.gradle.internal.hash.FileInfo;
import org.gradle.internal.hash.FileInfoCollector;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;

import java.io.File;

public class CachingFileInfoCollector implements FileInfoCollector {
    private final PersistentIndexedCache<String, FileInfo> cache;
    private final FileInfoCollector delegate;
    private final FileSystem fileSystem;
    private final StringInterner stringInterner;
    private final FileTimeStampInspector timestampInspector;
    private final FileHasherStatistics.Collector statisticsCollector;

    public CachingFileInfoCollector(
        FileInfoCollector delegate,
        CrossBuildFileHashCache store,
        StringInterner stringInterner,
        FileTimeStampInspector timestampInspector,
        String cacheName,
        FileSystem fileSystem,
        int inMemorySize,
        FileHasherStatistics.Collector statisticsCollector
    ) {
        this.delegate = delegate;
        this.fileSystem = fileSystem;
        this.cache = store.createCache(
            PersistentIndexedCacheParameters.of(cacheName, new InterningStringSerializer(stringInterner), new FileInfoSerializer()),
            inMemorySize,
            true);
        this.stringInterner = stringInterner;
        this.timestampInspector = timestampInspector;
        this.statisticsCollector = statisticsCollector;
    }

    @Override
    public String toString() {
        return "{hasher cache: " + cache + "}";
    }

    @Override
    public HashCode hash(File file) {
        return collect(file).getHash();
    }

    @Override
    public HashCode hash(File file, long length, long lastModified) {
        return collect(file, length, lastModified).getHash();
    }

    private FileInfo collect(File file) {
        FileMetadata fileMetadata = fileSystem.stat(file);
        return collect(file, fileMetadata.getLength(), fileMetadata.getLastModified());
    }

    @Override
    public FileInfo collect(File file, long length, long timestamp) {
        String absolutePath = file.getAbsolutePath();
        if (timestampInspector.timestampCanBeUsedToDetectFileChange(absolutePath, timestamp)) {
            FileInfo info = cache.getIfPresent(absolutePath);

            if (info != null && length == info.getLength() && timestamp == info.getTimestamp()) {
                return info;
            }
        }

        FileInfo info = delegate.collect(file, length, timestamp);
        cache.put(stringInterner.intern(absolutePath), info);
        statisticsCollector.reportFileHashed(length);
        return info;
    }

    public void discard(String path) {
        cache.remove(path);
    }

    private static class FileInfoSerializer extends AbstractSerializer<FileInfo> {
        private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();

        @Override
        public FileInfo read(Decoder decoder) throws Exception {
            HashCode hash = hashCodeSerializer.read(decoder);
            long timestamp = decoder.readLong();
            long length = decoder.readLong();
            String contentType = decoder.readString();
            return new FileInfo(hash, length, timestamp, FileContentType.valueOf(contentType));
        }

        @Override
        public void write(Encoder encoder, FileInfo value) throws Exception {
            hashCodeSerializer.write(encoder, value.getHash());
            encoder.writeLong(value.getTimestamp());
            encoder.writeLong(value.getLength());
            encoder.writeString(value.getContentType().name());
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
