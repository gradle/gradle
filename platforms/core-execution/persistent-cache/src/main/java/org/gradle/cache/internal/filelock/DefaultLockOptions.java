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

import static com.google.common.base.Preconditions.checkArgument;
import static org.gradle.cache.FileLockManager.LockMode.Shared;

public class DefaultLockOptions implements LockOptions {

    private final FileLockManager.LockMode mode;
    private final boolean crossVersion;
    private final boolean ensureAcquiredLockRepresentsStateOnFileSystem;

    private DefaultLockOptions(FileLockManager.LockMode mode, boolean crossVersion, boolean ensureAcquiredLockRepresentsStateOnFileSystem) {
        this.mode = mode;
        this.crossVersion = crossVersion;
        this.ensureAcquiredLockRepresentsStateOnFileSystem = ensureAcquiredLockRepresentsStateOnFileSystem;
    }

    public static DefaultLockOptions mode(FileLockManager.LockMode lockMode) {
        return new DefaultLockOptions(lockMode, false, false);
    }

    public DefaultLockOptions useCrossVersionImplementation() {
        return new DefaultLockOptions(mode, true, ensureAcquiredLockRepresentsStateOnFileSystem);
    }

    public DefaultLockOptions ensureAcquiredLockRepresentsStateOnFileSystem() {
        checkArgument(this.mode != Shared && !crossVersion, "Shared or cross-version locks are not supported with ensureAcquiredLockRepresentsStateOnFileSystem() option.");
        return new DefaultLockOptions(mode, crossVersion, true);
    }

    @Override
    public FileLockManager.LockMode getMode() {
        return mode;
    }

    @Override
    public boolean isUseCrossVersionImplementation() {
        return crossVersion;
    }

    @Override
    public boolean isEnsureAcquiredLockRepresentsStateOnFileSystem() {
        return ensureAcquiredLockRepresentsStateOnFileSystem;
    }

    @Override
    public LockOptions copyWithMode(FileLockManager.LockMode mode) {
        return new DefaultLockOptions(mode, crossVersion, ensureAcquiredLockRepresentsStateOnFileSystem);
    }

    @Override
    public String toString() {
        return "DefaultLockOptions{" +
            "mode=" + mode +
            ", crossVersion=" + crossVersion +
            ", ensureAcquiredLockRepresentsStateOnFileSystem=" + ensureAcquiredLockRepresentsStateOnFileSystem +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultLockOptions)) {
            return false;
        }

        DefaultLockOptions that = (DefaultLockOptions) o;
        return crossVersion == that.crossVersion
            && ensureAcquiredLockRepresentsStateOnFileSystem == that.ensureAcquiredLockRepresentsStateOnFileSystem
            && mode == that.mode;
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + (crossVersion ? 1 : 0);
        result = 31 * result + (ensureAcquiredLockRepresentsStateOnFileSystem ? 1 : 0);
        return result;
    }
}
