/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.jvm.toolchain.JdkRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.InstalledJdkTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue
import spock.lang.Timeout

/**
 * Reproducer for https://github.com/gradle/gradle/issues/34491.
 *
 * When building via Tooling API with daemon JVM criteria that differ from the TAPI client JVM,
 * and the Gradle user home is empty, the TAPI client process hangs after the build completes.
 * A non-daemon "File lock request listener" thread in {@code DefaultFileLockContentionHandler}
 * remains blocked reading from a DatagramSocket, preventing JVM shutdown.
 *
 * The hang is triggered by the JVM toolchain download/provisioning code path. This test spawns
 * a separate JVM that acts as a TAPI client and triggers a daemon JDK download. If the bug is
 * present, the client JVM hangs after the build, the {@code JavaExec} task never completes,
 * and the test times out.
 */
@Requires(InstalledJdkTestPreconditions.JavaHomeWithDifferentVersionAvailable)
class ToolingApiDaemonJvmCriteriaHangIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/34491")
    @Timeout(120)
    def "TAPI client process exits after build that triggers daemon JDK download with empty user home"() {
        def differentJdk = AvailableJavaHomes.differentVersion
        def jdkRepository = new JdkRepository(differentJdk, "jdk.zip")
        def uri = jdkRepository.start()
        jdkRepository.reset()

        def testProject = file("test-project")
        def emptyUserHome = file("empty-user-home")

        setup:
        testProject.file("settings.gradle") << ""
        testProject.file("build.gradle") << ""

        // Configure daemon JVM criteria with download URLs to trigger provisioning
        def downloadUrl = uri.toString()
        testProject.file("gradle/gradle-daemon-jvm.properties") << """
toolchainVersion=${differentJdk.javaVersion.majorVersion}
toolchainUrl.LINUX.X86_64=${downloadUrl}
toolchainUrl.LINUX.AARCH64=${downloadUrl}
toolchainUrl.MAC_OS.X86_64=${downloadUrl}
toolchainUrl.MAC_OS.AARCH64=${downloadUrl}
toolchainUrl.WINDOWS.X86_64=${downloadUrl}
toolchainUrl.WINDOWS.AARCH64=${downloadUrl}
""".stripIndent().trim()

        // Enable toolchain auto-download, disable auto-detect so only the download path is used
        testProject.file("gradle.properties") << """
org.gradle.java.installations.auto-download=true
org.gradle.java.installations.auto-detect=false
""".stripIndent().trim()

        settingsFile << "rootProject.name = 'client-runner'"
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
                maven { url = '${buildContext.localRepository.toURI()}' }
            }

            tasks.register('runToolingApiClient', JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiClient"
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
            }
        """

        file("src/main/java/ToolingApiClient.java") << """
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.ProjectConnection;

            import java.io.ByteArrayOutputStream;
            import java.io.File;

            public class ToolingApiClient {
                public static void main(String[] args) throws Exception {
                    File projectDir = new File("${TextUtil.escapeString(testProject.absolutePath)}");
                    File userHome = new File("${TextUtil.escapeString(emptyUserHome.absolutePath)}");
                    File installation = new File("${TextUtil.escapeString(buildContext.gradleHomeDir.absolutePath)}");

                    GradleConnector connector = GradleConnector.newConnector()
                        .useInstallation(installation)
                        .useGradleUserHomeDir(userHome)
                        .forProjectDirectory(projectDir);

                    ProjectConnection connection = null;
                    try {
                        connection = connector.connect();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ByteArrayOutputStream err = new ByteArrayOutputStream();
                        connection.newBuild()
                            .forTasks("help")
                            .setStandardOutput(out)
                            .setStandardError(err)
                            .run();
                        System.out.println(out.toString());
                        System.err.println(err.toString());
                    } finally {
                        if (connection != null) {
                            connection.close();
                        }
                        connector.disconnect();
                    }
                    // If the bug is present, the JVM hangs here due to the
                    // non-daemon "File lock request listener" thread
                    System.out.println("TAPI client exiting normally");
                }
            }
        """

        when:
        succeeds("runToolingApiClient")

        then:
        output.contains("TAPI client exiting normally")

        cleanup:
        jdkRepository.stop()
    }
}
