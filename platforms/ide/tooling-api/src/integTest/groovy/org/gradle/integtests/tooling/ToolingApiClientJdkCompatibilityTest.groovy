/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION

abstract class ToolingApiClientJdkCompatibilityTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    private final TestFile testProject = file("test-project")

    def setup() {
        executer.beforeExecute {
            withToolchainDetectionEnabled()
        }
    }

    def createProject(Jvm daemonJvm, String targetGradleVersion, String action) {
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            repositories {
                maven { url = '${buildContext.localRepository.toURI()}' }
            }

            task run(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiCompatibilityClient"
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(${clientJdkVersion.majorVersion})
                }
                enableAssertions = true

                if (${clientJdkVersion.isCompatibleWith(JavaVersion.VERSION_16)} && ['4.0'].contains("${targetGradleVersion}")) {
                    jvmArgs = ["--add-opens", "java.base/java.lang=ALL-UNNAMED"]
                }
            }

            java {
                disableAutoTargetJvm()
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
            }

            // Even though we're using a JDK8 toolchain, we compile down to Java 6 bytecode.
            // This makes it easier to run these tests locally since most developers have Java 8
            // installed. We still try to run the Gradle build with Java 6/7, but we skip those tests
            // when Java 6/7 are not installed.
            tasks.withType(JavaCompile).configureEach {
                options.compilerArgs.addAll('-target', '6', '-source', 6)
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
            }
        """

        testProject.file("settings.gradle") << "rootProject.name = 'target-project'"

        file("src/main/java/ToolingApiCompatibilityClient.java").java("""
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.ProjectConnection;

            import java.io.ByteArrayOutputStream;
            import java.io.File;

            public class ToolingApiCompatibilityClient {
                public static void main(String[] args) {
                    try {
                        doRun();
                        System.exit(0);
                    } catch (org.gradle.tooling.GradleConnectionException e) {
                        // Earlier versions of Gradle can sometimes fail to connect to the just started Gradle daemon.
                        // The failure will be the "tried to connect to 100 daemons" error
                        // If we're testing against a version that has this problem, we can ignore it.
                        boolean allowUnusable = ${GradleVersion.version(targetGradleVersion) < GradleVersion.version("6.0")};
                        if (allowUnusable && e.getCause() != null && e.getCause().getClass().getSimpleName().equals("NoUsableDaemonFoundException")) {
                            System.out.println("Daemon registry is in a bad state and we cannot connect to the daemon.");
                            System.exit(0);
                        } else {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }

                private static void doRun() throws Exception {
                    GradleConnector connector = GradleConnector.newConnector();
                    connector.useGradleVersion("${targetGradleVersion}");

                    ProjectConnection connection = null;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteArrayOutputStream err = new ByteArrayOutputStream();
                    try {
                        connection = connector
                            .forProjectDirectory(new File("${TextUtil.escapeString(testProject.absolutePath)}"))
                            .useGradleUserHomeDir(new File("${TextUtil.escapeString(executer.gradleUserHomeDir.absolutePath)}"))
                            .connect();

                        File javaHome = new File("${TextUtil.escapeString(daemonJvm.javaHome.absolutePath)}");
                        ${action}
                    } finally {
                        System.out.println(out.toString());
                        System.err.println(err.toString());
                        if (connection != null) {
                            connection.close();
                        }
                        connector.disconnect();
                    }
                }
            }
        """)
    }

    abstract JavaVersion getClientJdkVersion()

    def "tapi client can launch task with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        given:
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(gradleDaemonJdk != null)

        testProject.file("build.gradle") << "println 'Hello from ' + gradle.gradleVersion"
        createProject(gradleDaemonJdk, gradleVersion, """
            connection.newBuild()
                .forTasks("help")
                .setStandardOutput(out)
                .setStandardError(err)
                .setJavaHome(javaHome)
                .run();

            assert out.toString().contains("Hello from " + "${gradleVersion}");
        """)

        when:
        withInstallations(AvailableJavaHomes.getAvailableJvms())
        succeeds("run")

        then:
        output.contains("BUILD SUCCESSFUL")

        where:
        gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_7 | MINIMUM_SUPPORTED_GRADLE_VERSION.version
        JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7

        JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_8 | "4.10.3"
        JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_8 | "6.9.2"
        JavaVersion.VERSION_1_8 | "7.6.4"
        JavaVersion.VERSION_17  | "8.14.4"
    }

    def "tapi client can run build action with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        given:
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(gradleDaemonJdk != null)
        withCompatibilityBuildAction()
        createProject(gradleDaemonJdk, gradleVersion, runCompatibilityBuildAction())

        when:
        withInstallations(AvailableJavaHomes.getAvailableJvms())
        succeeds("run")

        then:
        output.contains("BUILD SUCCESSFUL")

        where:
        gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_8 | "4.10.3"
        JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_8 | "6.9.2"
        JavaVersion.VERSION_1_8 | "7.6.4"
        JavaVersion.VERSION_17  | "8.14.4"
    }

    def "tapi client cannot run build action with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        given:
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(gradleDaemonJdk != null)
        withCompatibilityBuildAction()
        createProject(gradleDaemonJdk, gradleVersion, runCompatibilityBuildAction())

        when:
        executer.withStackTraceChecksDisabled()
        executer.ignoreCleanupAssertions()
        withInstallations(AvailableJavaHomes.getAvailableJvms())

        then:
        fails("run")

        where:
        gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_7 | MINIMUM_SUPPORTED_GRADLE_VERSION.version
        JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7
    }

    void withCompatibilityBuildAction() {
        file("src/main/java/ToolingApiCompatibilityBuildAction.java").java("""
            import org.gradle.tooling.BuildAction;
            import org.gradle.tooling.BuildController;

            public class ToolingApiCompatibilityBuildAction implements BuildAction<String> {

                @Override
                public String execute(BuildController controller) {
                    return "Build action result: " + controller.toString();
                }
            }
        """)
    }

    String runCompatibilityBuildAction() {
        """
            String result = connection.action(new ToolingApiCompatibilityBuildAction())
                .setStandardOutput(out)
                .setStandardError(err)
                .setJavaHome(javaHome)
                .run();
            assert result.contains("Build action result");
        """
    }
}
