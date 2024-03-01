/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class DefaultOutputFilesRepository implements OutputFilesRepository, Closeable {

    private final PersistentCache cacheAccess;
    private final IndexedCache<String, Boolean> outputFiles; // The value is true if it is an output file, false if it is a parent of an output file

    public DefaultOutputFilesRepository(PersistentCache cacheAccess, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.cacheAccess = cacheAccess;
        this.outputFiles = cacheAccess.createIndexedCache(cacheParameters(inMemoryCacheDecoratorFactory));
    }

    @Override
    public boolean isGeneratedByGradle(File file) {
        File absoluteFile = file.getAbsoluteFile();
        return containsFilesGeneratedByGradle(absoluteFile) || isContainedInAnOutput(absoluteFile);
    }

    private boolean isContainedInAnOutput(File absoluteFile) {
        File currentFile = absoluteFile;
        while (currentFile != null) {
            if (outputFiles.getIfPresent(currentFile.getPath()) == Boolean.TRUE) {
                return true;
            }
            currentFile = currentFile.getParentFile();
        }
        return false;
    }

    private boolean containsFilesGeneratedByGradle(File absoluteFile) {
        return outputFiles.getIfPresent(absoluteFile.getPath()) != null;
    }

    @Override
    public void recordOutputs(Iterable<? extends FileSystemSnapshot> outputSnapshots) {
        for (FileSystemSnapshot outputFileSnapshot : outputSnapshots) {
            outputFileSnapshot.accept(entrySnapshot -> {
                entrySnapshot.accept(new FileSystemLocationSnapshotVisitor() {
                    @Override
                    public void visitDirectory(DirectorySnapshot directorySnapshot) {
                        recordOutputSnapshot(directorySnapshot);
                    }

                    @Override
                    public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                        recordOutputSnapshot(fileSnapshot);
                    }

                    private void recordOutputSnapshot(FileSystemLocationSnapshot snapshot) {
                        String outputPath = snapshot.getAbsolutePath();
                        File outputFile = new File(outputPath);
                        outputFiles.put(outputPath, Boolean.TRUE);
                        File outputFileParent = outputFile.getParentFile();
                        while (outputFileParent != null) {
                            String parentPath = outputFileParent.getPath();
                            if (outputFiles.getIfPresent(parentPath) != null) {
                                break;
                            }
                            outputFiles.put(parentPath, Boolean.FALSE);
                            outputFileParent = outputFileParent.getParentFile();
                        }
                    }
                });
                return SnapshotVisitResult.SKIP_SUBTREE;
            });
        }
    }

    private static IndexedCacheParameters<String, Boolean> cacheParameters(InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return IndexedCacheParameters.of("outputFiles", String.class, Boolean.class)
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(100000, true));
    }

    @Override
    public void close() throws IOException {
        cacheAccess.close();
    }
}
