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

package org.gradle.internal.nativeplatform

import spock.lang.Specification
import com.google.common.io.Files
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class GenericFileSystemTest extends Specification {

    FilePermissionHandler handler = Mock()
    GenericFileSystem fileSystem = new GenericFileSystem(handler)

    def "getUnixMode delegates to FilePermissionHandler"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)
        when:
        fileSystem.getUnixMode(file)
        then:
        1 * handler.getUnixMode(file);
    }

    def "getUnixMode throws FileNotFoundException if called on non existing file"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        when:
        fileSystem.getUnixMode(file)
        then:
        thrown(FileNotFoundException)
        0 * handler.getUnixMode(file, 0644);
    }

    def "chmod delegates to FilePermissionHandler"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)
        when:
        fileSystem.chmod(file, 0644)
        then:
        1 * handler.chmod(file, 0644);
    }

    def "chmod throws FileNotFoundException if called on non existing file"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        when:
        fileSystem.chmod(file, 0644)
        then:
        thrown(FileNotFoundException)
        0 * handler.chmod(file, 0644);
    }

    @Requires(TestPrecondition.WINDOWS)
    def "windows file system is case insensitive"() {
        setup:
        def fs = FileSystems.default
        expect:
        !fs.caseSensitive
    }

    @Requires(TestPrecondition.WINDOWS)
    def "windows file system cannot create symbolic link"() {
        setup:
        def fs = FileSystems.default
        expect:
        !fs.canCreateSymbolicLink()
    }
}
