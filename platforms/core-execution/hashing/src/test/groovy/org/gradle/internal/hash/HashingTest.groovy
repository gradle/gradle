/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.hash

import spock.lang.Specification

import static java.lang.Thread.currentThread
import static java.util.concurrent.CompletableFuture.supplyAsync
import static java.util.concurrent.Executors.newFixedThreadPool

class HashingTest extends Specification {
    def 'cannot call hash multiple times'() {
        given:
        def hasher = Hashing.newHasher()
        hasher.putInt(1)
        hasher.hash()

        when:
        hasher.hash()

        then:
        thrown(IllegalStateException)
    }

    def 'hashers can overlap'() {
        when:
        def hasher1 = Hashing.newHasher()
        hasher1.putInt(1)

        and:
        def hasher2 = Hashing.newHasher()
        hasher2.putInt(1)

        then: "closing them in reverse order"
        def hash2 = hasher2.hash()
        def hash1 = hasher1.hash()

        then:
        hash2 == hash1
    }

    def 'hasher works without calling final hash method'() {
        given:
        def value = ('a'..'z').join()

        when:
        def hasher1 = Hashing.newHasher()
        hasher1.putString(value)
        def hash = hasher1.hash()

        and:
        def hasher2 = Hashing.newHasher()
        hasher2.putString(value)
        // no call to hasher2.hash()

        and:
        def hasher3 = Hashing.newHasher()
        hasher3.putString(value)
        def hash3 = hasher3.hash()

        then:
        hash == hash3
    }

    def 'hasher can be used from multiple threads'() {
        given:
        def threadRange = 1..100

        when:
        def hashes = threadRange.collect {
            supplyAsync({ Hashing.hashString(currentThread().name) }, newFixedThreadPool(threadRange.size()))
        }*.join().toSet()

        then:
        hashes.size() == threadRange.size()
    }

    def 'null does not collide with other values'() {
        expect:
        def hasher = Hashing.newHasher()
        hasher.putNull()
        def hash = hasher.hash()
        hash != Hashing.hashString("abc")
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

    def hashStrings(List<String> strings) {
        def hasher = Hashing.newHasher()
        strings.each { hasher.putString(it) }
        hasher.hash()
    }

    def hashKey(List<List<Integer>> bytes) {
        def hasher = Hashing.newHasher()
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
