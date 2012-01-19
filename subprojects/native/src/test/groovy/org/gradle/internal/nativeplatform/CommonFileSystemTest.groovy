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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder

class CommonFileSystemTest extends Specification {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    def fs = FileSystems.default
    def posix = PosixUtil.current()

    def "unix permissions cannot be read on non existing file"() {
        when:
        fs.getUnixMode(new File(tmpDir.dir, "someFile"))

        then:
        thrown(FileNotFoundException)
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "unix permissions on files can be changed and read"() {
        setup:
        def File f = new File(tmpDir.dir, "someFile")
        Files.touch(f)

        when:
        fs.chmod(f, mode)

        then:
        fs.getUnixMode(f) == mode
        (PosixUtil.current().stat(f.getAbsolutePath()).mode() & 0777) == mode

        where:
        mode << [0644, 0600]
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "unix permissions on directories can be changed and read"() {
        setup:
        def File d = new File(tmpDir.dir, "someDir")
        assert d.mkdir()

        when:
        fs.chmod(d, mode)

        then:
        fs.getUnixMode(d) == mode
        (PosixUtil.current().stat(d.getAbsolutePath()).mode() & 0777) == mode

        where:
        mode << [0755, 0700]
    }
}
