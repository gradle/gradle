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

import com.google.common.base.Objects;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;
import org.gradle.internal.hash.HashUtil;

public abstract class AbstractNormalizedFileSnapshot implements NormalizedFileSnapshot {
    private final IncrementalFileSnapshot snapshot;

    public AbstractNormalizedFileSnapshot(IncrementalFileSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public IncrementalFileSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void appendToCacheKey(TaskCacheKeyBuilder hasher) {
        hasher.putString(getNormalizedPath());
        hasher.putBytes(getSnapshot().getHash().asBytes());
    }

    @Override
    public int compareTo(NormalizedFileSnapshot o) {
        int result = getNormalizedPath().compareTo(o.getNormalizedPath());
        if (result == 0) {
            result = HashUtil.compareHashCodes(getSnapshot().getHash(), o.getSnapshot().getHash());
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractNormalizedFileSnapshot that = (AbstractNormalizedFileSnapshot) o;
        return Objects.equal(getNormalizedPath(), that.getNormalizedPath())
            && Objects.equal(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getNormalizedPath(), snapshot);
    }
}
