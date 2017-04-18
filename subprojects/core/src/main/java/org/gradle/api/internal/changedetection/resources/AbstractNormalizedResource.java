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

package org.gradle.api.internal.changedetection.resources;

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshotCollector;
import org.gradle.api.internal.changedetection.state.SnapshottableFileSystemResource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public abstract class AbstractNormalizedResource implements NormalizedResource {
    protected final SnapshottableResource resource;
    protected final NormalizedPath normalizedPath;

    public AbstractNormalizedResource(SnapshottableResource resource, NormalizedPath normalizedPath) {
        this.resource = resource;
        this.normalizedPath = normalizedPath;
    }

    @Override
    public SnapshottableResource getResource() {
        return resource;
    }

    @Override
    public NormalizedPath getNormalizedPath() {
        return normalizedPath;
    }

    @Override
    public HashCode getHash(NormalizedFileSnapshotCollector collector) {
        HashCode hash = getHashInternal(collector);
        if (collector != null) {
            if (resource instanceof SnapshottableFileSystemResource) {
                SnapshottableFileSystemResource fileSystemResource = (SnapshottableFileSystemResource) resource;
                if (resource.getType() == FileType.RegularFile) {
                    fileSystemResource = fileSystemResource.withContentHash(hash);
                }
                collector.collectSnapshot(new DefaultNormalizedFileSnapshot(fileSystemResource.getPath(), getNormalizedPath(), fileSystemResource.getContent()));
            }
        }
        return hash;
    }

    protected abstract HashCode getHashInternal(NormalizedFileSnapshotCollector collector);

    @Override
    public int compareTo(NormalizedResource o) {
        int result = getNormalizedPath().compareTo(o.getNormalizedPath());
        if (result == 0) {
            result = HashUtil.compareHashCodes(getHash(null), o.getHash(null));
        }
        return result;
    }
}
