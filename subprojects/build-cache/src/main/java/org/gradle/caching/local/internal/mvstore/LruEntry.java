/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal.mvstore;

import org.h2.mvstore.DataUtils;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;

import java.nio.ByteBuffer;

public class LruEntry {

    private final byte[] buildCacheKey;
    private final byte[] blobId;
    private final long lastAccessTime;

    public LruEntry(byte[] buildCacheKey, byte[] blobId, long lastAccessTime) {
        this.buildCacheKey = buildCacheKey;
        this.blobId = blobId;
        this.lastAccessTime = lastAccessTime;
    }

    public byte[] getBlobId() {
        return blobId;
    }

    public byte[] getBuildCacheKey() {
        return buildCacheKey;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    static class LruEntryType extends BasicDataType<LruEntry> {
        static final LruEntryType INSTANCE = new LruEntryType();

        @Override
        public int getMemory(LruEntry obj) {
            return Long.BYTES + obj.buildCacheKey.length + obj.blobId.length;
        }

        @Override
        public void write(WriteBuffer buff, LruEntry obj) {
            buff.putVarInt(obj.buildCacheKey.length);
            buff.put(obj.buildCacheKey);
            buff.putVarInt(obj.blobId.length);
            buff.put(obj.blobId);
            buff.putLong(obj.lastAccessTime);
        }

        @Override
        public LruEntry read(ByteBuffer buff) {
            int buildCacheKeyLength = DataUtils.readVarInt(buff);
            byte[] buildCacheKey = new byte[buildCacheKeyLength];
            buff.get(buildCacheKey);
            int blobIdLength = DataUtils.readVarInt(buff);
            byte[] blobId = new byte[blobIdLength];
            buff.get(blobId);
            long lastAccessTime = buff.getLong();
            return new LruEntry(buildCacheKey, blobId, lastAccessTime);
        }

        @Override
        public LruEntry[] createStorage(int size) {
            return new LruEntry[size];
        }
    }
}
