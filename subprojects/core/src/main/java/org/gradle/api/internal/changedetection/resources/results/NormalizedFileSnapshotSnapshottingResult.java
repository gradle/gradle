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
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshottableFileSystemResource;

public class NormalizedFileSnapshotSnapshottingResult extends AbstractNormalizedFileSnapshotSnapshottingResult {
    private final FileContentSnapshot snapshot;
    private final HashCode hash;

    public NormalizedFileSnapshotSnapshottingResult(SnapshottableFileSystemResource resource, NormalizedPath normalizedPath, HashCode hash) {
        super(resource, normalizedPath);
        this.hash = hash;
        this.snapshot = getFileContentSnapshot(resource.getContent(), hash);
    }

    @Override
    public FileContentSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    protected HashCode getHashInternal(NormalizedFileSnapshotCollector collector) {
        return hash;
    }
}
