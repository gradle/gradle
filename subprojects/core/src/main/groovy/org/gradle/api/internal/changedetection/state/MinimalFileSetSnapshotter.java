/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.ListBackedFileSet;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.File;
import java.util.List;

public class MinimalFileSetSnapshotter extends DefaultFileCollectionSnapshotter {
    private final FileSystem fileSystem;

    public MinimalFileSetSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver, FileSystem fileSystem) {
        super(snapshotter, cacheAccess, stringInterner, fileResolver);
        this.fileSystem = fileSystem;
    }

    @Override
    protected void visitFiles(FileCollection input, final List<FileVisitDetails> allFileVisitDetails, final List<File> missingFiles) {
        visitFiles(new ListBackedFileSet(input.getFiles()), allFileVisitDetails, missingFiles);
    }

    private void visitFiles(MinimalFileSet input, final List<FileVisitDetails> allFileVisitDetails, final List<File> missingFiles) {
        for (File file : input.getFiles()) {
            if (file.exists()) {
                allFileVisitDetails.add(new DefaultFileVisitDetails(file, fileSystem, fileSystem));
            } else {
                missingFiles.add(file);
            }
        }
    }
}
