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

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.StreamStore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

class MVStoreStreamStore {

    private final MVMap<byte[], GBlobMetadata> blobMetadata;
    private final StreamStore data;

    public MVStoreStreamStore(MVStore mvStore) {
        MVMap<Long, byte[]> map = mvStore.openMap("data");
        this.data = new StreamStore(map);
        this.data.setNextKey(map.lastKey() == null ? 0 : map.lastKey() + 1);
        this.blobMetadata = mvStore.openMap("dataIndexToBuildCacheKey");;
    }

    public GBlobMetadata writeBlob(byte[] blobHashCode, long timestamp, InputStream in) throws IOException {
        byte[] blobId = data.put(in);
        GBlobMetadata blobMetadata = new GBlobMetadata(blobId, blobHashCode, timestamp);
        blobMetadata.put(blobId, blobMetadata);
        return blobMetadata;
    }

    public InputStream openBlob(byte[] blobId) {
        return data.get(blobId);
    }

    public void removeIfMatches(byte[] blobHashCode, byte[] blobId) {
        GBlobMetadata metadata = blobMetadata.get(blobId);
        if (metadata == null || Arrays.equals(metadata.getHash(), blobHashCode)) {
            blobMetadata.remove(blobId);
            data.remove(blobId);
        }
    }

    public GBlobMetadata getBlobMetadata(byte[] blobId) {
        blobMetadata.get(blobId);
    }

    public static class GBlobMetadata {
        private final byte[] blobId;
        private final byte[] hash;
        private final long timestamp;

        public GBlobMetadata(byte[] blobId, byte[] hash, long timestamp) {
            this.blobId = blobId;
            this.hash = hash;
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public byte[] getHash() {
            return hash;
        }

        public byte[] getBlobId() {
            return blobId;
        }
    }
}
