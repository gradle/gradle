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

package org.gradle.caching.internal;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;

/**
 * A hasher used for build cache keys.
 *
 * In order to avoid collisions we prepend the length of the next bytes to the underlying hasher (see this <a href="http://crypto.stackexchange.com/a/10065">answer</a> on stackexchange).
 */
public class DefaultBuildCacheHasher implements BuildCacheHasher {
    private final Hasher hasher = Hashing.md5().newHasher();

    @Override
    public DefaultBuildCacheHasher putByte(byte value) {
        hasher.putInt(1);
        hasher.putByte(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putBytes(byte[] bytes) {
        hasher.putInt(bytes.length);
        hasher.putBytes(bytes);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putBytes(byte[] bytes, int off, int len) {
        hasher.putInt(len);
        hasher.putBytes(bytes, off, len);
        return this;
    }

    @Override
    public BuildCacheHasher putHash(HashCode hashCode) {
        hasher.putInt(hashCode.length());
        hasher.putHash(hashCode);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putInt(int value) {
        hasher.putInt(4);
        hasher.putInt(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putLong(long value) {
        hasher.putInt(8);
        hasher.putLong(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putDouble(double value) {
        hasher.putInt(8);
        hasher.putDouble(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putBoolean(boolean value) {
        hasher.putInt(1);
        hasher.putBoolean(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putString(CharSequence value) {
        hasher.putInt(value.length());
        hasher.putString(value);
        return this;
    }

    @Override
    public DefaultBuildCacheHasher putNull() {
        this.putInt(0);
        return this;
    }

    @Override
    public HashCode hash() {
        return hasher.hash();
    }
}
