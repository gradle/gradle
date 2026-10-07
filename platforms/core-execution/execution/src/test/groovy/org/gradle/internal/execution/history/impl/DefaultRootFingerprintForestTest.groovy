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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.cache.IndexedCache
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.RootFingerprint
import org.gradle.internal.fingerprint.impl.DefaultRootFingerprintInterner
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

import java.util.function.Function

class DefaultRootFingerprintForestTest extends Specification {
    def cache = new InMemoryIndexedCache()
    def strategyConfigurationHash = TestHashCodes.hashCodeFrom(6543)
    def forest = new DefaultRootFingerprintForest(cache, new DefaultRootFingerprintInterner())

    def "stores each root once"() {
        def first = root("/a", 1)
        def second = root("/b", 2)

        when:
        forest.store(fingerprint(first, second))
        forest.store(fingerprint(second, first))

        then:
        cache.puts == 2
        cache.entries.values() as Set == [first, second] as Set
    }

    def "loads stored roots and shares them"() {
        def stored = root("/a", 1)
        forest.store(fingerprint(stored))
        def otherForest = new DefaultRootFingerprintForest(cache, new DefaultRootFingerprintInterner())

        when:
        def loaded = otherForest.load("/a", TestHashCodes.hashCodeFrom(1), strategyConfigurationHash)

        then:
        loaded.is(stored)
        otherForest.load("/a", TestHashCodes.hashCodeFrom(1), strategyConfigurationHash).is(loaded)
        forest.load("/a", TestHashCodes.hashCodeFrom(1), strategyConfigurationHash).is(stored)
    }

    def "loaded root is not written again"() {
        forest.store(fingerprint(root("/a", 1)))
        def otherForest = new DefaultRootFingerprintForest(cache, new DefaultRootFingerprintInterner())

        when:
        def loaded = otherForest.load("/a", TestHashCodes.hashCodeFrom(1), strategyConfigurationHash)
        otherForest.store(fingerprint(loaded))

        then:
        cache.puts == 1
    }

    def "fails to load a root that is not stored"() {
        when:
        forest.load("/a", TestHashCodes.hashCodeFrom(1), strategyConfigurationHash)

        then:
        thrown(MissingRootFingerprintException)
    }

    private SerializableFileCollectionFingerprint fingerprint(RootFingerprint... roots) {
        new SerializableFileCollectionFingerprint(ImmutableList.copyOf(roots), strategyConfigurationHash, TestHashCodes.hashCodeFrom(99))
    }

    private static RootFingerprint root(String path, int hash) {
        new RootFingerprint(path, TestHashCodes.hashCodeFrom(hash), ImmutableMap.of(
            path, IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(hash))))
    }

    private static class InMemoryIndexedCache implements IndexedCache<HashCode, RootFingerprint> {
        final Map<HashCode, RootFingerprint> entries = [:]
        int puts

        @Override
        RootFingerprint getIfPresent(HashCode key) {
            entries[key]
        }

        @Override
        RootFingerprint get(HashCode key, Function<? super HashCode, ? extends RootFingerprint> producer) {
            entries.computeIfAbsent(key, producer)
        }

        @Override
        void put(HashCode key, RootFingerprint value) {
            puts++
            entries[key] = value
        }

        @Override
        void remove(HashCode key) {
            entries.remove(key)
        }
    }
}
