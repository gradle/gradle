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

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.file.FileEvents;
import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

public class LinuxFileWatcherRegistryFactory extends AbstractFileWatcherRegistryFactory<LinuxFileEventFunctions> {

    public LinuxFileWatcherRegistryFactory(Predicate<String> watchFilter) throws NativeIntegrationUnavailableException {
        super(FileEvents.get(LinuxFileEventFunctions.class), watchFilter);
    }

    @Override
    protected FileWatcher createFileWatcher(BlockingQueue<FileWatchEvent> fileEvents) throws InterruptedException {
        return fileEventFunctions.newWatcher(fileEvents)
            .start();
    }

    @Override
    protected FileWatcherUpdater createFileWatcherUpdater(FileWatcher watcher, Predicate<String> watchFilter) {
        return new NonHierarchicalFileWatcherUpdater(watcher, watchFilter);
    }
}
