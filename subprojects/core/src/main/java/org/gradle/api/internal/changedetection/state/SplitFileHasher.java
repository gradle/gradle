/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.file.FileTreeElement;
import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.WellKnownFileLocations;

import java.io.File;

/**
 * A {@link FileHasher} that delegates to the global hasher for immutable files
 * and uses the local hasher for all other files. This ensures optimal cache utilization.
 */
public class SplitFileHasher implements FileHasher {
    private final FileHasher globalHasher;
    private final FileHasher localHasher;
    private final WellKnownFileLocations wellKnownFileLocations;

    public SplitFileHasher(FileHasher globalHasher, FileHasher localHasher, WellKnownFileLocations wellKnownFileLocations) {
        this.globalHasher = globalHasher;
        this.localHasher = localHasher;
        this.wellKnownFileLocations = wellKnownFileLocations;
    }

    @Override
    public HashCode hash(File file) {
        if (wellKnownFileLocations.isImmutable(file.getPath())) {
            return globalHasher.hash(file);
        } else {
            return localHasher.hash(file);
        }
    }

    @Override
    public HashCode hash(FileTreeElement fileDetails) {
        if (wellKnownFileLocations.isImmutable(fileDetails.getFile().getPath())) {
            return globalHasher.hash(fileDetails);
        } else {
            return localHasher.hash(fileDetails);
        }
    }

    @Override
    public HashCode hash(File file, FileMetadataSnapshot fileDetails) {
        if (wellKnownFileLocations.isImmutable(file.getPath())) {
            return globalHasher.hash(file, fileDetails);
        } else {
            return localHasher.hash(file, fileDetails);
        }
    }
}
