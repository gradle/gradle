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

package org.gradle.internal.hash

import org.apache.commons.lang.RandomStringUtils
import spock.lang.Specification

import java.security.MessageDigest

import static java.lang.Thread.currentThread
import static java.util.concurrent.CompletableFuture.supplyAsync
import static java.util.concurrent.Executors.newFixedThreadPool

class HashingTest extends Specification {

    def 'hasher can be reused'() {
        given:
        def values = 'a'..'z'
        def hasher = Hashing.newHasher()

        when:
        values.each { hasher.putString(it) }
        def hash = hasher.hash()

        then:
        hasher.putString(values.join())
        def differentHash = hasher.hash()
        hash != differentHash

        then:
        values.each { hasher.putString(it) }
        def reusedHasher = hasher.hash()
        hash == reusedHasher
    }

    def "reused hashes should return the same values as new ones"() {
        given:
        def sharedHasher = Hashing.md5().defaultHasher()
        def range = 0..10_000
        def inputs = range.collect { RandomStringUtils.random(it).getBytes() }

        expect:
        inputs.forEach { input ->
            sharedHasher.putBytes(input)

            MessageDigest newHasher = MessageDigest.getInstance("MD5")
            newHasher.update([input.length, (input.length >> 8), (input.length >> 16), (input.length >> 24)] as byte[])
            newHasher.update(input)

            assert sharedHasher.hash().toByteArray() == newHasher.digest()
        }
    }

    def 'hasher can be used from multiple threads'() {
        given:
        def threadRange = 1..100
        def hasher = Hashing.newHasher()

        when:
        def hashes = threadRange.collect {
            supplyAsync({
                hasher.putString(currentThread().name)
                hasher.hash().toString()
            }, newFixedThreadPool(threadRange.size()))
        }*.join().toSet()

        then:
        hashes.size() == threadRange.size()
    }

    def 'null does not collide with other values'() {
        expect:
        def hasher = Hashing.newHasher()
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
        def hasher = Hashing.newHasher()
        hasher.putString(value)
        return hasher.hash()
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
