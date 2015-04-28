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

import org.gradle.internal.filewatch.FileWatchInputs;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherListener;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class DefaultFileWatcher implements FileWatcher {
    private final FileWatcherTask fileWatcherTask;
    private final DirTreeWatchRegistry dirTreeWatchRegistry;
    private final IndividualFileWatchRegistry individualFileWatchRegistry;

    public DefaultFileWatcher(ExecutorService executor, WatchStrategy watchStrategy, FileWatcherListener listener) throws IOException {
        this.dirTreeWatchRegistry = DirTreeWatchRegistry.create(watchStrategy);
        this.individualFileWatchRegistry = new IndividualFileWatchRegistry(watchStrategy);
        this.fileWatcherTask = new FileWatcherTask(watchStrategy, createWatchListener(listener));
        executor.submit(fileWatcherTask);
    }

    private WatchListener createWatchListener(final FileWatcherListener listener) {
        return new WatchListener() {
            @Override
            public void onOverflow() {
                listener.onOverflow();
            }

            @Override
            public void onChange(ChangeDetails changeDetails) {
                dirTreeWatchRegistry.handleChange(changeDetails, listener);
                individualFileWatchRegistry.handleChange(changeDetails, listener);
            }
        };
    }

    @Override
    public void watch(FileWatchInputs inputs) throws IOException {
        dirTreeWatchRegistry.register(inputs.getDirectoryTrees());
        individualFileWatchRegistry.register(inputs.getFiles());
    }

    @Override
    public void stop() {
        fileWatcherTask.stop();
    }

}
