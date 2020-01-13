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

package org.gradle.caching.internal.packaging;

import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.origin.OriginReader;
import org.gradle.caching.internal.origin.OriginWriter;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public interface BuildCacheEntryPacker {
    PackResult pack(CacheableEntity entity, Map<String, ? extends FileSystemSnapshot> snapshots, OutputStream output, OriginWriter writeOrigin) throws IOException;

    class PackResult {
        private final long entries;

        public PackResult(long entries) {
            this.entries = entries;
        }

        public long getEntries() {
            return entries;
        }
    }

    UnpackResult unpack(CacheableEntity entity, InputStream input, OriginReader readOrigin) throws IOException;

    class UnpackResult {
        private final OriginMetadata originMetadata;
        private final long entries;
        private final Map<String, ? extends CompleteFileSystemLocationSnapshot> snapshots;

        public UnpackResult(OriginMetadata originMetadata, long entries, Map<String, ? extends CompleteFileSystemLocationSnapshot> snapshots) {
            this.originMetadata = originMetadata;
            this.entries = entries;
            this.snapshots = snapshots;
        }

        public OriginMetadata getOriginMetadata() {
            return originMetadata;
        }

        public long getEntries() {
            return entries;
        }

        public Map<String, ? extends CompleteFileSystemLocationSnapshot> getSnapshots() {
            return snapshots;
        }
    }
}
