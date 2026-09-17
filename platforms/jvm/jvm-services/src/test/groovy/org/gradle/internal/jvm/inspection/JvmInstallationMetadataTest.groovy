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

package org.gradle.internal.jvm.inspection

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.OsTestPreconditions
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification

/**
 * Tests capability detection in {@link JvmInstallationMetadata}.
 */
class JvmInstallationMetadataTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Issue("https://github.com/gradle/gradle/issues/36118")
    @Requires(OsTestPreconditions.Windows)
    def "detects native-image capability from #script script on Windows"() {
        given:
        def javaHome = jdkHome()
        javaHome.file("bin/$script").touch()

        expect:
        metadataFor(javaHome).capabilities.contains(JavaInstallationCapability.NATIVE_IMAGE)

        where:
        // We test non-lowercase variants, but Windows is typically case-insensitive so these generally pass
        // even if we don't explicitly support them.
        // Testing case-sensitive behavior is not feasible as we would need elevated privileges to set that,
        // or a local FS that is case-sensitive, which is not available in CI.
        script << ["native-image.cmd", "native-image.CMD", "NATIVE-IMAGE.cmd"]
    }

    @Issue("https://github.com/gradle/gradle/issues/36118")
    @Requires(OsTestPreconditions.Windows)
    def "detects native-image capability when both the executable and the .cmd script are present on Windows"() {
        given:
        def javaHome = jdkHome()
        javaHome.file("bin", OperatingSystem.current().getExecutableName("native-image")).touch()
        javaHome.file("bin/native-image.cmd").touch()

        expect:
        metadataFor(javaHome).capabilities.contains(JavaInstallationCapability.NATIVE_IMAGE)
    }

    @Requires(OsTestPreconditions.NotWindows)
    def "native-image.cmd script alone does not grant native-image capability on non-Windows OS"() {
        given:
        def javaHome = jdkHome()
        javaHome.file("bin/native-image.cmd").touch()

        expect:
        !metadataFor(javaHome).capabilities.contains(JavaInstallationCapability.NATIVE_IMAGE)
    }

    def "does not detect native-image capability when no native-image tool is present"() {
        given:
        def javaHome = jdkHome()

        expect:
        !metadataFor(javaHome).capabilities.contains(JavaInstallationCapability.NATIVE_IMAGE)
    }

    private TestFile jdkHome() {
        temporaryFolder.createDir("jdk")
    }

    private static JvmInstallationMetadata metadataFor(TestFile javaHome) {
        JvmInstallationMetadata.from(
            javaHome,
            "17.0.1",
            "BellSoft",
            "OpenJDK Runtime Environment",
            "17.0.1+12",
            "OpenJDK 64-Bit Server VM",
            "17.0.1+12",
            "BellSoft",
            "amd64"
        )
    }
}
