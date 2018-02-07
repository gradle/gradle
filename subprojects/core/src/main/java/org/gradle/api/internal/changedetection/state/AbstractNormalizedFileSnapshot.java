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

public abstract class AbstractNormalizedFileSnapshot implements NormalizedFileSnapshot {
    private final FileContentSnapshot snapshot;

    public AbstractNormalizedFileSnapshot(FileContentSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public final FileContentSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public final void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString(getNormalizedPath());
        hasher.putHash(getSnapshot().getContentMd5());
    }

    @Override
    public final int compareTo(NormalizedFileSnapshot o) {
        int result = getNormalizedPath().compareTo(o.getNormalizedPath());
        if (result == 0) {
            result = getSnapshot().getContentMd5().compareTo(o.getSnapshot().getContentMd5());
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
        return snapshot.equals(that.snapshot)
            && hasSameNormalizedPathAs(that);
    }

    @Override
    public final int hashCode() {
        int result = snapshot.hashCode();
        result = 31 * result + hashNormalizedPath();
        return result;
    }

    @Override
    public final String toString() {
        return String.format("'%s' / %s", getNormalizedPath(), snapshot);
    }

    /**
     * This is a performance optimization and must return the same result as
     * <pre>
     *     getNormalizedPath().equals(other.getNormalizedPath());
     * </pre>
     */
    protected boolean hasSameNormalizedPathAs(AbstractNormalizedFileSnapshot other) {
        return getNormalizedPath().equals(other.getNormalizedPath());
    }


    /**
     * This is a performance optimization and must return the same result as
     * <pre>
     *     getNormalizedPath().hashCode();
     * </pre>
     */
    protected int hashNormalizedPath() {
        return getNormalizedPath().hashCode();
    }
}
