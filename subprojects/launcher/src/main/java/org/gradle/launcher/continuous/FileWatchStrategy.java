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

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.filewatch.FilteringFileWatcherListener;
import org.gradle.internal.filewatch.StopThenFireFileWatcherListener;

import java.io.File;

/**
 * Hacky initial implementation for file watching
 * Monitors the "current" directory and excludes build/** and .gradle/**
 * TODO: Look for the project directory?
 */
class FileWatchStrategy implements TriggerStrategy {

    FileWatchStrategy(final TriggerListener listener, FileWatcherFactory fileWatcherFactory) {
        File workingDir = new File(".");
        DirectoryFileTree directoryFileTree = new DirectoryFileTree(workingDir);
        directoryFileTree.getPatterns().exclude("build/**/*", ".gradle/**/*");
        final FileCollectionInternal fileCollection = new FileTreeAdapter(directoryFileTree);

        fileWatcherFactory.watch(
            fileCollection.getFileSystemRoots(),
            Actions.doNothing(), // this should be replaced with something that tears down the build
            new FilteringFileWatcherListener(
                new Spec<FileWatcherEvent>() {
                    @Override
                    public boolean isSatisfiedBy(FileWatcherEvent element) {
                        return element.getType().equals(FileWatcherEvent.Type.OVERFLOW)
                            || element.getType().equals(FileWatcherEvent.Type.DELETE) // because fileCollection.contains() may not consider files that would be contained if they did exist
                            || fileCollection.contains(element.getFile());
                    }
                },
                new StopThenFireFileWatcherListener(new Runnable() {
                    @Override
                    public void run() {
                        listener.triggered(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "file change"));
                    }
                })
            )
        );
    }

    @Override
    public void run() {
        // TODO: Enforce quiet period here?
    }

}
