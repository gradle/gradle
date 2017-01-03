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

import org.gradle.internal.nativeintegration.filesystem.FileType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class FallbackFileMetadataAccessorTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def accessor = new FallbackFileMetadataAccessor()

    def "stats missing file"() {
        def file = tmpDir.file("missing")

        expect:
        def stat = accessor.stat(file)
        stat.type == FileType.Missing
        stat.lastModified == 0
        stat.length == 0
    }

    def "stats regular file"() {
        def file = tmpDir.file("file")
        file.text = "123"

        expect:
        def stat = accessor.stat(file)
        stat.type == FileType.RegularFile
        stat.lastModified == file.lastModified()
        stat.length == 3
    }

    def "stats directory"() {
        def dir = tmpDir.file("dir").createDir()

        expect:
        def stat = accessor.stat(dir)
        stat.type == FileType.Directory
        stat.lastModified == 0
        stat.length == 0
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "stats symlink"() {
        def file = tmpDir.file("file")
        file.text = "123"
        def link = tmpDir.file("link")
        link.createLink(file)

        expect:
        def stat = accessor.stat(link)
        stat.type == FileType.RegularFile
        stat.lastModified == file.lastModified()
        stat.length == 3
    }
}
