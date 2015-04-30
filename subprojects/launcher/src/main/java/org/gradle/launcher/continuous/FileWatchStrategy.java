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

package org.gradle.launcher.continuous;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.internal.filewatch.*;

import java.io.File;

/**
 * Hacky initial implementation for file watching
 * Monitors the "current" directory and excludes build/** and .gradle/**
 * TODO: Look for the project directory?
 */
class FileWatchStrategy implements TriggerStrategy {
    private final TriggerListener listener;
    private FileWatcher fileWatcher;

    FileWatchStrategy(TriggerListener listener, FileWatcherFactory fileWatcherFactory) {
        this.listener = listener;
        DirectoryFileTree directoryFileTree = new DirectoryFileTree(new File("."));
        directoryFileTree.getPatterns().exclude("build/**/*", ".gradle/**/*");
        FileCollectionInternal fileCollection = new FileTreeAdapter(directoryFileTree);

        this.fileWatcher = fileWatcherFactory.watch(fileCollection.getFileSystemRoots(), new FileChangeCallback(listener, fileCollection));
    }

    @Override
    public void run() {
        // TODO: Enforce quiet period here?
    }

    static class FileChangeCallback implements FileWatcherListener {
        private final TriggerListener listener;
        private final FileCollection fileCollection;

        private FileChangeCallback(TriggerListener listener, FileCollection fileCollection) {
            this.listener = listener;
            this.fileCollection = fileCollection;
        }

        @Override
        public FileWatcherEventResult onChange(FileWatcherEvent event) {
            if(event.getFile() == null || fileCollection.contains(event.getFile())) {
                listener.triggered(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "file change"));
                return FileWatcherEventResults.getContinueResult();
                // TODO: stop watcher and restart for each new build
                //return FileWatcherEventResults.getTerminateResult();
            } else {
                return FileWatcherEventResults.getContinueResult();
            }
        }
    }
}
