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
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions.WindowsFileWatcher;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;

import static org.gradle.internal.watch.registry.impl.HierarchicalFileWatcherUpdater.FileSystemLocationToWatchValidator.NO_VALIDATION;

public class WindowsFileWatcherRegistryFactory extends AbstractFileWatcherRegistryFactory<WindowsFileEventFunctions, WindowsFileWatcher> {

    // 64 kB is the limit for SMB drives
    // See https://docs.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-readdirectorychangesw#remarks:~:text=ERROR_INVALID_PARAMETER
    private static final int BUFFER_SIZE = 64 * 1024;

    public WindowsFileWatcherRegistryFactory(
        Predicate<String> watchFilter
    ) throws NativeIntegrationUnavailableException {
        super(FileEvents.get(WindowsFileEventFunctions.class), watchFilter);
    }

    @Override
    protected WindowsFileWatcher createFileWatcher(BlockingQueue<FileWatchEvent> fileEvents) throws InterruptedException {
        return fileEventFunctions.newWatcher(fileEvents)
            .withBufferSize(BUFFER_SIZE)
            .start();
    }

    @Override
    protected FileWatcherUpdater createFileWatcherUpdater(
        WindowsFileWatcher watcher,
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies
    ) {
        return new HierarchicalFileWatcherUpdater(watcher, NO_VALIDATION, probeRegistry, watchableHierarchies, root -> watcher.stopWatchingMovedPaths());
    }
}
