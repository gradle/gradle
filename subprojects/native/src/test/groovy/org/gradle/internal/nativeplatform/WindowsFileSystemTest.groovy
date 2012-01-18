/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.io.Files;
import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.WINDOWS)
class WindowsFileSystemTest extends Specification {
    def fs = FileSystems.default

    def "is case insensitive"() {
        expect:
        !fs.caseSensitive
    }

    def "cannot create symbolic link"() {
        expect:
        !fs.canCreateSymbolicLink()
    }

    def "default values are used to emulate unix permissions"() {
        setup:
        def File dir = Files.createTempDir();
        def File file = new File(dir, "f")
        Files.touch(file)

        expect:
        fs.getUnixMode(file) == FileSystem.DEFAULT_FILE_MODE
        fs.getUnixMode(dir) == FileSystem.DEFAULT_DIR_MODE

        cleanup:
        assert file.delete()
        assert dir.delete()
    }

    def "unix permissions cannot be changed"() {
        setup:
        def File f = Files.createTempDir();

        when:
        fs.chmod(f, 0123)

        then:
        fs.getUnixMode(f) == FileSystem.DEFAULT_DIR_MODE
    }
}
