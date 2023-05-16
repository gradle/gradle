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

public class BlobId {

    private final long lruKey;
    private final byte[] blobId;
    private final long lastAccessTime;


    public BlobId(long lruKey, byte[] blobId, long lastAccessTime) {
        this.lruKey = lruKey;
        this.blobId = blobId;
        this.lastAccessTime = lastAccessTime;
    }

    public long getLruKey() {
        return lruKey;
    }

    public byte[] getBlobId() {
        return blobId;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    static class BlobIdType extends BasicDataType<BlobId> {
        static final BlobIdType INSTANCE = new BlobIdType();

        @Override
        public int getMemory(BlobId obj) {
            return Long.BYTES * 2 + obj.blobId.length;
        }

        @Override
        public void write(WriteBuffer buff, BlobId obj) {
            buff.putVarInt(obj.blobId.length);
            buff.put(obj.blobId);
            buff.putVarLong(obj.lruKey);
            buff.putLong(obj.lastAccessTime);
        }

        @Override
        public BlobId read(ByteBuffer buff) {
            int blobIdLength = DataUtils.readVarInt(buff);
            byte[] blobId = new byte[blobIdLength];
            buff.get(blobId);
            long lruKey = DataUtils.readVarLong(buff);
            long lastAccessTime = buff.getLong();
            return new BlobId(lruKey, blobId, lastAccessTime);
        }

        @Override
        public BlobId[] createStorage(int size) {
            return new BlobId[size];
        }
    }
}
