/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching

import org.gradle.internal.hash.HashCode
import org.gradle.testing.internal.util.Specification

class BuildCacheService2Test extends Specification {
    def "alma"() {
        def key = new TestBuildCacheKey(HashCode.fromInt(123))
        BuildCache cache = Mock(BuildCache);
        cache.load(key) { hit, context ->

        }
    }

    interface BuildCache {
        boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException;
    }

    interface BuildCacheHit {
        Map<String, Entry> getOutputProperties()
    }

    interface BuildCacheEntryReaderContext {
        void request()
    }

    interface BuildCacheEntryReader {
        void processHit(BuildCacheHit hit, BuildCacheEntryReaderContext context);
    }

    interface Entry extends Comparable<Entry> {
        HashCode getHashCode();
    }

    interface MerkleTree extends Entry {
        Map<String, Entry> getEntries();
    }

    interface RegularFile extends Entry {
    }

    static class TestBuildCacheKey implements BuildCacheKey {
        private final HashCode hashCode;

        TestBuildCacheKey(HashCode hashCode) {
            this.hashCode = hashCode
        }

        @Override
        String getHashCode() {
            return hashCode.toString();
        }

        @Override
        String getDisplayName() {
            return "Key $hashCode"
        }
    }
}
