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

package org.gradle.caching.internal.tasks;

import com.google.common.collect.ImmutableListMultimap;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader;
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.SortedSet;

public interface TaskOutputPacker {
    // Initial format version
    // NOTE: This should be changed whenever we change the way we pack a cache entry, such as
    // - changing from gzip to bzip2.
    // - adding/removing properties to the origin metadata
    // - using a different format for the origin metadata
    // - any major changes of the layout of a cache entry
    int CACHE_ENTRY_FORMAT = 1;

    PackResult pack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, Map<String, Map<String, FileContentSnapshot>> outputSnapshots, OutputStream output, TaskOutputOriginWriter writeOrigin) throws IOException;

    class PackResult {
        private final long entries;

        public PackResult(long entries) {
            this.entries = entries;
        }

        public long getEntries() {
            return entries;
        }
    }

    UnpackResult unpack(SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs, InputStream input, TaskOutputOriginReader readOrigin) throws IOException;

    class UnpackResult {
        private final TaskOutputOriginMetadata originMetadata;
        private final long entries;
        private final ImmutableListMultimap<String, FileSnapshot> snapshots;

        public UnpackResult(TaskOutputOriginMetadata originMetadata, long entries, ImmutableListMultimap<String, FileSnapshot> snapshots) {
            this.originMetadata = originMetadata;
            this.entries = entries;
            this.snapshots = snapshots;
        }

        public TaskOutputOriginMetadata getOriginMetadata() {
            return originMetadata;
        }

        public long getEntries() {
            return entries;
        }

        public ImmutableListMultimap<String, FileSnapshot> getSnapshots() {
            return snapshots;
        }
    }
}
