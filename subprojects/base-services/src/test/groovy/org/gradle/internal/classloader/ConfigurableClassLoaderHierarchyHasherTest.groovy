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

package org.gradle.internal.classloader

import com.google.common.base.Charsets
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import spock.lang.Specification

class ConfigurableClassLoaderHierarchyHasherTest extends Specification {
    def "hashes known classloader"() {
        def runtimeLoader = Mock(ClassLoader)
        def hasher = hasher([
            (runtimeLoader): "system"
        ])
        expect:
        hasher.getHash(runtimeLoader) == hashFor("system")
    }

    def "hashes unknown classloader"() {
        def runtimeLoader = Mock(ClassLoader)
        def hasher = hasher([:])
        expect:
        hasher.getHash(runtimeLoader) == hashFor("unknown")
    }

    def "hashes hashed classloader"() {
        def runtimeLoader = Mock(ClassLoader)
        def hashedLoaderHash = HashCode.fromLong(123456)
        def hashedLoader = new HashedClassLoader(runtimeLoader, hashedLoaderHash)
        def hasher = hasher([:])
        expect:
        hasher.getHash(hashedLoader) == hashFor(hashedLoaderHash)
    }

    def "hashes known classloader with parent"() {
        def runtimeLoader = Mock(ClassLoader)
        def classLoader = new DelegatingLoader(runtimeLoader)
        def hasher = hasher([
            (runtimeLoader): "system",
            (classLoader): "this"
        ])
        expect:
        hasher.getHash(classLoader) == hashFor("this", "system")
    }

    def "hashes unknown classloader with parent"() {
        def runtimeLoader = Mock(ClassLoader)
        def classLoader = new DelegatingLoader(runtimeLoader)
        def hasher = hasher([
            (runtimeLoader): "system"
        ])
        expect:
        hasher.getHash(classLoader) == hashFor("unknown", "system")
    }

    def "hashes hashed classloader with parent"() {
        def runtimeLoader = Mock(ClassLoader)
        def classLoader = new DelegatingLoader(runtimeLoader)
        def hashedLoaderHash = HashCode.fromLong(123456)
        def hashedLoader = new HashedClassLoader(classLoader, hashedLoaderHash)
        def hasher = hasher([
            (runtimeLoader): "system"
        ])
        expect:
        hasher.getHash(hashedLoader) == hashFor(hashedLoaderHash, "system")
    }

    private static ConfigurableClassLoaderHierarchyHasher hasher(Map<ClassLoader, String> classLoaders) {
        classLoaders = new HashMap<>(classLoaders)
        classLoaders.put(ClassLoader.getSystemClassLoader(), "system")
        return new ConfigurableClassLoaderHierarchyHasher(classLoaders)
    }

    private static HashCode hashFor(Object... values) {
        def hasher = Hashing.md5().newHasher()
        values.each {
            if (it instanceof String) {
                hasher.putString(it, Charsets.UTF_8)
            } else if (it instanceof HashCode) {
                hasher.putBytes(it.asBytes())
            } else {
                throw new AssertionError()
            }
        }
        return hasher.hash()
    }

    private static class DelegatingLoader extends ClassLoader {
        DelegatingLoader(ClassLoader parent) {
            super(parent)
        }
    }
}
