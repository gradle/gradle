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

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.*;

import java.io.File;
import java.io.IOException;

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
        DirectoryTree dir = new DirectoryFileTree(new File("."));
        dir.getPatterns().exclude("build/**/*", ".gradle/**/*");
        FileWatchInputs inputs = FileWatchInputs.newBuilder().add(dir).build();
        try {
            this.fileWatcher = fileWatcherFactory.createFileWatcher(new FileChangeCallback(listener));
            this.fileWatcher.watch(null, inputs);
        } catch (IOException e) {
            // TODO:
            UncheckedException.throwAsUncheckedException(e);
        }
        // TODO: We need to stop the fileWatcher?
    }

    @Override
    public void run() {
        // TODO: Enforce quiet period here?
    }

    static class FileChangeCallback implements FileWatcherListener {
        private final TriggerListener listener;

        private FileChangeCallback(TriggerListener listener) {
            this.listener = listener;
        }

        @Override
        public void onOverflow() {
            listener.triggered(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "overflow in filewatching"));
        }

        @Override
        public void onChange(FileChangeDetails fileChangeDetails) {
            listener.triggered(new DefaultTriggerDetails(TriggerDetails.Type.REBUILD, "file change"));
        }
    }
}
