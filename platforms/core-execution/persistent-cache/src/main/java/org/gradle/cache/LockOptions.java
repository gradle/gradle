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

    /**
     * The alternate lock directory to use.
     *
     * If this is non-{@code null}, the lock file will <strong>NOT</strong> be created in the cache's content directory,
     * it will be created in this directory instead.  This is only valid when the {@link LockTargetType} does
     * <strong>NOT</strong> equal {@link LockTargetType#CachePropertiesFile}.
     */
    @Nullable File getAlternateLockDir();

    boolean isCrossVersionImplementation();

    LockTargetType getLockTargetType();

    /**
     * Creates a copy of this options instance using the given mode.
     *
     * @param mode the mode to overwrite the current mode with
     */
    LockOptions copyWithMode(FileLockManager.LockMode mode);

    /**
     * Calculates the lock target for a cache using these options with a given content directory and properties file.
     *
     * We call it a "target" because it could be a file or a directory.  It should <strong>NOT</strong> be confused with the
     * contents of the cache, or the lock file itself.
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
     * Calculates the {@code .lock} file for a given lock target.
     *
     * @param lockTarget the lock target as calculated by {@link #determineLockTarget(File, File)}, which may
     *      be a file or directory based on the lock target type
     * @return the lock file that will be used
     */
    static File determineLockFile(File lockTarget) {
        if (lockTarget.isDirectory()) {
            return new File(lockTarget, lockTarget.getName() + ".lock");
        } else {
            return new File(lockTarget.getParentFile(), lockTarget.getName() + ".lock");
        }
    }

    /**
     * Ensures that the combination of options values is valid.
     *
     * @throws IllegalStateException if this instance is not valid
     */
    default void assertValid() {
        if (getLockTargetType() == LockTargetType.CachePropertiesFile && getAlternateLockDir() != null) {
            throw new IllegalStateException("Cannot use alternate lock directory with lock target type: " + getLockTargetType());
        }
    }

    /**
     * The type of lock to use when generating a lock file for a {@link PersistentCache}.
     *
     * The default is {@link LockTargetType#DefaultTarget}, which generates a file with the same name as the cache,
     * located in the cache's content directory, is identical in behavior to {@link LockTargetType#CacheDirectory}
     * and should be used in most cases.  This file will be re-located out of the content dir if the alternate
     * lock directory value at {@link #getAlternateLockDir()} is non-{@code null}.
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
