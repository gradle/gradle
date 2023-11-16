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

import org.gradle.api.NonNullApi;
import org.gradle.cache.FileLockManager.LockMode;
import org.gradle.cache.LockOptions;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Objects;

/**
 * Default implementation of {@link LockOptions}.
 *
 * Defaults to {@code mode=OnDemand, crossVersion=false, alternateLockDir=null, lockTargetType=DefaultTarget} unless otherwise specified.
 */
@NonNullApi
public class DefaultLockOptions implements LockOptions {
    private final LockMode mode;
    private final boolean crossVersion;
    @Nullable private final File alternateLockDir;
    private final LockTargetType lockTargetType;

    public DefaultLockOptions() {
        this(LockMode.OnDemand);
    }

    public DefaultLockOptions(LockMode mode) {
        this(mode, false);
    }

    public DefaultLockOptions(LockMode mode, boolean crossVersion) {
        this(mode, crossVersion, null);
    }

    public DefaultLockOptions(LockMode mode, boolean crossVersion, @Nullable File alternateLockDir) {
        this(mode, crossVersion, alternateLockDir, LockTargetType.DefaultTarget);
    }

    public DefaultLockOptions(LockMode mode, boolean crossVersion, @Nullable File alternateLockDir, LockTargetType lockTargetType) {
        this.mode = mode;
        this.crossVersion = crossVersion;
        this.alternateLockDir = alternateLockDir;
        this.lockTargetType = lockTargetType;
        assertValid();
    }

    @Override
    public LockMode getMode() {
        return mode;
    }

    @Override
    public @Nullable File getAlternateLockDir() {
        return alternateLockDir;
    }

    @Override
    public LockTargetType getLockTargetType() {
        return lockTargetType;
    }

    @Override
    public boolean isCrossVersionImplementation() {
        return crossVersion;
    }

    @Override
    public LockOptions copyWithMode(LockMode mode) {
        return new DefaultLockOptions(mode, crossVersion, alternateLockDir, lockTargetType);
    }

    @Override
    public String toString() {
        return "DefaultLockOptions{" +
            "mode=" + mode +
            ", crossVersion=" + crossVersion +
            ", alternateLockDir=" + alternateLockDir +
            ", lockTargetType=" + lockTargetType +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultLockOptions that = (DefaultLockOptions) o;

        if (crossVersion != that.crossVersion) {
            return false;
        }
        if (mode != that.mode) {
            return false;
        }
        if (!Objects.equals(alternateLockDir, that.alternateLockDir)) {
            return false;
        }
        return lockTargetType == that.lockTargetType;
    }

    @Override
    public int hashCode() {
        int result = mode != null ? mode.hashCode() : 0;
        result = 31 * result + (crossVersion ? 1 : 0);
        result = 31 * result + (alternateLockDir != null ? alternateLockDir.hashCode() : 0);
        result = 31 * result + (lockTargetType != null ? lockTargetType.hashCode() : 0);
        return result;
    }
}
