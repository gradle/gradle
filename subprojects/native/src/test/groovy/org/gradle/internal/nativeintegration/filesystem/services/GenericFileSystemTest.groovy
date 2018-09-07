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
import org.gradle.internal.nativeintegration.filesystem.FileException
import org.gradle.internal.nativeintegration.filesystem.FileMetadataAccessor
import org.gradle.internal.nativeintegration.filesystem.FileModeAccessor
import org.gradle.internal.nativeintegration.filesystem.FileModeMutator
import org.gradle.internal.nativeintegration.filesystem.Symlink
import spock.lang.Specification

class GenericFileSystemTest extends Specification {
    def fileModeMutator = Stub(FileModeMutator)
    def fileModeAccessor = Stub(FileModeAccessor)
    def symlink = Stub(Symlink)
    def fileMetadataAccessor = Stub(FileMetadataAccessor)
    def fileSystem = new GenericFileSystem(fileModeMutator, fileModeAccessor, symlink, fileMetadataAccessor)

    def "wraps failure to set file mode"() {
        def failure = new RuntimeException()
        def file = new File("does-not-exist")

        given:
        fileModeMutator.chmod(_, _) >> { throw failure }

        when:
        fileSystem.chmod(file, 0640)

        then:
        def e = thrown FileException
        e.message == "Could not set file mode 640 on '$file'."
    }

    def "wraps failure to get file mode"() {
        def failure = new RuntimeException()
        def file = new File("does-not-exist")

        given:
        fileModeAccessor.getUnixMode(_) >> { throw failure }

        when:
        fileSystem.getUnixMode(file)

        then:
        def e = thrown FileException
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
        def e = thrown FileException
        e.message == "Could not create symlink from '$file' to '$target'."
    }
}
