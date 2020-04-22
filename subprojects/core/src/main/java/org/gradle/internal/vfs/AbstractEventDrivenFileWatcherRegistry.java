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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.CREATED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.INVALIDATE;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.MODIFIED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.REMOVED;

public abstract class AbstractEventDrivenFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractEventDrivenFileWatcherRegistry.class);

    private final FileWatcher watcher;
    private final BlockingQueue<FileEvent> fileEvents = new ArrayBlockingQueue<>(4096);
    private final Thread eventConsumerThread;
    private final AtomicReference<MutableFileWatchingStatistics> fileWatchingStatistics = new AtomicReference<>(new MutableFileWatchingStatistics());

    private volatile boolean consumeEvents = true;
    private volatile boolean stopping = false;

    public AbstractEventDrivenFileWatcherRegistry(FileWatcherCreator watcherCreator, ChangeHandler handler) {
        this.watcher = createWatcher(watcherCreator);
        this.eventConsumerThread = createAndStartEventConsumerThread(handler);
    }

    private Thread createAndStartEventConsumerThread(ChangeHandler handler) {
        Thread thread = new Thread(() -> {
            try {
                while (consumeEvents) {
                    FileEvent nextEvent = fileEvents.take();
                    if (!stopping) {
                        if (nextEvent.lostState) {
                            handler.handleLostState();
                        } else {
                            handler.handleChange(nextEvent.type, nextEvent.path);
                        }
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

    public FileWatcher getWatcher() {
        return watcher;
    }

    private FileWatcher createWatcher(FileWatcherCreator watcherCreator) {
        return watcherCreator.createWatcher(new FileWatcherCallback() {
            @Override
            public void pathChanged(Type type, String path) {
                FileEvent event;
                if (type == FileWatcherCallback.Type.UNKNOWN) {
                    fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::unknownEventEncountered);
                    event = FileEvent.lostState();
                } else {
                    fileWatchingStatistics.updateAndGet(MutableFileWatchingStatistics::eventReceived);
                    event = FileEvent.changed(convertType(type), Paths.get(path));
                }
                boolean addedToQueue = fileEvents.offer(event);
                if (!addedToQueue) {
                    LOGGER.warn("Gradle file event buffer overflow, dropping state");
                    signalLostState();
                }
            }

            @Override
            public void reportError(Throwable ex) {
                LOGGER.error("Error while receiving file changes", ex);
                fileWatchingStatistics.updateAndGet(statistics -> statistics.errorWhileReceivingFileChanges(ex));
                signalLostState();
            }

            private void signalLostState() {
                fileEvents.clear();
                try {
                    fileEvents.put(FileEvent.lostState());
                } catch (InterruptedException e) {
                    //noinspection ResultOfMethodCallIgnored
                    Thread.interrupted();
                    throw new RuntimeException(e);
                }
            }
        });
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
        stopping = true;
        try {
            watcher.close();
        } finally {
            consumeEvents = false;
            eventConsumerThread.interrupt();
        }
    }

    protected interface FileWatcherCreator {
        FileWatcher createWatcher(FileWatcherCallback callback);
    }

    private static class FileEvent {
        final Path path;
        final FileWatcherRegistry.Type type;
        final boolean lostState;

        public static FileEvent lostState() {
            return new FileEvent(null, null, true);
        }

        public static FileEvent changed(Type type, Path path) {
            return new FileEvent(path, type, false);
        }

        private FileEvent(@Nullable Path path, @Nullable FileWatcherRegistry.Type type, boolean lostState) {
            this.path = path;
            this.type = type;
            this.lostState = lostState;
        }
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
