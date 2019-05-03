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

import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import spock.lang.Specification

class CachingResourceHasherTest extends Specification {
    def delegate = Mock(ResourceHasher)
    def path = "some"
    def relativePath = ["relative", "path"]
    private RegularFileSnapshot snapshot = new RegularFileSnapshot(path, "path", HashCode.fromInt(456), 456)
    def cachingHasher = new CachingResourceHasher(delegate, new DefaultResourceSnapshotterCacheService(new InMemoryIndexedCache(new HashCodeSerializer())))

    def "returns result from delegate"() {
        def expectedHash = HashCode.fromInt(123)
        when:
        def actualHash = cachingHasher.hash(snapshot)
        then:
        1 * delegate.hash(snapshot) >> expectedHash
        actualHash == expectedHash
        0 * _
    }

    def "caches the result"() {
        def expectedHash = HashCode.fromInt(123)
        when:
        def actualHash = cachingHasher.hash(snapshot)
        then:
        1 * delegate.hash(snapshot) >> expectedHash
        actualHash == expectedHash
        0 * _

        when:
        actualHash = cachingHasher.hash(snapshot)
        then:
        actualHash == expectedHash
        0 * _
    }

    def "caches 'no signature' results too"() {
        def noSignature = null
        when:
        def actualHash = cachingHasher.hash(snapshot)
        then:
        1 * delegate.hash(snapshot) >> noSignature
        actualHash == noSignature
        0 * _

        when:
        actualHash = cachingHasher.hash(snapshot)
        then:
        actualHash == noSignature
        0 * _
    }

    def "does not cache zip entries"() {
        def expectedHash = HashCode.fromInt(123)
        def inputStream = Mock(InputStream)
        def zipEntry = Mock(ZipEntry)

        when:
        def actualHash = cachingHasher.hash(zipEntry)

        then:
        1 * delegate.hash(zipEntry) >> expectedHash
        0 * _

        actualHash == expectedHash

        when:
        actualHash = cachingHasher.hash(zipEntry)

        then:
        1 * delegate.hash(zipEntry) >> expectedHash
        0 * _

        actualHash == expectedHash
    }
}
