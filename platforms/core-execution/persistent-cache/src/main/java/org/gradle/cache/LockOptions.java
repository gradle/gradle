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
     * Calculates the lock target for a cache using these options with a given content directory and properties file.
     *
     * We call it a "target" because it could be a file or a directory.  It should <strong>NOT</strong> be confused with the
     * contents of the cache.
     *
     * @param cacheContentDir the cache's content directory
     * @param cachePropertiesFile the cache's properties file
     * @return the lock target that will be used (either the cache dir or alternate lock dir, or the properties file)
     */
    default File determineLockTarget(File cacheContentDir, File cachePropertiesFile) {
        switch (getLockTargetType()) {
            case CacheDirectory:
            case DefaultTarget:
                return getAlternateLockDir() == null ? cacheContentDir : getAlternateLockDir();
            case CachePropertiesFile:
                return cachePropertiesFile;
            default:
                throw new IllegalArgumentException("Unsupported lock target type: " + getLockTargetType());
        }
    }

    /**
     * Calculates the {@code .lock} file for a given lock target, as calculated by {@link #determineLockTarget(File, File)}.
     *
     * @param lockTarget the lock target, which may be a file or directory based on the lock target type
     * @return the lock file that will be used
     */
    default File determineLockFile(File lockTarget) {
        if (lockTarget.isDirectory()) {
            return new File(lockTarget, lockTarget.getName() + ".lock");
        } else {
            return new File(lockTarget.getParentFile(), lockTarget.getName() + ".lock");
        }
    }

    /**
     * The type of lock to target use when generating a lock file for a {@link PersistentCache}.
     *
     * The default is {@link LockTargetType#DefaultTarget}, which generates a file with the same name as the cache,
     * is identical in behavior to {@link LockTargetType#CacheDirectory }and should be used in most cases.  This file
     * is located within the cache's content directory by default,
     * but this will be replaced by the value at {@link #getAlternateLockDir()} instead if it is not {@code null}.
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
