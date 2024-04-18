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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;

public interface DirectorySnapshotterStatistics {
    /**
     * The number of visited directory trees.
     */
    long getVisitedHierarchyCount();

    /**
     * The number of visited directories.
     */
    long getVisitedDirectoryCount();

    /**
     * The number of visited files.
     */
    long getVisitedFiles();

    /**
     * The number of files we failed to visit.
     */
    long getFailedFiles();

    @ServiceScope(Scope.Global.class)
    class Collector {
        private final AtomicLong hierarchyCount = new AtomicLong();
        private final AtomicLong directoryCount = new AtomicLong();
        private final AtomicLong fileCount = new AtomicLong();
        private final AtomicLong failedFileCount = new AtomicLong();

        public void recordVisitHierarchy() {
            hierarchyCount.incrementAndGet();
        }

        public void recordVisitDirectory() {
            directoryCount.incrementAndGet();
        }

        public void recordVisitFile() {
            fileCount.incrementAndGet();
        }

        public void recordVisitFileFailed() {
            failedFileCount.incrementAndGet();
        }

        public DirectorySnapshotterStatistics collect() {
            long hierarchyCount = this.hierarchyCount.getAndSet(0);
            long directoryCount = this.directoryCount.getAndSet(0);
            long fileCount = this.fileCount.getAndSet(0);
            long failedFileCount = this.failedFileCount.getAndSet(0);

            return new DirectorySnapshotterStatistics() {
                @Override
                public long getVisitedHierarchyCount() {
                    return hierarchyCount;
                }

                @Override
                public long getVisitedDirectoryCount() {
                    return directoryCount;
                }

                @Override
                public long getVisitedFiles() {
                    return fileCount;
                }

                @Override
                public long getFailedFiles() {
                    return failedFileCount;
                }

                @Override
                public String toString() {
                    return MessageFormat.format("Snapshot {0,number,integer} directory hierarchies (visited {1,number,integer} directories, {2,number,integer} files and {3,number,integer} failed files)",
                        hierarchyCount, directoryCount, fileCount, failedFileCount);
                }
            };
        }
    }

    abstract class CollectingFileVisitor implements FileVisitor<Path> {
        protected final Collector collector;

        public CollectingFileVisitor(Collector collector) {
            this.collector = collector;
            collector.recordVisitHierarchy();
        }

        @Override
        public final FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            collector.recordVisitDirectory();
            return doPreVisitDirectory(dir, attrs);
        }

        protected abstract FileVisitResult doPreVisitDirectory(Path dir, BasicFileAttributes attrs);

        @Override
        public final FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            collector.recordVisitFile();
            return doVisitFile(file, attrs);
        }

        protected abstract FileVisitResult doVisitFile(Path file, BasicFileAttributes attrs);

        @Override
        public final FileVisitResult visitFileFailed(Path file, IOException exc) {
            collector.recordVisitFileFailed();
            return doVisitFileFailed(file, exc);
        }

        protected abstract FileVisitResult doVisitFileFailed(Path file, IOException exc);

        @Override
        public final FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            return doPostVisitDirectory(dir, exc);
        }

        protected abstract FileVisitResult doPostVisitDirectory(Path dir, IOException exc);
    }
}
