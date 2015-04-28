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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.filewatch.FileWatcherListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

class IndividualFileWatchRegistry extends WatchRegistry<File> {
    private final Map<Path, Set<File>> individualFilesByParentPath;

    IndividualFileWatchRegistry(WatchStrategy watchStrategy) {
        super(watchStrategy);
        individualFilesByParentPath = new HashMap<Path, Set<File>>();
    }

    @Override
    public synchronized void register(Iterable<File> files) throws IOException {
        Set<Path> addedParents = new HashSet<Path>();
        for (File originalFile : files) {
            File file = originalFile.getAbsoluteFile();
            Path parent = dirToPath(file.getParentFile());
            Set<File> children = individualFilesByParentPath.get(parent);
            if (children == null) {
                children = new LinkedHashSet<File>();
                individualFilesByParentPath.put(parent, children);
                addedParents.add(parent);
            }
            children.add(file);
        }
        for (Path parent : addedParents) {
            if(!isWatching(parent)) {
                watchDirectory(parent);
            }
        }
    }

    @Override
    public synchronized void handleChange(ChangeDetails changeDetails, FileWatcherListener listener) {
        Set<File> files = individualFilesByParentPath.get(changeDetails.getWatchedPath());
        if(files != null) {
            File file = changeDetails.getFullItemPath().toFile().getAbsoluteFile();
            if(files.contains(file)) {
                sendOnChangeEvent(changeDetails, listener);
            }
        }
    }
}
