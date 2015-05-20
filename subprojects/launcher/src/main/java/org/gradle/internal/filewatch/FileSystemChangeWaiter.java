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
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.BiAction;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class FileSystemChangeWaiter implements BiAction<FileSystemSubset, Runnable> {
    private static final long FIRING_DELAY_TIMEMILLIS = 250L;
    private final FileWatcherFactory fileWatcherFactory;

    public FileSystemChangeWaiter(FileWatcherFactory fileWatcherFactory) {
        this.fileWatcherFactory = fileWatcherFactory;
    }

    @Override
    public void execute(FileSystemSubset taskFileSystemInputs, Runnable notifier) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        FileWatcher watcher = fileWatcherFactory.watch(
            taskFileSystemInputs,
            new Action<Throwable>() {
                @Override
                public void execute(Throwable throwable) {
                    error.set(throwable);
                    latch.countDown();
                }
            },
            new FileWatcherListener() {
                @Override
                public void onChange(FileWatcher watcher, FileWatcherEvent event) {
                    watcher.stop();
                    latch.countDown();
                }
            }
        );

        try {
            notifier.run();
        } catch (Exception e) {
            watcher.stop();
            throw UncheckedException.throwAsUncheckedException(e);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        Throwable throwable = error.get();
        if (throwable != null) {
            throw UncheckedException.throwAsUncheckedException(throwable);
        }

        try {
            Thread.sleep(FIRING_DELAY_TIMEMILLIS);
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
