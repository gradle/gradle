/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import com.google.common.hash.Hashing
import org.gradle.api.file.RelativePath
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import spock.lang.Specification

class CachingClasspathEntryHasherTest extends Specification {
    def delegate = Mock(ClasspathEntryHasher)
    def fileDetails = new RegularFileSnapshot("path", RelativePath.parse(true, "path"), false, new FileHashSnapshot(Hashing.md5().hashInt(0)))
    def cachingHasher = new CachingClasspathEntryHasher(delegate, new InMemoryIndexedCache(new HashCodeSerializer()))

    def "returns result from delegate"() {
        def expectedHash = Hashing.md5().hashInt(1)
        when:
        def actualHash = cachingHasher.hash(fileDetails)
        then:
        1 * delegate.hash(fileDetails) >> expectedHash
        actualHash == expectedHash
        0 * _
    }

    def "caches the result"() {
        def expectedHash = Hashing.md5().hashInt(1)
        when:
        def actualHash = cachingHasher.hash(fileDetails)
        then:
        1 * delegate.hash(fileDetails) >> expectedHash
        actualHash == expectedHash
        0 * _

        when:
        actualHash = cachingHasher.hash(fileDetails)
        then:
        actualHash == expectedHash
        0 * _
    }

    def "caches 'no signature' results too"() {
        def noSignature = null
        when:
        def actualHash = cachingHasher.hash(fileDetails)
        then:
        1 * delegate.hash(fileDetails) >> noSignature
        actualHash == noSignature
        0 * _

        when:
        actualHash = cachingHasher.hash(fileDetails)
        then:
        actualHash == noSignature
        0 * _
    }
}
