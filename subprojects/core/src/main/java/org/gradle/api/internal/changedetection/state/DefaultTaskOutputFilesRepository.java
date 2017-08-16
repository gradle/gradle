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

package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.file.FileType;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class DefaultTaskOutputFilesRepository implements TaskOutputFilesRepository, Closeable {

    private final PersistentCache cacheAccess;
    private final FileSystemMirror fileSystemMirror;
    private final PersistentIndexedCache<String, Boolean> outputFiles; // The value is true if it is an output file, false if it is a parent of an output file

    public DefaultTaskOutputFilesRepository(PersistentCache cacheAccess, FileSystemMirror fileSystemMirror, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.cacheAccess = cacheAccess;
        this.fileSystemMirror = fileSystemMirror;
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
    public void recordOutputs(Iterable<String> outputFilePaths) {
        for (String outputFilePath : outputFilePaths) {
            FileSnapshot fileSnapshot = fileSystemMirror.getFile(outputFilePath);
            File outputFile = new File(outputFilePath);
            boolean exists = fileSnapshot == null ? outputFile.exists() : fileSnapshot.getType() != FileType.Missing;
            if (exists) {
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
        }
    }

    private static PersistentIndexedCacheParameters<String, Boolean> cacheParameters(InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new PersistentIndexedCacheParameters<String, Boolean>("outputFiles", String.class, Boolean.class)
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(100000, true));
    }

    @Override
    public void close() throws IOException {
        cacheAccess.close();
    }
}
