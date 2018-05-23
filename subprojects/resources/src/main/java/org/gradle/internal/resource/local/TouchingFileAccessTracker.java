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

package org.gradle.internal.resource.local;

import com.google.common.base.Preconditions;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("Since15")
public class TouchingFileAccessTracker implements FileAccessTracker {

    private final Path baseDir;
    private final int endNameIndex;
    private final int startNameIndex;

    public TouchingFileAccessTracker(File baseDir, int depth) {
        Preconditions.checkArgument(depth > 0, "depth must be > 0: %s", depth);
        this.baseDir = baseDir.toPath().toAbsolutePath();
        this.startNameIndex = this.baseDir.getNameCount();
        this.endNameIndex = startNameIndex + depth;
    }

    public void markAccessed(Collection<File> files) {
        FileTime time = FileTime.fromMillis(System.currentTimeMillis());
        for (Path path : collectSubPaths(files)) {
            touch(path, time);
        }
    }

    private Set<Path> collectSubPaths(Collection<File> files) {
        Set<Path> paths = new HashSet<Path>();
        for (File file : files) {
            Path path = file.toPath().toAbsolutePath();
            if (path.getNameCount() >= endNameIndex && path.startsWith(baseDir)) {
                paths.add(baseDir.resolve(path.subpath(startNameIndex, endNameIndex)));
            }
        }
        return paths;
    }

    private void touch(Path path, FileTime time) {
        try {
            Files.setLastModifiedTime(path, time);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not set last modification time for file: " + path, e);
        }
    }
}
