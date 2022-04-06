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

package org.gradle.internal.nativeintegration.filesystem.services

import net.rubygrapefruit.platform.Native
import net.rubygrapefruit.platform.file.Files
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor
import org.gradle.util.UsesNativeServices
import static org.gradle.test.fixtures.FileMetadataTestFixture.maybeRoundLastModified

import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributeView

@UsesNativeServices
class NativePlatformBackedFileMetadataAccessorTest extends AbstractFileMetadataAccessorTest {

    @Override
    FileMetadataAccessor getAccessor() {
        return new NativePlatformBackedFileMetadataAccessor(Native.get(Files.class))
    }

    @Override
    void assertSameLastModified(FileMetadata fileMetadata, File file) {
        assert maybeRoundLastModified(fileMetadata.lastModified) == maybeRoundLastModified(lastModifiedViaJavaNio(file))
    }

    private static long lastModifiedViaJavaNio(File file) {
        return java.nio.file.Files.getFileAttributeView(file.toPath(), BasicFileAttributeView, LinkOption.NOFOLLOW_LINKS).readAttributes().lastModifiedTime().toMillis()
    }
}
