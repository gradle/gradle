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

package org.gradle.internal.nativeplatform.filesystem

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification
import com.google.common.io.Files

@Requires(TestPrecondition.JDK7)
class FilePermissionHandlerFactoryOnJdk7Test extends Specification{

    @Requires(TestPrecondition.WINDOWS)
    def "createDefaultFilePermissionHandler creates WindowsFilePermissionHandler on Windows OS"(){
        when:
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        then:
        handler instanceof WindowsFilePermissionHandler
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "createDefaultFilePermissionHandler creates Jdk7PosixFilePermissionHandler on JDK7 with Posix Fs"(){
        when:
        def handler = FilePermissionHandlerFactory.createDefaultFilePermissionHandler()
        then:
        handler.getClass().name == "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler"
    }

    @Requires(TestPrecondition.UNKNOWN_OS)
        def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with disabled Chmod on Unknown OS"() {
            setup:
            def File dir = Files.createTempDir();
            def File file = new File(dir, "f")
            Files.touch(file)
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
