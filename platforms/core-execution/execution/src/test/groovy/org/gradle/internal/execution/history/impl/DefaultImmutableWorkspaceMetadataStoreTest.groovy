/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableListMultimap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.time.Duration

import static org.gradle.internal.hash.TestHashCodes.hashCodeFrom

@CleanupTestDirectory
class DefaultImmutableWorkspaceMetadataStoreTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def workspace = temporaryFolder.createDir("workspace")
    def store = new DefaultImmutableWorkspaceMetadataStore()

    def "can serialize and deserialize metadata"() {
        def outputHashes = ImmutableListMultimap.<String, HashCode>builder()
            .putAll("out1", hashCodeFrom(0x1234), hashCodeFrom(0x2345))
            .putAll("out2", hashCodeFrom(0x3456))
            .build()
        def metadata = new ImmutableWorkspaceMetadata(new OriginMetadata("test-invocation-id", Duration.ofSeconds(123)), outputHashes)

        when:
        store.storeWorkspaceMetadata(workspace, metadata)

        then:
        workspace.file("metadata.bin").assertIsFile()

        when:
        def loadedMetadata = store.loadWorkspaceMetadata(workspace)

        then:
        loadedMetadata == metadata
    }
}
