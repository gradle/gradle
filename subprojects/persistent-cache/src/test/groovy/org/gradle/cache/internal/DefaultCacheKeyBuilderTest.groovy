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

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.HashFunction
import org.gradle.internal.hash.Hasher
import spock.lang.Specification

import static org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec
import static org.gradle.internal.hash.HashUtil.compactStringFor

class DefaultCacheKeyBuilderTest extends Specification {

    def hashFunction = Mock(HashFunction)
    def fileHasher = Mock(FileHasher)
    def classpathHasher = Mock(ClasspathHasher)
    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def subject = new DefaultCacheKeyBuilder(hashFunction, fileHasher, classpathHasher, classLoaderHierarchyHasher)

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
        def stringHash = HashCode.fromInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + string)

        then:
        1 * hashFunction.hashString(string) >> stringHash
        0 * _

        and:
        key == "$prefix/${compactStringFor(stringHash)}"
    }

    def 'given a File component, it should hash it and append it to the prefix'() {
        given:
        def prefix = 'p'
        def file = new File('f')
        def fileHash = HashCode.fromInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + file)

        then:
        1 * fileHasher.hash(file) >> fileHash
        0 * _

        and:
        key == "$prefix/${compactStringFor(fileHash)}"
    }

    def 'given a ClassPath component, it should snapshot it and append it to the prefix'() {
        given:
        def prefix = 'p'
        def classPath = DefaultClassPath.of([new File('f')])
        def classPathHash = HashCode.fromInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + classPath)

        then:
        1 * classpathHasher.hash(classPath) >> classPathHash
        0 * _

        and:
        key == "$prefix/${compactStringFor(classPathHash)}"
    }

    def 'given a ClassLoader component, it should hash its hierarchy and append it to the prefix'() {
        given:
        def prefix = 'p'
        def classLoader = Mock(ClassLoader)
        def classLoaderHierarchyHash = HashCode.fromInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + classLoader)

        then:
        1 * classLoaderHierarchyHasher.getClassLoaderHash(classLoader) >> classLoaderHierarchyHash
        0 * _

        and:
        key == "$prefix/${compactStringFor(classLoaderHierarchyHash)}"
    }

    def 'given a HashCode component, it should append it to the prefix'() {
        given:
        def prefix = 'p'
        def hash = HashCode.fromInt(42)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + hash)

        then:
        0 * _

        and:
        key == "$prefix/${compactStringFor(hash)}"
    }

    def 'given more than one component, it should combine their hashes together and append the combined hash to the prefix'() {
        given:
        def prefix = 'p'
        def string = 's'
        def stringHash = HashCode.fromInt(42)
        def file = new File('f')
        def fileHash = HashCode.fromInt(51)
        def hasher = Mock(Hasher)
        def combinedHash = HashCode.fromInt(99)

        when:
        def key = subject.build(CacheKeySpec.withPrefix(prefix) + string + file)

        then:
        1 * hashFunction.newHasher() >> hasher
        1 * hashFunction.hashString(string) >> stringHash
        1 * fileHasher.hash(file) >> fileHash
        1 * hasher.putHash(stringHash)
        1 * hasher.putHash(fileHash)
        1 * hasher.hash() >> combinedHash
        0 * _

        and:
        key == "$prefix/${compactStringFor(combinedHash)}"
    }

}
