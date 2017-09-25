/*
 * Copyright 2017 the original author or authors.
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

public class ProviderSnapshot implements ValueSnapshot {
    private final ValueSnapshot valueSnapshot;

    ProviderSnapshot(ValueSnapshot valueSnapshot) {
        this.valueSnapshot = valueSnapshot;
    }

    public ValueSnapshot getValue() {
        return valueSnapshot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ProviderSnapshot other = (ProviderSnapshot) obj;
        return other.valueSnapshot.equals(valueSnapshot);
    }

    @Override
    public int hashCode() {
        return valueSnapshot.hashCode();
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (equals(snapshot)) {
            return this;
        }
        return snapshot;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        valueSnapshot.appendToHasher(hasher);
    }
}
