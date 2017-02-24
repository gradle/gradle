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

/**
 * An immutable snapshot of the state of some value.
 */
public interface ValueSnapshot {
    /**
     * Appends the snapshot to the given hasher.
     */
    void appendToHasher(BuildCacheHasher hasher);

    /**
     * Returns true if the given value is known to be the same as the value represented by this snapshot. Returns false if it is not cheap to determine this, or the value is different.
     */
    boolean maybeSameValue(Object value);
}
