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
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.filewatch.FileWatchInputs;
import org.gradle.internal.filewatch.FileWatcherService;

import java.io.File;

public class FileWatchTrigger implements TriggerBehavior {
    private final TriggerListener listener;
    private final Stoppable fileWatcher;

    public FileWatchTrigger(TriggerListener listener, FileWatcherService fileWatcherService) {
        this.listener = listener;
        DirectoryTree dir = new DirectoryFileTree(new File("."));
        dir.getPatterns().exclude("build/**/*", ".gradle/**/*");
        FileWatchInputs inputs = FileWatchInputs.newBuilder().add(dir).build();
        this.fileWatcher = fileWatcherService.watch(inputs, new FileChangeCallback());
    }

    @Override
    public void run() {

    }

    private class FileChangeCallback implements Runnable {
        public void run() {
            listener.triggered(new DefaultTriggerDetails("file change"));
        }
    }
}
