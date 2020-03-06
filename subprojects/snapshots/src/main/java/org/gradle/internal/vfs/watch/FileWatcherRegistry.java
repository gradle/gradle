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

package org.gradle.internal.vfs.watch;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface FileWatcherRegistry extends Closeable {

    interface ChangeHandler {
        void handleChange(Type type, Path path);

        void handleLostState();
    }

    enum Type {
        CREATED,
        MODIFIED,
        REMOVED,
        INVALIDATE
    }

    /**
     * Get statistics about the received changes.
     */
    FileWatchingStatistics getStatistics();

    /**
     * Close the watcher registry. Stops watching without handling the changes.
     */
    @Override
    void close() throws IOException;

    class FileWatchingStatistics {
        private final boolean unknownEventEncountered;
        private final int numberOfReceivedEvents;
        private final Throwable errorWhileReceivingFileChanges;

        public Optional<Throwable> getErrorWhileReceivingFileChanges() {
            return Optional.ofNullable(errorWhileReceivingFileChanges);
        }

        public FileWatchingStatistics(boolean unknownEventEncountered, int numberOfReceivedEvents, @Nullable Throwable errorWhileReceivingFileChanges) {
            this.unknownEventEncountered = unknownEventEncountered;
            this.numberOfReceivedEvents = numberOfReceivedEvents;
            this.errorWhileReceivingFileChanges = errorWhileReceivingFileChanges;
        }

        public boolean isUnknownEventEncountered() {
            return unknownEventEncountered;
        }

        public int getNumberOfReceivedEvents() {
            return numberOfReceivedEvents;
        }
    }
}
