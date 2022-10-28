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
import net.rubygrapefruit.platform.internal.jni.AbstractNativeFileEventFunctions;
import net.rubygrapefruit.platform.internal.jni.NativeLogger;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;
import org.gradle.internal.watch.registry.WatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import static org.gradle.internal.watch.registry.FileWatcherRegistry.Type.CREATED;
import static org.gradle.internal.watch.registry.FileWatcherRegistry.Type.INVALIDATED;
import static org.gradle.internal.watch.registry.FileWatcherRegistry.Type.MODIFIED;
import static org.gradle.internal.watch.registry.FileWatcherRegistry.Type.OVERFLOW;
import static org.gradle.internal.watch.registry.FileWatcherRegistry.Type.REMOVED;

public class DefaultFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileWatcherRegistry.class);

    private final AbstractNativeFileEventFunctions<?> fileEventFunctions;
    private final FileWatcher watcher;
    private final BlockingQueue<FileWatchEvent> fileEvents;
    private final Thread eventConsumerThread;
    private final FileWatcherUpdater fileWatcherUpdater;

    private volatile MutableFileWatchingStatistics fileWatchingStatistics = new MutableFileWatchingStatistics();
    private volatile boolean consumeEvents = true;
    private volatile boolean stopping = false;

    public DefaultFileWatcherRegistry(
        AbstractNativeFileEventFunctions<?> fileEventFunctions,
        FileWatcher watcher,
        ChangeHandler handler,
        FileWatcherUpdater fileWatcherUpdater,
        BlockingQueue<FileWatchEvent> fileEvents
    ) {
        this.fileEventFunctions = fileEventFunctions;
        this.watcher = watcher;
        this.fileEvents = fileEvents;
        this.fileWatcherUpdater = fileWatcherUpdater;
        this.eventConsumerThread = createAndStartEventConsumerThread(handler);
    }

    private Thread createAndStartEventConsumerThread(ChangeHandler handler) {
        Thread thread = new Thread(() -> {
            LOGGER.debug("Started listening to file system change events");
            try {
                while (consumeEvents) {
                    FileWatchEvent nextEvent = fileEvents.take();
                    if (!stopping) {
                        nextEvent.handleEvent(new FileWatchEvent.Handler() {
                            @Override
                            public void handleChangeEvent(FileWatchEvent.ChangeType type, String absolutePath) {
                                fileWatchingStatistics.eventReceived();
                                fileWatcherUpdater.triggerWatchProbe(absolutePath);
                                handler.handleChange(convertType(type), Paths.get(absolutePath));
                            }

                            @Override
                            public void handleUnknownEvent(String absolutePath) {
                                LOGGER.error("Received unknown event for {}", absolutePath);
                                fileWatchingStatistics.unknownEventEncountered();
                                handler.stopWatchingAfterError();
                            }

                            @Override
                            public void handleOverflow(FileWatchEvent.OverflowType type, @Nullable String absolutePath) {
                                if (absolutePath == null) {
                                    LOGGER.info("Overflow detected (type: {}), invalidating all watched files", type);
                                    fileWatcherUpdater.getWatchedFiles().visitRoots(watchedRoot ->
                                        handler.handleChange(OVERFLOW, Paths.get(watchedRoot)));
                                } else {
                                    LOGGER.info("Overflow detected (type: {}) for watched path '{}', invalidating", type, absolutePath);
                                    handler.handleChange(OVERFLOW, Paths.get(absolutePath));
                                }
                            }

                            @Override
                            public void handleFailure(Throwable failure) {
                                LOGGER.error("Error while receiving file changes", failure);
                                fileWatchingStatistics.errorWhileReceivingFileChanges(failure);
                                handler.stopWatchingAfterError();
                            }

                            @Override
                            public void handleTerminated() {
                                consumeEvents = false;
                            }
                        });
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // stop thread
            }
            LOGGER.debug("Finished listening to file system change events");
        });
        thread.setDaemon(true);
        thread.setName("File watcher consumer");
        thread.setUncaughtExceptionHandler((failedThread, exception) -> {
            LOGGER.error("File system event consumer thread stopped due to exception", exception);
            handler.stopWatchingAfterError();
        });
        thread.start();
        return thread;
    }

    @Override
    public boolean isWatchingAnyLocations() {
        return !fileWatcherUpdater.getWatchedFiles().isEmpty();
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy, SnapshotHierarchy root) {
        fileWatcherUpdater.registerWatchableHierarchy(watchableHierarchy, root);
    }

    @Override
    public void virtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, SnapshotHierarchy root) {
        fileWatcherUpdater.virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, root);
    }

    @Override
    public SnapshotHierarchy updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode, List<File> unsupportedFileSystems) {
        return fileWatcherUpdater.updateVfsOnBuildStarted(root, watchMode, unsupportedFileSystems);
    }

    @Override
    public SnapshotHierarchy updateVfsOnBuildFinished(SnapshotHierarchy root, WatchMode watchMode, int maximumNumberOfWatchedHierarchies, List<File> unsupportedFileSystems) {
        return fileWatcherUpdater.updateVfsOnBuildFinished(root, watchMode, maximumNumberOfWatchedHierarchies, unsupportedFileSystems);
    }

    private static Type convertType(FileWatchEvent.ChangeType type) {
        switch (type) {
            case CREATED:
                return CREATED;
            case MODIFIED:
                return MODIFIED;
            case REMOVED:
                return REMOVED;
            case INVALIDATED:
                return INVALIDATED;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public FileWatchingStatistics getAndResetStatistics() {
        MutableFileWatchingStatistics currentStatistics = fileWatchingStatistics;
        fileWatchingStatistics = new MutableFileWatchingStatistics();
        AtomicInteger numberOfWatchedHierarchies = new AtomicInteger();
        fileWatcherUpdater.getWatchedFiles().visitRoots(root -> numberOfWatchedHierarchies.incrementAndGet());
        return new FileWatchingStatistics() {
            @Override
            public Optional<Throwable> getErrorWhileReceivingFileChanges() {
                return currentStatistics.getErrorWhileReceivingFileChanges();
            }

            @Override
            public boolean isUnknownEventEncountered() {
                return currentStatistics.isUnknownEventEncountered();
            }

            @Override
            public int getNumberOfReceivedEvents() {
                return currentStatistics.getNumberOfReceivedEvents();
            }

            @Override
            public int getNumberOfWatchedHierarchies() {
                return numberOfWatchedHierarchies.get();
            }
        };
    }

    @Override
    public void setDebugLoggingEnabled(boolean debugLoggingEnabled) {
        java.util.logging.Logger.getLogger(NativeLogger.class.getName()).setLevel(debugLoggingEnabled
            ? Level.FINEST
            : Level.INFO
        );
        fileEventFunctions.invalidateLogLevelCache();
    }

    @Override
    public void close() throws IOException {
        stopping = true;
        try {
            watcher.shutdown();
            if (!watcher.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Watcher did not terminate within 5 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Awaiting termination of watcher was interrupted");
        } finally {
            consumeEvents = false;
            eventConsumerThread.interrupt();
        }
    }

    private static class MutableFileWatchingStatistics {
        private boolean unknownEventEncountered;
        private int numberOfReceivedEvents;
        private Throwable errorWhileReceivingFileChanges;

        public Optional<Throwable> getErrorWhileReceivingFileChanges() {
            return Optional.ofNullable(errorWhileReceivingFileChanges);
        }

        public boolean isUnknownEventEncountered() {
            return unknownEventEncountered;
        }

        public int getNumberOfReceivedEvents() {
            return numberOfReceivedEvents;
        }

        public void eventReceived() {
            numberOfReceivedEvents++;
        }

        public void errorWhileReceivingFileChanges(Throwable error) {
            if (errorWhileReceivingFileChanges != null) {
                errorWhileReceivingFileChanges = error;
            }
        }

        public void unknownEventEncountered() {
            unknownEventEncountered = true;
        }
    }
}
