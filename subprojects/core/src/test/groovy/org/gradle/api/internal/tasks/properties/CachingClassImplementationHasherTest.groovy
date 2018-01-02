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

package org.gradle.api.internal.tasks.properties

import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class CachingClassImplementationHasherTest extends Specification {

    def classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)
    def classImplementationHasher = new CachingClassImplementationHasher(classLoaderHierarchyHasher)

    def "implementation hash is cashed"() {
        when:
        def implementationHash = classImplementationHasher.getImplementationHash(Integer)
        then:
        1 * classLoaderHierarchyHasher.getClassLoaderHash(Integer.getClassLoader()) >> HashCode.fromString("012345abcdef")
        implementationHash != null

        when:
        def cachedImplementationHash = classImplementationHasher.getImplementationHash(Integer)
        then:
        0 * _
        cachedImplementationHash == implementationHash
    }

    def "unknown classloader yields null implemenation hash"() {
        when:
        def implementationHash = classImplementationHasher.getImplementationHash(Integer)
        then:
        1 * classLoaderHierarchyHasher.getClassLoaderHash(Integer.getClassLoader()) >> null
        implementationHash == null

        when:
        implementationHash = classImplementationHasher.getImplementationHash(Integer)
        then:
        0 * _
        implementationHash == null
    }
}
