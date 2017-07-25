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

import org.gradle.api.invocation.Gradle;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.util.GradleVersion;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultTaskOutputFilesRepository implements TaskOutputFilesRepository, Closeable {

    private final static String CACHE_DISPLAY_NAME = "Build Output Cleanup Cache";

    private final CacheRepository cacheRepository;
    private final PersistentCache cacheAccess;
    private final FileSystemMirror fileSystemMirror;
    private final PersistentIndexedCache<String, Boolean> outputFiles; // The value is true if it is an output file, false if it is a parent of an output file

    public DefaultTaskOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, FileSystemMirror fileSystemMirror, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        this.cacheRepository = cacheRepository;
        this.cacheAccess = createCache(gradle);
        this.fileSystemMirror = fileSystemMirror;
        this.outputFiles = cacheAccess.createCache(cacheParameters(inMemoryCacheDecoratorFactory));
    }

    private static PersistentIndexedCacheParameters<String, Boolean> cacheParameters(InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new PersistentIndexedCacheParameters<String, Boolean>("outputFiles", String.class, Boolean.class)
            .cacheDecorator(inMemoryCacheDecoratorFactory.decorator(100000, true));
    }

    @Override
    public boolean isGeneratedByGradle(File file) {
        File absoluteFile = file.getAbsoluteFile();
        return containsFilesGeneratedByGradle(absoluteFile) || isContainedInAnOutput(absoluteFile);
    }

    private Boolean isContainedInAnOutput(File file) {
        File currentFile = file;
        while (currentFile != null) {
            if (outputFiles.get(currentFile.getPath()) == Boolean.TRUE) {
                return true;
            }
            currentFile = currentFile.getParentFile();
        }
        return false;
    }

    private boolean containsFilesGeneratedByGradle(File file) {
        return outputFiles.get(file.getPath()) != null;
    }

    @Override
    public void recordOutputs(TaskExecution taskExecution) {
        for (String outputFilePath : taskExecution.getDeclaredOutputFilePaths()) {
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

    protected PersistentCache createCache(Gradle gradle) {
        return cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName(CACHE_DISPLAY_NAME)
            .withLockOptions(mode(FileLockManager.LockMode.None))
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
    }

    @Override
    public void close() throws IOException {
        cacheAccess.close();
    }
}
