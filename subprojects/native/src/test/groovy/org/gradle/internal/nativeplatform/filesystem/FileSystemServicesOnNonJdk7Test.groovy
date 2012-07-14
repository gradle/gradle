/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.nativeplatform.filesystem;

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder

@Requires(TestPrecondition.NOT_JDK7)
public class FileSystemServicesOnNonJdk7Test extends Specification {
    @Rule TemporaryFolder temporaryFolder
    final Chmod chmod = FileSystemServices.services.get(Chmod)
    final Stat stat = FileSystemServices.services.get(Stat)
    final Symlink symlink = FileSystemServices.services.get(Symlink)

    @Requires(TestPrecondition.MAC_OS_X)
    def "creates LibCChmod on Mac"() {
        expect:
        chmod instanceof FileSystemServices.LibcChmod
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "creates LibCStat on Mac"() {
        expect:
        stat instanceof FileSystemServices.LibCStat
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "creates LibcSymlink on Mac"() {
        expect:
        symlink instanceof LibcSymlink
    }

    @Requires(TestPrecondition.LINUX)
    def "creates LibCChmod on Linux"() {
        expect:
        chmod instanceof FileSystemServices.LibcChmod
    }

    @Requires(TestPrecondition.LINUX)
    def "creates LibCStat on Linux"() {
        expect:
        stat instanceof FileSystemServices.LibCStat
    }

    @Requires(TestPrecondition.LINUX)
    def "creates LibcSymlink on Linux"() {
        expect:
        symlink instanceof LibcSymlink
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "Throws IOException for failed chmod calls"() {
        setup:
        def notExistingFile = new File(temporaryFolder.createDir(), "nonexisting.file")
        when:
        chmod.chmod(notExistingFile, 622)
        then:
        def e = thrown(IOException)
        e.message == "Failed to set file permissions 622 on file nonexisting.file. errno: 2"
    }

    @Requires(TestPrecondition.UNKNOWN_OS)
    def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with disabled Chmod on Unknown OS"() {
        setup:
        def file = temporaryFolder.createFile("testFile")

        def originalMode = stat.getUnixMode(file);
        when:
        chmod.chmod(file, mode);
        then:
        originalMode == stat.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }
}
