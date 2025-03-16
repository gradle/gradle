/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.caching.impl;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheKeyInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.hash.HashCode;

public class DefaultBuildCacheKey implements BuildCacheKeyInternal {
    private final HashCode hashCode;

    public DefaultBuildCacheKey(HashCode hashCode) {
        this.hashCode = hashCode;
    }

    @Override
    public String getHashCode() {
        return hashCode.toString();
    }

    @Override
    public HashCode getHashCodeInternal() {
        return hashCode;
    }

    @Override
    public byte[] toByteArray() {
        return hashCode.toByteArray();
    }

    @Deprecated
    @Override
    public String getDisplayName() {
        // TODO Switch to SimpleBuildCacheKey once this method is removed
        DeprecationLogger.deprecateMethod(BuildCacheKey.class, "getDisplayName()")
            .replaceWith("getHashCode()")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();
        return getHashCode();
    }

    @Override
    public String toString() {
        return getHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultBuildCacheKey that = (DefaultBuildCacheKey) o;

        return hashCode.equals(that.hashCode);
    }

    @Override
    public int hashCode() {
        return hashCode.hashCode();
    }
}
