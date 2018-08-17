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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;

public class Jdk7FileWatcherFactory implements FileWatcherFactory {

    private final ListeningExecutorService executor;
    private final FileSystem fileSystem;

    public Jdk7FileWatcherFactory(ExecutorService executor, FileSystem fileSystem) {
        this.executor = MoreExecutors.listeningDecorator(executor);
        this.fileSystem = fileSystem;
    }

    @Override
    public FileWatcher watch(Action<? super Throwable> onError, FileWatcherListener listener) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            WatchServiceFileWatcherBacking backing = new WatchServiceFileWatcherBacking(onError, listener, watchService);
            return backing.start(executor);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
