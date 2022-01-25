/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.internal.snapshot.FileSystemSnapshot;

public class BuildCacheLoadMetadata {
    private final BuildCacheEntryPacker.UnpackResult unpackResult;
    private final ImmutableSortedMap<String, FileSystemSnapshot> resultingSnapshots;

    private BuildCacheLoadMetadata(BuildCacheEntryPacker.UnpackResult unpackResult, ImmutableSortedMap<String, FileSystemSnapshot> resultingSnapshots) {
        this.unpackResult = unpackResult;
        this.resultingSnapshots = resultingSnapshots;
    }

    public long getArtifactEntryCount() {
        return unpackResult.getEntries();
    }

    public OriginMetadata getOriginMetadata() {
        return unpackResult.getOriginMetadata();
    }

    public ImmutableSortedMap<String, FileSystemSnapshot> getResultingSnapshots() {
        return resultingSnapshots;
    }

    public static BuildCacheLoadMetadata of(BuildCacheEntryPacker.UnpackResult unpackResult, ImmutableSortedMap<String, FileSystemSnapshot> resultingSnapshots) {
        return new BuildCacheLoadMetadata(unpackResult, resultingSnapshots);
    }
}
