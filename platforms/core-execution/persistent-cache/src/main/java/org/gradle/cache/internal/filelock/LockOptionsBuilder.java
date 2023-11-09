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
package org.gradle.cache.internal.filelock;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;

import javax.annotation.Nullable;
import java.io.File;

// TODO: This needs cleanup
public class LockOptionsBuilder implements LockOptions {

    private FileLockManager.LockMode mode;
    private boolean crossVersion;
    @Nullable private final File lockDir;
    private final LockTarget lockTarget;

    public LockOptionsBuilder() {
        this(FileLockManager.LockMode.OnDemand);
    }

    public LockOptionsBuilder(FileLockManager.LockMode mode) {
        this(mode, false);
    }

    public LockOptionsBuilder(FileLockManager.LockMode mode, boolean crossVersion) {
        this(mode, crossVersion, null);
    }

    public LockOptionsBuilder(FileLockManager.LockMode mode, boolean crossVersion, @Nullable File lockDir) {
        this(mode, crossVersion, lockDir, LockTarget.DefaultTarget);
    }

    public LockOptionsBuilder(FileLockManager.LockMode mode, boolean crossVersion, @Nullable File lockDir, LockTarget lockTarget) {
        this.mode = mode;
        this.crossVersion = crossVersion;
        this.lockDir = lockDir;
        this.lockTarget = lockTarget;
    }

    public LockOptionsBuilder useCrossVersionImplementation() {
        crossVersion = true;
        return this;
    }

    @Override
    public FileLockManager.LockMode getMode() {
        return mode;
    }

    @Nullable
    @Override
    public File getLockDir() {
        return lockDir;
    }

    @Override
    public LockTarget getLockTarget() {
        return lockTarget;
    }

    @Override
    public boolean isUseCrossVersionImplementation() {
        return crossVersion;
    }

    @Override
    public LockOptions copyWithMode(FileLockManager.LockMode mode) {
        return new LockOptionsBuilder(mode, crossVersion, lockDir);
    }

    @Override
    public String toString() {
        return mode + " (simple=" + crossVersion + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LockOptionsBuilder)) {
            return false;
        }

        LockOptionsBuilder that = (LockOptionsBuilder) o;

        if (crossVersion != that.crossVersion) {
            return false;
        }
        if (mode != that.mode) {
            return false;
        }
        if (lockDir != that.lockDir) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + (crossVersion ? 1 : 0);
        result = 31 * result + (lockDir != null ? lockDir.hashCode() : 0);
        return result;
    }
}
