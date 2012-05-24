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
public class FilePermissionHandlerFactoryOnNonJdk7Test extends Specification {

    @Rule TemporaryFolder temporaryFolder

    @Requires(TestPrecondition.WINDOWS)
    def "createChmod creates EmptyChmod instance on Windows OS"() {
        when:
        def chmod = FilePermissionHandlerFactory.createChmod()
        then:
        chmod instanceof FilePermissionHandlerFactory.EmptyChmod
    }

    @Requires(TestPrecondition.WINDOWS)
    def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with disabled Chmod on Windows OS"() {
        when:
        def chmod = FilePermissionHandlerFactory.createChmod()
        then:
        chmod instanceof FilePermissionHandlerFactory.EmptyChmod
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "createDefaultFilePermissionHandler creates ComposeableFilePermissionHandler if not on Windows OS"() {
        when:
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        then:
        handler instanceof ComposableFilePermissionHandler
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with enabled Chmod on Unknown OS"() {
        setup:
        def file = temporaryFolder.createFile("testFile")
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        when:
        handler.chmod(file, mode);
        then:
        mode == handler.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "Throws IOException for failed chmod calls"() {
        setup:
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        def notExistingFile = new File(temporaryFolder.createDir(), "nonexisting.file");
        when:
        handler.chmod(notExistingFile, 622);
        then:
        def e = thrown(IOException)
        e.message == "Failed to set file permissions 622 on file nonexisting.file. errno: 2"
    }

    @Requires(TestPrecondition.UNKNOWN_OS)
    def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with disabled Chmod on Unknown OS"() {
        setup:
        def file = temporaryFolder.createFile("testFile")

        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        def originalMode = handler.getUnixMode(file);
        when:
        handler.chmod(file, mode);
        then:
        originalMode == handler.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }
}
