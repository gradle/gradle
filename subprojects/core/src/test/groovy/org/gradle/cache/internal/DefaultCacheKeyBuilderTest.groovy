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

package org.gradle.cache.internal

import com.google.common.base.Charsets
import com.google.common.hash.HashCode
import com.google.common.hash.HashFunction
import com.google.common.hash.Hasher
import org.gradle.api.internal.hash.FileHasher
import org.gradle.internal.classloader.ClassPathSnapshot
import org.gradle.internal.classloader.ClassPathSnapshotter
import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

import static org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

class DefaultCacheKeyBuilderTest extends Specification {

    def hashFunction = Mock(HashFunction)
    def fileHasher = Mock(FileHasher)
    def snapshotter = Mock(ClassPathSnapshotter)
    def subject = new DefaultCacheKeyBuilder(hashFunction, fileHasher, snapshotter)

    def 'given just a prefix, it should return it'() {
        given:
        def prefix = 'p'

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix))

        then:
        0 * _

        and:
        key == prefix
    }

    def 'given a String component, it should hash it and append it to the prefix'() {
        given:
        def prefix = 'p'
        def string = 's'
        def stringHash = bigInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + string)

        then:
        1 * hashFunction.hashString(string, Charsets.UTF_8) >> hashCodeFrom(stringHash)
        0 * _

        and:
        key == "$prefix/${stringHash.toString(36)}"
    }

    def 'given a File component, it should hash it and append it to the prefix'() {
        given:
        def prefix = 'p'
        def file = new File('f')
        def fileHash = bigInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + file)

        then:
        1 * fileHasher.hash(file) >> hashCodeFrom(fileHash)
        0 * _

        and:
        key == "$prefix/${fileHash.toString(36)}"
    }

    def 'given a ClassPath component, it should snapshot it and append it to the prefix'() {
        given:
        def prefix = 'p'
        def classPath = DefaultClassPath.of([new File('f')])
        def classPathHash = bigInt(42)
        def classPathSnapshot = Mock(ClassPathSnapshot)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + classPath)

        then:
        1 * snapshotter.snapshot(classPath) >> classPathSnapshot
        1 * classPathSnapshot.getStrongHash() >> hashCodeFrom(classPathHash)
        0 * _

        and:
        key == "$prefix/${classPathHash.toString(36)}"
    }

    def 'given more than one component, it should combine their hashes together and append the combined hash to the prefix'() {
        given:
        def prefix = 'p'
        def string = 's'
        def file = new File('f')
        def fileHash = bigInt(51)
        def hasher = Mock(Hasher)
        def combinedHash = bigInt(99)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + string + file)

        then:
        1 * hashFunction.newHasher() >> hasher
        1 * hasher.putString(string, Charsets.UTF_8) >> hasher
        1 * fileHasher.hash(file) >> hashCodeFrom(fileHash)
        1 * hasher.putBytes(fileHash.toByteArray())
        1 * hasher.hash() >> hashCodeFrom(combinedHash)
        0 * _

        and:
        key == "$prefix/${combinedHash.toString(36)}"
    }

    private BigInteger bigInt(long val) {
        BigInteger.valueOf(val)
    }

    private HashCode hashCodeFrom(BigInteger bigInteger) {
        HashCode.fromBytes(bigInteger.toByteArray())
    }
}
