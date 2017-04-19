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
import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshottableFileSystemResource;

public class CompositeFileSnapshotSnapshottingResult extends AbstractNormalizedFileSnapshotSnapshottingResult {
    private final SnapshottingResultRecorder recorder;
    private FileContentSnapshot snapshot;

    public CompositeFileSnapshotSnapshottingResult(SnapshottableFileSystemResource resource, NormalizedPath normalizedPath, SnapshottingResultRecorder recorder) {
        super(resource, normalizedPath);
        this.snapshot = resource.getContent();
        this.recorder = recorder;
    }

    @Override
    protected HashCode getHashInternal(NormalizedFileSnapshotCollector collector) {
        HashCode hash = recorder.getHash(collector);
        this.snapshot = getFileContentSnapshot(snapshot, hash);
        return hash;
    }

    @Override
    public FileContentSnapshot getSnapshot() {
        return snapshot;
    }
}
