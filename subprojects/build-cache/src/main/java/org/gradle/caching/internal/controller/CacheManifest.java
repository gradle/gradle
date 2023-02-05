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

package org.gradle.caching.internal.controller;

import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.List;
import java.util.Map;

public class CacheManifest {
    private final OriginMetadata originMetadata;
    private final Map<String, List<ManifestEntry>> propertyManifests;

    public CacheManifest(OriginMetadata originMetadata, Map<String, List<ManifestEntry>> propertyManifests) {
        this.originMetadata = originMetadata;
        this.propertyManifests = propertyManifests;
    }

    public OriginMetadata getOriginMetadata() {
        return originMetadata;
    }

    public Map<String, List<ManifestEntry>> getPropertyManifests() {
        return propertyManifests;
    }

    public static class ManifestEntry {
        private final FileType type;
        private final String relativePath;
        private final HashCode contentHash;
        private final long length;

        public ManifestEntry(FileType type, String relativePath, HashCode contentHash, long length) {
            this.type = type;
            this.relativePath = relativePath;
            this.contentHash = contentHash;
            this.length = length;
        }

        public FileType getType() {
            return type;
        }

        public String getRelativePath() {
            return relativePath;
        }

        public HashCode getContentHash() {
            return contentHash;
        }

        public long getLength() {
            return length;
        }
    }
}
