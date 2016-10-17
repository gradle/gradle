/*
 * Copyright 2014 the original author or authors.
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

package org.gradle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.nativeintegration.jansi.JansiLibraryResolver
import org.gradle.util.Requires
import spock.lang.Issue

import static org.gradle.testfixtures.NativeTestPrecondition.*

class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    final File nativeDir = new File(executer.gradleUserHomeDir, 'native')
    final File jansiDir = new File(nativeDir, 'jansi')
    final JansiLibraryResolver resolver = new JansiLibraryResolver()

    def setup() {
        requireGradleDistribution()
    }

    def "native services libs are unpacked to gradle user home dir"() {
        when:
        quietExecutor().run()

        then:
        nativeDir.directory
    }

    @Requires(adhoc = { isMacOsXOrLinuxOrWindows() })
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        given:
        String libraryPath = resolver.resolve().path
        File library = new File(jansiDir, libraryPath)

        when:
        quietExecutor().run()

        then:
        library.exists()
        long lastModified = library.lastModified()

        when:
        quietExecutor().run()

        then:
        library.exists()
        lastModified == library.lastModified()
    }

    @Requires(adhoc = { isNotMacOsXOrLinuxOrWindows() })
    def "can initialize jansi for OS without supported library"() {
        when:
        quietExecutor().run()

        then:
        noExceptionThrown()
    }

    @Issue("GRADLE-3573")
    @Requires(adhoc = { isMountedNoexec('/tmp') })
    def "creates Jansi library directory even if tmp dir is mounted with noexec option"() {
        when:
        executer.withNoExplicitTmpDir().withBuildJvmOpts("-Djava.io.tmpdir=/tmp").run()

        then:
        jansiDir.directory
    }

    private GradleExecuter quietExecutor() {
        executer.withArguments('-q')
    }
}
