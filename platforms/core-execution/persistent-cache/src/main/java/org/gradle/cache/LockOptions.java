/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.cache;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Options that specify how to create and manage the lock file used to manage access to a {@link PersistentCache}.
 */
public interface LockOptions {
    FileLockManager.LockMode getMode();

    @Nullable File getAlternateLockDir();

    boolean isUseCrossVersionImplementation();

    LockTargetType getLockTargetType();

    /**
     * Creates a copy of these options with the given mode.
     */
    LockOptions copyWithMode(FileLockManager.LockMode mode);

    /**
     * Calculates the lock file for a cache using these options with a given content directory and properties file.
     *
     * @param cacheContentDir the cache's content directory
     * @param propertiesFile the cache's properties file
     * @return the lock file that will be used
     */
    default File determineLockFile(File cacheContentDir, File propertiesFile) {
        final File lockDir;
        switch (getLockTargetType()) {
            case CacheDirectory:
            case DefaultTarget:
                lockDir = getAlternateLockDir() == null ? cacheContentDir : getAlternateLockDir();
                break;
            case CachePropertiesFile:
                lockDir = propertiesFile;
                break;
            default:
                throw new IllegalArgumentException("Unsupported lock target: " + getLockTargetType());
        }

        if (lockDir.isDirectory()) {
            return new File(lockDir, lockDir.getName() + ".lock");
        } else {
            return new File(lockDir.getParentFile(), lockDir.getName() + ".lock");
        }
    }

    /**
     * The type of lock to use when generating a lock for a {@link PersistentCache}.
     *
     * The default is {@link LockTargetType#DefaultTarget}, which generates a file with the same name as the cache,
     * and should be used in most cases.
     */
    enum LockTargetType {
        /**
         * Use the cache properties file as the lock target, for backwards compatibility with old Gradle versions.
         */
        CachePropertiesFile,
        /**
         * Use the cache directory as the lock target, for backwards compatibility with old Gradle versions.
         */
        CacheDirectory,
        /**
         * Use the default target.
         */
        DefaultTarget,
    }
}
