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

package org.gradle.internal.filewatch;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.filewatch.jdk7.Jdk7FileWatcherFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DefaultFileWatcherFactory implements FileWatcherFactory, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileWatcherFactory.class);

    private static final int STOP_TIMEOUT_SECONDS = 10;
    private final ManagedExecutor executor;
    private final FileSystem fileSystem;

    private FileWatcherFactory fileWatcherFactory;

    public DefaultFileWatcherFactory(ExecutorFactory executorFactory, FileSystem fileSystem) {
        this.executor = executorFactory.create("File watcher");
        this.fileSystem = fileSystem;
    }

    protected FileWatcherFactory createFileWatcherFactory() {
        return new Jdk7FileWatcherFactory(executor, fileSystem);
    }

    @Override
    public void stop() {
        try {
            executor.stop(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // could be caused by https://bugs.openjdk.java.net/browse/JDK-8011537 ignore problems in stopping
            LOGGER.debug("Problem shutting down executor. The problem might be caused by JDK-8011537.", e);
        }
    }

    @Override
    public FileWatcher watch(Action<? super Throwable> onError, FileWatcherListener listener) {
        if (fileWatcherFactory == null) {
            fileWatcherFactory = createFileWatcherFactory();
        }
        return fileWatcherFactory.watch(onError, listener);
    }
}
