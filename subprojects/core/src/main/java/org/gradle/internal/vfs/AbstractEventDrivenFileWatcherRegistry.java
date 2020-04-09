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

import net.rubygrapefruit.platform.file.FileWatchEvent;
import net.rubygrapefruit.platform.file.FileWatcher;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.CREATED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.INVALIDATED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.MODIFIED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.REMOVED;

public abstract class AbstractEventDrivenFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventDrivenFileWatcherRegistry.class);

    private final FileWatcher watcher;
    private final BlockingQueue<FileWatchEvent> fileEvents = new ArrayBlockingQueue<>(4096);
    private final Thread eventConsumerThread;
    private final AtomicReference<MutableFileWatchingStatistics> fileWatchingStatistics = new AtomicReference<>(new MutableFileWatchingStatistics());
    private final FileWatcherUpdater fileWatcherUpdater;

    private volatile boolean consumeEvents = true;
    private volatile boolean stopping = false;

    public AbstractEventDrivenFileWatcherRegistry(FileWatcherCreator watcherCreator, ChangeHandler handler, Function<FileWatcher, FileWatcherUpdater> watcherUpdaterCreator) {
        this.watcher = createWatcher(watcherCreator);
        this.fileWatcherUpdater = watcherUpdaterCreator.apply(watcher);
        this.eventConsumerThread = createAndStartEventConsumerThread(handler);
    }

    private Thread createAndStartEventConsumerThread(ChangeHandler handler) {
        Thread thread = new Thread(() -> {
            try {
                while (consumeEvents) {
                    FileWatchEvent nextEvent = fileEvents.take();
                    if (!stopping) {
                        nextEvent.handleEvent(new FileWatchEvent.Handler() {
                            @Override
                            public void handleChangeEvent(FileWatchEvent.ChangeType type, String absolutePath) {
                                fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::eventReceived);
                                handler.handleChange(convertType(type), Paths.get(absolutePath));
                            }

                            @Override
                            public void handleUnknownEvent(String absolutePath) {
                                fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::unknownEventEncountered);
                                handler.handleLostState();
                            }

                            @Override
                            public void handleOverflow(FileWatchEvent.OverflowType type, @Nullable String absolutePath) {
                                if (absolutePath == null) {
                                    handler.handleLostState();
                                } else {
                                    handler.handleChange(INVALIDATED, Paths.get(absolutePath));
                                }
                            }

                            @Override
                            public void handleFailure(Throwable failure) {
                                LOGGER.error("Error while receiving file changes", failure);
                                fileWatchingStatistics.updateAndGet(statistics -> statistics.errorWhileReceivingFileChanges(failure));
                                handler.handleLostState();
                            }

                            @Override
                            public void handleTerminated(boolean successful) {
                                consumeEvents = false;
                            }
                        });
                    }
                }
            } catch (InterruptedException e) {
                // stop thread
            }
        });
        thread.setDaemon(true);
        thread.setName("File watcher consumer");
        thread.start();
        return thread;
    }

    @Override
    public FileWatcherUpdater getFileWatcherUpdater() {
        return fileWatcherUpdater;
    }

    private FileWatcher createWatcher(FileWatcherCreator watcherCreator) {
        try {
            return watcherCreator.createWatcher(fileEvents);
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
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
        return fileWatchingStatistics.getAndSet(new MutableFileWatchingStatistics());
    }

    @Override
    public void close() throws IOException {
        stopping = true;
        try {
            watcher.close();
        } finally {
            consumeEvents = false;
            eventConsumerThread.interrupt();
        }
    }

    protected interface FileWatcherCreator {
        FileWatcher createWatcher(BlockingQueue<FileWatchEvent> eventQueue) throws InterruptedException;
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
