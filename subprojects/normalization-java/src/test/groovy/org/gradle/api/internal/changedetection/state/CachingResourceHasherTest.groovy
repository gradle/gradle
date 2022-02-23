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

import org.gradle.api.internal.file.archive.ZipEntry
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.fingerprint.hashing.ResourceHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification

class CachingResourceHasherTest extends Specification {
    def delegate = Mock(ResourceHasher)
    def path = "some"
    def snapshotterCacheService = Mock(ResourceSnapshotterCacheService)
    private RegularFileSnapshot snapshot = new RegularFileSnapshot(path, "path", TestHashCodes.hashCodeFrom(456), DefaultFileMetadata.file(3456, 456, AccessType.DIRECT))
    def snapshotContext = new DefaultRegularFileSnapshotContext({path}, snapshot)
    def cachingHasher = new CachingResourceHasher(delegate, snapshotterCacheService)


    def "uses cache service for snapshots"() {
        def expectedHash = TestHashCodes.hashCodeFrom(123)
        when:
        cachingHasher.hash(snapshotContext)
        then:
        1 * snapshotterCacheService.hashFile(snapshotContext, delegate, _)
        0 * _
    }

    def "does not cache zip entries"() {
        def expectedHash = TestHashCodes.hashCodeFrom(123)
        def zipEntry = Mock(ZipEntry)
        def zipEntryContext = new DefaultZipEntryContext(zipEntry, "foo", "foo.zip")

        when:
        def actualHash = cachingHasher.hash(zipEntryContext)

        then:
        1 * delegate.hash(zipEntryContext) >> expectedHash
        0 * _

        actualHash == expectedHash

        when:
        actualHash = cachingHasher.hash(zipEntryContext)

        then:
        1 * delegate.hash(zipEntryContext) >> expectedHash
        0 * _

        actualHash == expectedHash
    }
}
