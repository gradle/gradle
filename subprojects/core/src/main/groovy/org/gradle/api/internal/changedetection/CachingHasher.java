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
package org.gradle.api.internal.changedetection;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.Serializer;

import java.io.*;

public class CachingHasher implements Hasher {
    private final PersistentIndexedCache<File, FileInfo> cache;
    private final Hasher hasher;
    private long timestamp;

    public CachingHasher(Hasher hasher, CacheRepository cacheRepository) {
        this.hasher = hasher;
        cache = cacheRepository.cache("fileHashes").open().openIndexedCache(new FileInfoSerializer());
    }

    public byte[] hash(File file) {
        FileInfo info = cache.get(file);

        long length = file.length();
        timestamp = file.lastModified();
        if (info != null && length == info.length && timestamp == info.timestamp) {
            return info.hash;
        }

        byte[] hash = hasher.hash(file);
        cache.put(file, new FileInfo(hash, length, timestamp));
        return hash;
    }

    public static class FileInfo implements Serializable {
        private final byte[] hash;
        private final long timestamp;
        private final long length;

        public FileInfo(byte[] hash, long length, long timestamp) {
            this.hash = hash;
            this.length = length;
            this.timestamp = timestamp;
        }
    }

    private static class FileInfoSerializer implements Serializer<FileInfo> {
        public FileInfo read(InputStream instr) throws Exception {
            DataInputStream input = new DataInputStream(instr);
            int hashLength = input.readInt();
            byte[] hash = new byte[hashLength];
            input.readFully(hash);
            long timestamp = input.readLong();
            long length = input.readLong();
            return new FileInfo(hash, length, timestamp);
        }

        public void write(OutputStream outstr, FileInfo value) throws Exception {
            DataOutputStream output = new DataOutputStream(outstr);
            output.writeInt(value.hash.length);
            output.write(value.hash);
            output.writeLong(value.timestamp);
            output.writeLong(value.length);
            output.flush();
        }
    }
}
