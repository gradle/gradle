/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources.results;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.resources.paths.NormalizedPath;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileHashSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshottableFileSystemResource;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public abstract class AbstractNormalizedFileSnapshotSnapshottingResult extends AbstractSnapshottingResult implements NormalizedFileSnapshot {
    protected static FileContentSnapshot getFileContentSnapshot(FileContentSnapshot snapshot, HashCode hash) {
        if (snapshot.getType() != FileType.RegularFile || snapshot.getContentMd5().equals(hash)) {
            return snapshot;
        }
        return new FileHashSnapshot(hash);
    }

    private final String path;

    public AbstractNormalizedFileSnapshotSnapshottingResult(SnapshottableFileSystemResource resource, NormalizedPath normalizedPath) {
        super(normalizedPath);
        this.path = resource.getPath();
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString(getNormalizedPath().getPath());
        hasher.putBytes(getSnapshot().getContentMd5().asBytes());
    }


    @Override
    public HashCode getHash(NormalizedFileSnapshotCollector collector) {
        if (collector != null) {
            collector.collectSnapshot(path, this);
        }
        return getHashInternal(collector);
    }

    protected abstract HashCode getHashInternal(NormalizedFileSnapshotCollector collector);
}
