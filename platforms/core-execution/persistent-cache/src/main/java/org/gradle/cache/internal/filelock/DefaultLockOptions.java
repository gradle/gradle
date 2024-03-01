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

public class DefaultLockOptions implements LockOptions {

    private FileLockManager.LockMode mode;
    private boolean crossVersion;

    private DefaultLockOptions(FileLockManager.LockMode mode, boolean crossVersion) {
        this.mode = mode;
        this.crossVersion = crossVersion;
    }

    public static DefaultLockOptions mode(FileLockManager.LockMode lockMode) {
        return new DefaultLockOptions(lockMode, false);
    }

    public DefaultLockOptions useCrossVersionImplementation() {
        crossVersion = true;
        return this;
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
    public LockOptions copyWithMode(FileLockManager.LockMode mode) {
        return new DefaultLockOptions(mode, crossVersion);
    }

    @Override
    public String toString() {
        return "DefaultLockOptions{" +
            "mode=" + mode +
            ", crossVersion=" + crossVersion +
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

        if (crossVersion != that.crossVersion) {
            return false;
        }
        if (mode != that.mode) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + (crossVersion ? 1 : 0);
        return result;
    }
}
