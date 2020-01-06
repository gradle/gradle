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

import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class DefaultOutputFilesRepository implements OutputFilesRepository, Closeable {

    private final PersistentCache cacheAccess;
    private final PersistentIndexedCache<String, Boolean> outputFiles; // The value is true if it is an output file, false if it is a parent of an output file

    public DefaultOutputFilesRepository(PersistentCache cacheAccess, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.cacheAccess = cacheAccess;
        this.outputFiles = cacheAccess.createCache(cacheParameters(inMemoryCacheDecoratorFactory));
    }

    @Override
    public boolean isGeneratedByGradle(File file) {
        File absoluteFile = file.getAbsoluteFile();
        return containsFilesGeneratedByGradle(absoluteFile) || isContainedInAnOutput(absoluteFile);
    }

    private boolean isContainedInAnOutput(File absoluteFile) {
        File currentFile = absoluteFile;
        while (currentFile != null) {
            if (outputFiles.get(currentFile.getPath()) == Boolean.TRUE) {
                return true;
            }
            currentFile = currentFile.getParentFile();
        }
        return false;
    }

    private boolean containsFilesGeneratedByGradle(File absoluteFile) {
        return outputFiles.get(absoluteFile.getPath()) != null;
    }

    @Override
    public void recordOutputs(Iterable<? extends FileSystemSnapshot> outputFileFingerprints) {
        for (FileSystemSnapshot outputFileFingerprint : outputFileFingerprints) {
            outputFileFingerprint.accept(new FileSystemSnapshotVisitor() {
                @Override
                public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    recordOutputSnapshot(directorySnapshot);
                    return false;
                }

                @Override
                public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                    if (fileSnapshot.getType() == FileType.RegularFile) {
                        recordOutputSnapshot(fileSnapshot);
                    }
                }

                private void recordOutputSnapshot(CompleteFileSystemLocationSnapshot directorySnapshot) {
                    String outputFilePath = directorySnapshot.getAbsolutePath();
                    File outputFile = new File(outputFilePath);
                    outputFiles.put(outputFilePath, Boolean.TRUE);
                    File outputFileParent = outputFile.getParentFile();
                    while (outputFileParent != null) {
                        String parentPath = outputFileParent.getPath();
                        if (outputFiles.get(parentPath) != null) {
                            break;
                        }
                        outputFiles.put(parentPath, Boolean.FALSE);
                        outputFileParent = outputFileParent.getParentFile();
                    }
                }

                @Override
                public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                }
            });
        }
    }

    private static PersistentIndexedCacheParameters<String, Boolean> cacheParameters(InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return PersistentIndexedCacheParameters.of("outputFiles", String.class, Boolean.class)
            .withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(100000, true));
    }

    @Override
    public void close() throws IOException {
        cacheAccess.close();
    }
}
