/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon.registry

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

@Requires(UnitTestPreconditions.NotWindows)
class DaemonDirTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "creates the versioned directory accessible only to the owner"() {
        when:
        def daemonDir = new DaemonDir(tmp.file("daemon"))

        then:
        permissions(daemonDir.versionedDir) == "rwx------"
    }

    def "restricts an existing versioned directory to the owner"() {
        given:
        def versionedDir = new DaemonDir(tmp.file("daemon")).versionedDir
        Files.setPosixFilePermissions(versionedDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))

        when:
        new DaemonDir(tmp.file("daemon"))

        then:
        permissions(versionedDir) == "rwx------"
    }

    private static String permissions(File file) {
        PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))
    }
}
