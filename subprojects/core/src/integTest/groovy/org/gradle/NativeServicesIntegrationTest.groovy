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
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.nativeintegration.jansi.JansiStorageLocator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule
import spock.lang.Issue

import static org.gradle.internal.nativeintegration.services.NativeServices.NATIVE_SERVICES_OPTION

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = "needs to run a distribution from scratch to not have native services on the classpath already")
class NativeServicesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def nativeDir = new File(executer.gradleUserHomeDir, 'native')
    def library

    def setup() {
        def jansiLibraryLocator = new JansiStorageLocator()
        def jansiStorage = jansiLibraryLocator.locate(nativeDir)
        library = jansiStorage.targetLibFile
    }

    def "native services libs are unpacked to gradle user home dir"() {
        given:
        executer.withArguments('-q')

        when:
        succeeds("help")

        then:
        nativeDir.directory
    }

    def "native services are #description with systemProperties == #systemProperties"() {
        given:
        executer.requireOwnGradleUserHomeDir().withNoExplicitNativeServicesDir()
        nativeDir = new File(executer.gradleUserHomeDir, 'native')
        executer.withArguments(systemProperties.collect { it.toString() })

        when:
        succeeds("help")

        then:
        nativeDir.exists() == initialized

        where:
        description       | systemProperties                    | initialized
        "initialized"     | ["-D$NATIVE_SERVICES_OPTION=true"]  | true
        "not initialized" | ["-D$NATIVE_SERVICES_OPTION=false"] | false
        "initialized"     | ["-D$NATIVE_SERVICES_OPTION=''"]    | true
        "initialized"     | []                                  | true
    }

    def "daemon with different native services flag is not reused"() {
        given:
        executer.requireDaemon()
        executer.requireIsolatedDaemons()

        when:
        executer.withArguments("-D$NATIVE_SERVICES_OPTION=$firstRunNativeServicesOption")
        succeeds()

        then:
        daemons.daemon.becomesIdle()

        when:
        executer.withArguments("-D$NATIVE_SERVICES_OPTION=$secondRunNativeServicesOption")
        succeeds()

        then:
        daemons.daemons.size() == expectedDaemonCount

        where:
        firstRunNativeServicesOption | secondRunNativeServicesOption | expectedDaemonCount | reuseDescription
        true                         | true                          | 1                   | "reused"
        false                        | false                         | 1                   | "reused"
        true                         | false                         | 2                   | "not reused"
        false                        | true                          | 2                   | "not reused"
    }

    @Issue("GRADLE-3573")
    def "jansi library is unpacked to gradle user home dir and isn't overwritten if existing"() {
        String tmpDirJvmOpt = "-Djava.io.tmpdir=$tmpDir.testDirectory.absolutePath"
        executer.withBuildJvmOpts(tmpDirJvmOpt)

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        long lastModified = library.lastModified()

        when:
        succeeds("help")

        then:
        library.exists()
        assertNoFilesInTmp()
        lastModified == library.lastModified()
    }

    private void assertNoFilesInTmp() {
        assert tmpDir.testDirectory.listFiles().length == 0
    }

    private DaemonLogsAnalyzer getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
