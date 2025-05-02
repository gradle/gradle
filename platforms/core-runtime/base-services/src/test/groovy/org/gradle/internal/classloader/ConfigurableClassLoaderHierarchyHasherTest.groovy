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

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import spock.lang.Specification

class ConfigurableClassLoaderHierarchyHasherTest extends Specification {
    def classLoaderFactory = Mock(HashingClassLoaderFactory)
    def runtimeLoader = getClass().getClassLoader()
    def hasher = hasher((runtimeLoader): "system")

    def "hashes known classloader"() {
        expect:
        hasher.getClassLoaderHash(runtimeLoader) == hashFor("system")
    }

    def "hashes unknown classloader"() {
        def unknownLoader = Mock(ClassLoader)
        expect:
        hasher.getClassLoaderHash(unknownLoader) == null
    }

    def "hashes hashed classloader"() {
        def hashedLoader = new DelegatingLoader(runtimeLoader)
        def hashedLoaderHash = TestHashCodes.hashCodeFrom(123456)

        when:
        hasher.getClassLoaderHash(hashedLoader) == hashFor(hashedLoaderHash)

        then:
        _ * classLoaderFactory.getClassLoaderClasspathHash(hashedLoader) >> hashedLoaderHash
    }

    def "hashes known classloader with parent"() {
        def classLoader = new DelegatingLoader(runtimeLoader)
        def hasher = hasher([
            (runtimeLoader): "system",
            (classLoader): "this"
        ])
        expect:
        hasher.getClassLoaderHash(classLoader) == hashFor("this")
    }

    def "hashes unknown classloader with parent"() {
        def classLoader = new DelegatingLoader(runtimeLoader)
        expect:
        hasher.getClassLoaderHash(classLoader) == null
    }

    private ConfigurableClassLoaderHierarchyHasher hasher(Map<ClassLoader, String> classLoaders) {
        classLoaders = new HashMap<>(classLoaders)
        classLoaders.put(ClassLoader.getSystemClassLoader(), "system")
        return new ConfigurableClassLoaderHierarchyHasher(classLoaders, classLoaderFactory)
    }

    private static HashCode hashFor(Object... values) {
        def hasher = Hashing.newHasher()
        values.each {
            if (it instanceof String) {
                hasher.putString(it)
            } else if (it instanceof HashCode) {
                hasher.putHash(it)
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
