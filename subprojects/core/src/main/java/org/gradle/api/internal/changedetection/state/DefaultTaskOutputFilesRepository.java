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
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.serialize.BaseSerializerFactory;
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
    private final PersistentIndexedCache<String, Boolean> outputFilesHistory;

    public DefaultTaskOutputFilesRepository(CacheRepository cacheRepository, Gradle gradle, FileSystemMirror fileSystemMirror) {
        this.cacheRepository = cacheRepository;
        this.cacheAccess = createCache(gradle);
        this.fileSystemMirror = fileSystemMirror;
        this.outputFilesHistory = cacheAccess.createCache("outputFilesHistory", String.class, BaseSerializerFactory.BOOLEAN_SERIALIZER);
    }

    @Override
    public boolean isGeneratedByGradle(final File file) {
        return cacheAccess.useCache(new Factory<Boolean>() {
            @Override
            public Boolean create() {
                File currentFile = file;
                do {
                    if (outputFilesHistory.get(currentFile.getAbsolutePath()) != null) {
                        return true;
                    }
                    currentFile = currentFile.getParentFile();
                } while (currentFile != null);
                return false;
            }
        });
    }

    @Override
    public void recordOutputs(final TaskExecution taskExecution) {
        cacheAccess.useCache(new Runnable() {
            @Override
            public void run() {
                for (String outputFilePath : taskExecution.getDeclaredOutputFilePaths()) {
                    FileSnapshot fileSnapshot = fileSystemMirror.getFile(outputFilePath);
                    boolean exists = fileSnapshot == null ? new File(outputFilePath).exists() : fileSnapshot.getType() != FileType.Missing;
                    if (exists) {
                        outputFilesHistory.put(outputFilePath, Boolean.TRUE);
                    }
                }
            }
        });
    }

    protected PersistentCache createCache(Gradle gradle) {
        return cacheRepository
            .cache(gradle, "buildOutputCleanup")
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .withDisplayName(CACHE_DISPLAY_NAME)
            .withLockOptions(mode(FileLockManager.LockMode.None).useCrossVersionImplementation())
            .withProperties(Collections.singletonMap("gradle.version", GradleVersion.current().getVersion()))
            .open();
    }

    @Override
    public void close() throws IOException {
        cacheAccess.close();
    }
}
