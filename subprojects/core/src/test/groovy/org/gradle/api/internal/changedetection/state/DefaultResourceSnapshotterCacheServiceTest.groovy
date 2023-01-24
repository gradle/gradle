/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.hashing.ResourceHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.testfixtures.internal.TestInMemoryIndexedCache
import spock.lang.Specification


class DefaultResourceSnapshotterCacheServiceTest extends Specification {
    def delegate = Mock(ResourceHasher)
    def path = "some"
    def snapshot = new RegularFileSnapshot(path, "path", TestHashCodes.hashCodeFrom(456), DefaultFileMetadata.file(3456, 456, FileMetadata.AccessType.DIRECT))
    def snapshotContext = new DefaultRegularFileSnapshotContext({path}, snapshot)
    def snapshotterCache = new DefaultResourceSnapshotterCacheService(new TestInMemoryIndexedCache(new HashCodeSerializer()))

    def "returns result from delegate"() {
        def expectedHash = TestHashCodes.hashCodeFrom(123)

        when:
        def actualHash = snapshotterCache.hashFile(snapshotContext, delegate, configurationHash)
        then:
        1 * delegate.hash(snapshotContext) >> expectedHash
        actualHash == expectedHash
        0 * _
    }

    def "caches the result"() {
        def expectedHash = TestHashCodes.hashCodeFrom(123)
        when:
        def actualHash = snapshotterCache.hashFile(snapshotContext, delegate, configurationHash)
        then:
        1 * delegate.hash(snapshotContext) >> expectedHash
        actualHash == expectedHash
        0 * _

        when:
        actualHash = snapshotterCache.hashFile(snapshotContext, delegate, configurationHash)
        then:
        actualHash == expectedHash
        0 * _
    }

    def "caches 'no signature' results too"() {
        def noSignature = null
        when:
        def actualHash = snapshotterCache.hashFile(snapshotContext, delegate, configurationHash)
        then:
        1 * delegate.hash(snapshotContext) >> noSignature
        actualHash == noSignature
        0 * _

        when:
        actualHash = snapshotterCache.hashFile(snapshotContext, delegate, configurationHash)
        then:
        actualHash == noSignature
        0 * _
    }

    private HashCode getConfigurationHash() {
        def hasher = Hashing.newHasher()
        hasher.putString(delegate.getClass().getName())
        return hasher.hash()
    }
}
