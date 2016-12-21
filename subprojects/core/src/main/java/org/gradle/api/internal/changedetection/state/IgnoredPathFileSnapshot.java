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
import org.gradle.caching.internal.BuildCacheKeyBuilder;
import org.gradle.internal.hash.HashUtil;

public class IgnoredPathFileSnapshot implements NormalizedFileSnapshot {
    private final IncrementalFileSnapshot snapshot;

    public IgnoredPathFileSnapshot(IncrementalFileSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public String getNormalizedPath() {
        return "";
    }

    @Override
    public IncrementalFileSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public void appendToCacheKey(BuildCacheKeyBuilder builder) {
        builder.putBytes(snapshot.getHash().asBytes());
    }

    @Override
    public int compareTo(NormalizedFileSnapshot o) {
        if (!(o instanceof IgnoredPathFileSnapshot)) {
            return -1;
        }
        return HashUtil.compareHashCodes(getSnapshot().getHash(), o.getSnapshot().getHash());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IgnoredPathFileSnapshot that = (IgnoredPathFileSnapshot) o;
        return Objects.equal(snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }
}
