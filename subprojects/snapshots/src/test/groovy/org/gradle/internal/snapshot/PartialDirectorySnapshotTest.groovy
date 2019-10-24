/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot

import com.google.common.collect.ImmutableList
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import spock.lang.Specification

class PartialDirectorySnapshotTest extends Specification {
    def "can obtain metadata from Directory"() {
        def metadataSnapshot = new PartialDirectorySnapshot("some/prefix", ImmutableList.of())
        expect:
        metadataSnapshot.getSnapshot("/absolute/path", "/absolute/path".length() + 1).get() == metadataSnapshot
        !metadataSnapshot.getSnapshot("another/path", 0).present
    }

    def "invalidating something in a directory retains the directory information"() {
        def metadataSnapshot = new PartialDirectorySnapshot("some/prefix", ImmutableList.of(new RegularFileSnapshot("/absolute/some/prefix/whatever", "whatever", HashCode.fromInt(1234), new FileMetadata(2, 3))))

        expect:
        metadataSnapshot.invalidate("whatever").get().getSnapshot("/absolute/some/prefix", "/absolute/some/prefix".length() + 1).get().type == FileType.Directory
    }
}
