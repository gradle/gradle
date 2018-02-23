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

package org.gradle.caching.internal

import spock.lang.Specification

class DefaultBuildCacheHasherTest extends Specification {

    def 'null does not collide with other values'() {
        expect:
        def hasher = new DefaultBuildCacheHasher()
        hasher.putNull()
        def hash = hasher.hash()
        hash != hashKey("abc")
    }

    def 'hash collision for bytes'() {
        def left = [[1, 2, 3], [4, 5]]
        def right = [[1, 2], [3, 4, 5]]
        expect:
        hashKey(left) != hashKey(right)
    }

    def 'hash collision for strings'() {
        expect:
        hashStrings(["abc", "de"]) != hashStrings(["ab", "cde"])
    }

    def hashKey(String value) {
        def hasher = new DefaultBuildCacheHasher()
        hasher.putString(value).hash()
    }

    def hashStrings(List<String> strings) {
        def hasher = new DefaultBuildCacheHasher()
        strings.each { hasher.putString(it) }
        hasher.hash()
    }

    def hashKey(List<List<Integer>> bytes) {
        def hasher = new DefaultBuildCacheHasher()
        bytes.each {
            if (it.size() == 1) {
                hasher.putByte(it[0] as byte)
            } else {
                hasher.putBytes(it as byte[])
            }
        }
        hasher.hash()
    }
}
