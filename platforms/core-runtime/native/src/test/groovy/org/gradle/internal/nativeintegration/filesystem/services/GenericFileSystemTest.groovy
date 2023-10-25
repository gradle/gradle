/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.FileException
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator
import org.gradle.internal.nativeintegration.filesystem.Symlink
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GenericFileSystemTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def fileModeMutator = Stub(FileModeMutator)
    def fileModeAccessor = Stub(FileModeAccessor)
    def symlink = Stub(Symlink)
    def fileMetadataAccessor = Stub(FileMetadataAccessor)
    def statistics = Mock(StatStatistics.Collector)
    def fileSystemFactory = new GenericFileSystem.Factory(fileMetadataAccessor, statistics, TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.testDirectory))
    def fileSystem = fileSystemFactory.create(fileModeMutator, fileModeAccessor, symlink)

    def "wraps failure to set file mode"() {
        def failure = new RuntimeException()
        def file = new File("does-not-exist")

        given:
        fileModeMutator.chmod(_, _) >> { throw failure }

        when:
        fileSystem.chmod(file, 0640)

        then:
        FileException e = thrown()
        e.message == "Could not set file mode 640 on '$file'."
    }

    def "wraps failure to get file mode"() {
        def failure = new RuntimeException()
        def file = new File("does-not-exist")

        given:
        fileModeAccessor.getUnixMode(_, true) >> { throw failure }

        when:
        fileSystem.getUnixMode(file)

        then:
        FileException e = thrown()
        e.message == "Could not get file mode for '$file'."
    }

    def "wraps failure to get create symlink"() {
        def failure = new RuntimeException()
        def file = new File("does-not-exist")
        def target = new File("target")

        given:
        symlink.symlink(_, _) >> { throw failure }

        when:
        fileSystem.createSymbolicLink(file, target)

        then:
        FileException e = thrown()
        e.message == "Could not create symlink from '$file' to '$target'."
    }
}
