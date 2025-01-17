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
import org.gradle.test.fixtures.Flaky
import org.gradle.util.GradleVersion
import org.junit.Assume

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION

abstract class ToolingApiClientJdkCompatibilityTest extends AbstractIntegrationSpec {

    def setup() {
        System.out.println("TAPI client is using Java " + clientJdkVersion)

        executer.beforeExecute {
            withToolchainDetectionEnabled()
        }
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
                maven { url = '${buildContext.localRepository.toURI()}' }
            }

            def requestedGradleVersion = project.findProperty("gradleVersion")
            def requestedTargetJdk = project.findProperty("targetJdk")

            // Earlier versions of Gradle can sometimes fail to connect to the just started Gradle daemon.
            // The failure will be the "tried to connect to 100 daemons" error
            // If we're testing against a version that has this problem, we can ignore it.
            def ignoreFlakyDaemonConnections = Boolean.toString(GradleVersion.version(requestedGradleVersion) < GradleVersion.version("6.0"))

            task runTask(type: JavaExec) {
                args = [ "help", file("test-project"), requestedGradleVersion, requestedTargetJdk, gradle.gradleUserHomeDir, ignoreFlakyDaemonConnections ]
            }

            task buildAction(type: JavaExec) {
                args = [ "action", file("test-project"), requestedGradleVersion, requestedTargetJdk, gradle.gradleUserHomeDir, ignoreFlakyDaemonConnections ]
            }

            configure([runTask, buildAction]) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiCompatibilityClient"
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(Integer.parseInt(project.findProperty("clientJdk")))
                }
                enableAssertions = true

                if (${clientJdkVersion.isCompatibleWith(JavaVersion.VERSION_16)} && ['2.14.1'].contains(project.findProperty("gradleVersion"))) {
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
        settingsFile << "rootProject.name = 'client-runner'"

        file("test-project/build.gradle") << "println 'Hello from ' + gradle.gradleVersion"
        file("test-project/settings.gradle") << "rootProject.name = 'target-project'"
        file("src/main/java/ToolingApiCompatibilityClient.java").java("""
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.ProjectConnection;

            import java.io.ByteArrayOutputStream;
            import java.io.File;

            public class ToolingApiCompatibilityClient {
                public static void main(String[] args) {
                    // parameters
                    // 1. action
                    // 2. project directory
                    // 3. target gradle version (or home)
                    // 4. target JDK
                    // 5. gradle user home
                    // 6. whether or not we're allowed to ignore "NoUsableDaemonFoundException" exceptions
                    String action = args[0];
                    File projectDir = new File(args[1]);
                    String gradleVersion = args[2];
                    File javaHome = new File(args[3]);
                    File gradleUserHome = new File(args[4]);
                    boolean allowUnusable = Boolean.parseBoolean(args[5]);
                    System.out.println("action = " + action);
                    System.out.println("projectDir = " + projectDir);
                    System.out.println("gradleVersion = " + gradleVersion);
                    System.out.println("javaHome = " + javaHome);
                    System.out.println("gradleUserHome = " + gradleUserHome);
                    System.out.println("allow unusable daemons = " + allowUnusable);
                    try {
                        if (action.equals("help")) {
                            runHelp(projectDir, gradleVersion, javaHome, gradleUserHome);
                        } else if (action.equals("action")) {
                            buildAction(projectDir, gradleVersion, javaHome, gradleUserHome);
                        }
                        System.exit(0);
                    } catch (org.gradle.tooling.GradleConnectionException e) {
                        if (allowUnusable && e.getCause()!=null && e.getCause().getClass().getSimpleName().equals("NoUsableDaemonFoundException")) {
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

                private static void runHelp(File projectLocation, String gradleVersion, File javaHome, File gradleUserHome) throws Exception {
                    GradleConnector connector = GradleConnector.newConnector();
                    connector.useGradleVersion(gradleVersion);

                    ProjectConnection connection = null;

                    try {
                        connection = connector.forProjectDirectory(projectLocation).useGradleUserHomeDir(gradleUserHome).connect();

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ByteArrayOutputStream err = new ByteArrayOutputStream();

                        connection.newBuild()
                            .forTasks("help")
                            .setStandardOutput(out)
                            .setStandardError(err)
                            .setJavaHome(javaHome)
                            .run();

                        assert out.toString().contains("Hello from");
                        System.err.println(err.toString());
                    } finally {
                        if (connection != null) {
                            connection.close();
                        }
                        connector.disconnect();
                    }
                }

               private static void buildAction(File projectLocation, String gradleVersion, File javaHome, File gradleUserHome) throws Exception {
                    GradleConnector connector = GradleConnector.newConnector();
                    connector.useGradleVersion(gradleVersion);

                    ProjectConnection connection = null;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteArrayOutputStream err = new ByteArrayOutputStream();
                    try {
                        connection = connector.forProjectDirectory(projectLocation).useGradleUserHomeDir(gradleUserHome).connect();
                        String result = connection.action(new ToolingApiCompatibilityBuildAction())
                            .setStandardOutput(out)
                            .setStandardError(err)
                            .setJavaHome(javaHome)
                            .run();
                        assert result.contains("Build action result");
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

    abstract JavaVersion getClientJdkVersion()

    @Flaky
    def "tapi client can launch task with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(gradleDaemonJdk != null)
        sortOutNotSupportedNotWorkingCombinations(gradleVersion)

        when:
        succeeds("runTask",
            "-PclientJdk=" + clientJdkVersion.majorVersion,
            "-PtargetJdk=" + gradleDaemonJdk.javaHome.absolutePath,
            "-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}",
            "-PgradleVersion=" + gradleVersion)

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
    }

    private sortOutNotSupportedNotWorkingCombinations(String gradleVersion) {
        Assume.assumeFalse(clientJdkVersion.majorVersion.toInteger() >= 16 && GradleVersion.version(gradleVersion) <= GradleVersion.version("4.0"))
    }

    @Flaky
    def "tapi client can run build action with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(gradleDaemonJdk != null)
        sortOutNotSupportedNotWorkingCombinations(gradleVersion)

        when:
        succeeds("buildAction",
            "-PclientJdk=" + clientJdkVersion.majorVersion,
            "-PtargetJdk=" + gradleDaemonJdk.javaHome.absolutePath,
            "-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}",
            "-PgradleVersion=" + gradleVersion)

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
    }
}
