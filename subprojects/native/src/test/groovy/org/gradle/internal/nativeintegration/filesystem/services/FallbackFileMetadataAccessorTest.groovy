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

package org.gradle.internal.nativeintegration.filesystem.services

import org.gradle.internal.file.FileMetadataSnapshot
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor
import org.gradle.util.UsesNativeServices

import static org.gradle.internal.file.FileMetadataSnapshot.AccessType.DIRECT

@UsesNativeServices
class FallbackFileMetadataAccessorTest extends AbstractFileMetadataAccessorTest {
    FileMetadataAccessor getAccessor() {
        new FallbackFileMetadataAccessor()
    }

    @Override
    void assertSameLastModified(FileMetadataSnapshot metadataSnapshot, File file) {
        assert metadataSnapshot.lastModified == file.lastModified()
    }

    @Override
    void assertSameAccessType(FileMetadataSnapshot metadataSnapshot, FileMetadataSnapshot.AccessType accessType) {
        // Via the old Java API, it is impossible to decide whether a location is a symbolic link or not.
        assert metadataSnapshot.accessType == DIRECT
    }
}
