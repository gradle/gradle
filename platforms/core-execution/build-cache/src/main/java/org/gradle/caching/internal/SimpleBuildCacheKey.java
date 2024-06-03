/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.caching.internal;

import org.gradle.internal.hash.HashCode;

public class SimpleBuildCacheKey implements BuildCacheKeyInternal {
    private final HashCode hashCode;

    public SimpleBuildCacheKey(HashCode hashCode) {
        this.hashCode = hashCode;
    }

    @Override
    public HashCode getHashCodeInternal() {
        return hashCode;
    }

    @Override
    public String getHashCode() {
        return hashCode.toString();
    }

    // TODO Provide default implementation
    // TODO Deprecate and move this to BuildCacheKeyInternal
    @Override
    public byte[] toByteArray() {
        return hashCode.toByteArray();
    }

    // TODO Provide default implementation
    @Override
    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public String getDisplayName() {
        return getHashCode();
    }
}
