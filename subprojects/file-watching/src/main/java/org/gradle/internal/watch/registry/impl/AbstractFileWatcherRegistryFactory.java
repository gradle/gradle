/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.registry.impl;

import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.internal.jni.AbstractFileEventFunctions;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

public abstract class AbstractFileWatcherRegistryFactory<T extends AbstractFileEventFunctions<W>, W extends FileWatcher> implements FileWatcherRegistryFactory {
    private static final int FILE_EVENT_QUEUE_SIZE = 4096;

    protected final T fileEventFunctions;
    private final Predicate<String> watchFilter;

    public AbstractFileWatcherRegistryFactory(
        T fileEventFunctions,
        Predicate<String> watchFilter
    ) {
        this.fileEventFunctions = fileEventFunctions;
        this.watchFilter = watchFilter;
    }

    @Override
    public FileWatcherRegistry createFileWatcherRegistry(FileWatcherRegistry.ChangeHandler handler) {
        BlockingQueue<FileWatchEvent> fileEvents = new ArrayBlockingQueue<>(FILE_EVENT_QUEUE_SIZE);
        try {
            // TODO How can we avoid hard-coding ".gradle" here?
            FileWatcherProbeRegistry probeRegistry = new DefaultFileWatcherProbeRegistry(buildDir ->
                new File(new File(buildDir, ".gradle"), "file-system.probe"));
            W watcher = createFileWatcher(fileEvents);
            WatchableHierarchies watchableHierarchies = new WatchableHierarchies(probeRegistry, watchFilter);
            FileWatcherUpdater fileWatcherUpdater = createFileWatcherUpdater(watcher, probeRegistry, watchableHierarchies);
            return new DefaultFileWatcherRegistry(
                fileEventFunctions,
                watcher,
                handler,
                fileWatcherUpdater,
                fileEvents
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    protected abstract W createFileWatcher(BlockingQueue<FileWatchEvent> fileEvents) throws InterruptedException;

    protected abstract FileWatcherUpdater createFileWatcherUpdater(W watcher, FileWatcherProbeRegistry probeRegistry, WatchableHierarchies watchableHierarchies);
}
