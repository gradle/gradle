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

package org.gradle.testkit.runner.internal

import org.gradle.internal.file.nio.OwnerOnlyFiles
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.FileSystemTestPreconditions
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class UserTemporaryTestKitDirProviderTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    File tmpDir

    def setup() {
        tmpDir = testDirectoryProvider.createDir('tmp')
    }

    def "directory is specific to the user"() {
        expect:
        new UserTemporaryTestKitDirProvider(tmpDir, userName).dir == new File(tmpDir, dirName)

        where:
        userName       | dirName
        'alice'        | '.gradle-test-kit-alice'
        'a.b-c_d1'     | '.gradle-test-kit-a.b-c_d1'
        'DOMAIN\\bob'  | '.gradle-test-kit-DOMAIN_bob'
        '../../etc'    | '.gradle-test-kit-.._.._etc'
        'jörg müller'  | '.gradle-test-kit-j_rg_m_ller'
        ''             | '.gradle-test-kit-unknown'
        null           | '.gradle-test-kit-unknown'
    }

    def "refuses an existing location that cannot be trusted"() {
        given:
        def provider = provider()
        provider.dir.text = ''

        when:
        provider.ensureDirExists()

        then:
        def e = thrown(InvalidRunnerConfigurationException)
        e.message == refusal(provider, "it is not a directory")
        e.cause instanceof OwnerOnlyFiles.UntrustedDirectoryException
    }

    @Requires(FileSystemTestPreconditions.FilePermissions)
    def "refuses an existing directory it cannot write to"() {
        given:
        def provider = provider()
        Files.createDirectory(provider.dir.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString('r-x------')))

        when:
        provider.ensureDirExists()

        then:
        def e = thrown(InvalidRunnerConfigurationException)
        e.message == refusal(provider, "it is not writable")

        cleanup:
        Files.setPosixFilePermissions(provider.dir.toPath(), PosixFilePermissions.fromString('rwx------'))
    }

    @Requires(FileSystemTestPreconditions.FilePermissions)
    def "reports a directory it cannot create"() {
        given:
        def provider = provider()
        Files.setPosixFilePermissions(tmpDir.toPath(), PosixFilePermissions.fromString('r-x------'))

        when:
        provider.ensureDirExists()

        then:
        def e = thrown(InvalidRunnerConfigurationException)
        e.message == "Unable to create ${provider.dir.absolutePath} as the TestKit directory. " +
            "Check the permissions of ${tmpDir.absolutePath}, or set another TestKit directory using GradleRunner.withTestKitDir() or the 'org.gradle.testkit.dir' system property. " +
            documentation()
        e.cause instanceof IOException

        cleanup:
        Files.setPosixFilePermissions(tmpDir.toPath(), PosixFilePermissions.fromString('rwx------'))
    }

    private UserTemporaryTestKitDirProvider provider() {
        new UserTemporaryTestKitDirProvider(tmpDir, 'alice')
    }

    private static String refusal(UserTemporaryTestKitDirProvider provider, String reason) {
        "Unable to use ${provider.dir.absolutePath} as the TestKit directory: ${reason}. " +
            "Delete it, or set another TestKit directory using GradleRunner.withTestKitDir() or the 'org.gradle.testkit.dir' system property. " +
            documentation()
    }

    private static String documentation() {
        "For more information, please refer to https://docs.gradle.org/${GradleVersion.current().version}/userguide/test_kit.html#sec:controlling_the_build_environment in the Gradle documentation."
    }
}
