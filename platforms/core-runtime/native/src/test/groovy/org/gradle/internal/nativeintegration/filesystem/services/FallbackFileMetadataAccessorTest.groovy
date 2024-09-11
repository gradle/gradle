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

import org.gradle.internal.file.AbstractFileMetadataAccessorTest
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.util.UsesNativeServices

import static org.gradle.internal.file.FileMetadata.AccessType.DIRECT

@UsesNativeServices
class FallbackFileMetadataAccessorTest extends AbstractFileMetadataAccessorTest {
    FileMetadataAccessor getAccessor() {
        new FallbackFileMetadataAccessor()
    }

    @Override
    void assertSameLastModified(FileMetadata fileMetadata, File file) {
        assert fileMetadata.lastModified == file.lastModified()
    }

    @Override
    void assertSameAccessType(FileMetadata fileMetadata, FileMetadata.AccessType accessType) {
        // Via the old Java API, it is impossible to decide whether a location is a symbolic link or not.
        assert fileMetadata.accessType == DIRECT
    }
}
