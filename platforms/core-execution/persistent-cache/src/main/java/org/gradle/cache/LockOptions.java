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

public interface LockOptions {

    FileLockManager.LockMode getMode();

    @Nullable
    File getLockDir();

    boolean isUseCrossVersionImplementation();

    // TODO: rename this (LockTargetType?)
    LockTarget getLockTarget();

    /**
     * Creates a copy of these options with the given mode.
     */
    LockOptions copyWithMode(FileLockManager.LockMode mode);

    default File getLockTarget(File cacheDir, File propertiesFile) {
        switch (getLockTarget()) {
            case CacheDirectory:
            case DefaultTarget:
                return getLockDir() == null ? cacheDir : getLockDir();
            case CachePropertiesFile:
                return propertiesFile;
            default:
                throw new IllegalArgumentException("Unsupported lock target: " + getLockTarget());
        }
    }

    static File determineLockTargetFile(File target) {
        if (target.isDirectory()) {
            return new File(target, target.getName() + ".lock");
        } else {
            return new File(target.getParentFile(), target.getName() + ".lock");
        }
    }

    // TODO: Rename this type (LockTarget is overloaded)
    enum LockTarget {
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
