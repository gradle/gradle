/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs;

import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.CREATED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.INVALIDATE;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.MODIFIED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.REMOVED;

public class AbstractEventDrivenFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventDrivenFileWatcherRegistry.class);

    private final FileWatcher watcher;
    private final AtomicReference<MutableFileWatchingStatistics> fileWatchingStatistics = new AtomicReference<>(new MutableFileWatchingStatistics());

    public AbstractEventDrivenFileWatcherRegistry(Set<Path> roots, FileWatcherCreator watcherCreator, ChangeHandler handler) {
        this.watcher = createWatcher(roots, watcherCreator, handler);
    }

    private FileWatcher createWatcher(Set<Path> roots, FileWatcherCreator watcherCreator, ChangeHandler handler) {
        FileWatcher watcher = watcherCreator.createWatcher(new FileWatcherCallback() {
            @Override
            public void pathChanged(Type type, String path) {
                handleEvent(type, path, handler);
            }

            @Override
            public void reportError(Throwable ex) {
                LOGGER.error("Error while receiving file changes", ex);
                fileWatchingStatistics.updateAndGet(statistics -> statistics.errorWhileReceivingFileChanges(ex));
                handler.handleLostState();
            }
        });
        roots.stream()
            .map(Path::toFile)
            .forEach(watcher::startWatching);
        return watcher;
    }

    private void handleEvent(FileWatcherCallback.Type type, String path, ChangeHandler handler) {
        if (type == FileWatcherCallback.Type.UNKNOWN) {
            fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::unknownEventEncountered);
            handler.handleLostState();
        } else {
            fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::eventReceived);
            handler.handleChange(convertType(type), Paths.get(path));
        }
    }

    private static Type convertType(FileWatcherCallback.Type type) {
        switch (type) {
            case CREATED:
                return CREATED;
            case MODIFIED:
                return MODIFIED;
            case REMOVED:
                return REMOVED;
            case INVALIDATE:
                return INVALIDATE;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public FileWatchingStatistics getAndResetStatistics() {
        return fileWatchingStatistics.getAndSet(new MutableFileWatchingStatistics());
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    protected interface FileWatcherCreator {
        FileWatcher createWatcher(FileWatcherCallback callback);
    }

    private static class MutableFileWatchingStatistics implements FileWatchingStatistics {
        private boolean unknownEventEncountered;
        private int numberOfReceivedEvents;
        private Throwable errorWhileReceivingFileChanges;

        @Override
        public Optional<Throwable> getErrorWhileReceivingFileChanges() {
            return Optional.ofNullable(errorWhileReceivingFileChanges);
        }

        @Override
        public boolean isUnknownEventEncountered() {
            return unknownEventEncountered;
        }

        @Override
        public int getNumberOfReceivedEvents() {
            return numberOfReceivedEvents;
        }

        public MutableFileWatchingStatistics eventReceived() {
            numberOfReceivedEvents++;
            return this;
        }

        public MutableFileWatchingStatistics errorWhileReceivingFileChanges(Throwable error) {
            if (errorWhileReceivingFileChanges != null) {
                errorWhileReceivingFileChanges = error;
            }
            return this;
        }

        public MutableFileWatchingStatistics unknownEventEncountered() {
            unknownEventEncountered = true;
            return this;
        }
    }
}
