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

package org.gradle.internal.nativeintegration.filesystem.services

import org.gradle.internal.file.FileMetadata
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor
import org.gradle.internal.nativeintegration.filesystem.jdk7.NioFileMetadataAccessor
import org.gradle.util.UsesNativeServices

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView

@UsesNativeServices
class NioFileMetadataAccessorTest extends AbstractFileMetadataAccessorTest {
    FileMetadataAccessor getAccessor() {
        new NioFileMetadataAccessor()
    }

    @Override
    void assertSameLastModified(FileMetadata fileMetadata, File file) {
        assert fileMetadata.lastModified == lastModified(file)
    }

    private static long lastModified(File file) {
        return Files.getFileAttributeView(file.toPath(), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes().lastModifiedTime().toMillis()
    }
}
