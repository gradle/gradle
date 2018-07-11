/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

public abstract class AbstractNormalizedFileSnapshot implements NormalizedFileSnapshot {
    private final FileType type;
    private final HashCode normalizedContentHash;

    public AbstractNormalizedFileSnapshot(FileType type, HashCode normalizedContentHash) {
        this.type = type;
        this.normalizedContentHash = normalizedContentHash;
    }

    @Override
    public final void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString(getNormalizedPath());
        hasher.putHash(getNormalizedContentHash());
    }

    @Override
    public final int compareTo(NormalizedFileSnapshot o) {
        int result = getNormalizedPath().compareTo(o.getNormalizedPath());
        if (result == 0) {
            result = getNormalizedContentHash().compareTo(o.getNormalizedContentHash());
        }
        return result;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractNormalizedFileSnapshot that = (AbstractNormalizedFileSnapshot) o;
        return normalizedContentHash.equals(that.normalizedContentHash)
            && getNormalizedPath().equals(that.getNormalizedPath());
    }

    @Override
    public final int hashCode() {
        int result = normalizedContentHash.hashCode();
        result = 31 * result + getNormalizedPath().hashCode();
        return result;
    }

    @Override
    public final String toString() {
        return String.format("'%s' / %s", getNormalizedPath(), getType() == FileType.Directory ? "DIR" : getType() == FileType.Missing ? "MISSING" : normalizedContentHash);
    }

    @Override
    public HashCode getNormalizedContentHash() {
        return normalizedContentHash;
    }

    @Override
    public FileType getType() {
        return type;
    }
}
