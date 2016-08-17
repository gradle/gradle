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

import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

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
            result = compareHashCodes(this, o);
        }
        return result;
    }

    static int compareHashCodes(NormalizedFileSnapshot a, NormalizedFileSnapshot b) {
        int result;
        byte[] hashCode = a.getSnapshot().getHash().asBytes();
        byte[] oHashCode = b.getSnapshot().getHash().asBytes();
        int len = hashCode.length;
        result = len - oHashCode.length;
        if (result == 0) {
            for (int idx = 0; idx < len; idx++) {
                result = hashCode[idx] - oHashCode[idx];
                if (result != 0) {
                    break;
                }
            }
        }
        return result;
    }
}
