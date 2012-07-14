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
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

@Requires(TestPrecondition.JDK7)
class FilePermissionHandlerFactoryOnJdk7Test extends Specification {
    @Rule TemporaryFolder temporaryFolder
    final Chmod chmod = FilePermissionHandlerFactory.services.get(Chmod)
    final Stat stat = FilePermissionHandlerFactory.services.get(Stat)

    @Requires(TestPrecondition.WINDOWS)
    def "creates EmptyChmod instance on Windows OS"() {
        expect:
        chmod instanceof FilePermissionHandlerFactory.EmptyChmod
    }

    @Requires(TestPrecondition.WINDOWS)
    def "creates FallbackStat instance on Windows OS"() {
        expect:
        stat instanceof FilePermissionHandlerFactory.FallbackStat
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "createDefaultFilePermissionHandler creates Jdk7PosixFilePermissionHandler on JDK7 with Posix Fs"() {
        expect:
        chmod.class.name == "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler"
        stat.class.name == "org.gradle.internal.nativeplatform.filesystem.jdk7.PosixJdk7FilePermissionHandler"
    }

    @Requires(TestPrecondition.UNKNOWN_OS)
    def "createDefaultFilePermissionHandler creates ComposedFilePermissionHandler with disabled Chmod on Unknown OS"() {
        setup:
        def File file = temporaryFolder.createFile("testFile")
        def originalMode = stat.getUnixMode(file);
        when:
        chmod.chmod(file, mode);
        then:
        originalMode == stat.getUnixMode(file);
        where:
        mode << [0722, 0644, 0744, 0755]
    }
}
