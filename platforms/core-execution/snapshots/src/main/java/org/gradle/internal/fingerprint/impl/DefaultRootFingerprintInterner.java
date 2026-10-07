/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.fingerprint.RootFingerprint;
import org.gradle.internal.fingerprint.RootFingerprintInterner;
import org.gradle.internal.hash.HashCode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class DefaultRootFingerprintInterner implements RootFingerprintInterner {
    private final Cache<Key, RootFingerprint> roots = CacheBuilder.newBuilder()
        .weakValues()
        .build();

    @Nullable
    @Override
    public RootFingerprint find(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash) {
        return roots.getIfPresent(new Key(rootPath, rootHash, strategyConfigurationHash));
    }

    @Override
    public RootFingerprint intern(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash, Supplier<RootFingerprint> factory) {
        try {
            return roots.get(new Key(rootPath, rootHash, strategyConfigurationHash), factory::get);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static final class Key {
        private final String rootPath;
        private final HashCode rootHash;
        private final HashCode strategyConfigurationHash;

        Key(String rootPath, HashCode rootHash, HashCode strategyConfigurationHash) {
            this.rootPath = rootPath;
            this.rootHash = rootHash;
            this.strategyConfigurationHash = strategyConfigurationHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key key = (Key) o;
            return rootPath.equals(key.rootPath)
                && rootHash.equals(key.rootHash)
                && strategyConfigurationHash.equals(key.strategyConfigurationHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rootPath, rootHash, strategyConfigurationHash);
        }
    }
}
