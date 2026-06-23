/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.file.temp

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

class TempFilesTest extends Specification {

    @TempDir
    File tempDir;

    def "can generate temp files for short prefixes"() {
        when:
        def file = TempFiles.createTempFile("ok", "", tempDir)

        then:
        file.exists()
        file.canRead()
        file.canWrite()
    }

    def "can generate temp files for no prefix"() {
        when:
        def file = TempFiles.createTempFile(null, null, tempDir)

        then:
        file.exists()
        file.canRead()
        file.canWrite()
    }

    def "creates temp files with owner-only permissions on POSIX systems"() {
        when:
        def file = TempFiles.createTempFile("test-", ".tmp", tempDir)

        then:
        file.exists()
        file.canRead()
        file.canWrite()

        and:
        if (Files.getFileStore(tempDir.toPath()).supportsFileAttributeView("posix")) {
            def perms = Files.getPosixFilePermissions(file.toPath())
            assert perms == [PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE] as Set
        }
    }
}
