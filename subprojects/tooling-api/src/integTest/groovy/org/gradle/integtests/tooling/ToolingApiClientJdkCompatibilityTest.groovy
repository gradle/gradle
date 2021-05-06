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
import org.gradle.util.internal.TextUtil
import org.junit.Assume
import spock.lang.Unroll

abstract class ToolingApiClientJdkCompatibilityTest extends AbstractIntegrationSpec {
    def setup() {
        System.out.println("TAPI client is using Java " + clientJdkVersion)

        def jvmArgs = """
            if (${clientJdkVersion.isCompatibleWith(JavaVersion.VERSION_16)} && ['2.6', '2.14.1'].contains(project.findProperty("gradleVersion"))) {
                jvmArgs = ["--add-opens", "java.base/java.lang=ALL-UNNAMED"]
            }
        """

        def compilerJdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_6)
        String compilerJavaHomePath = TextUtil.normaliseFileSeparators(compilerJdk.javaHome.absolutePath)
        executer.beforeExecute {
            withToolchainDetectionEnabled()
        }
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
                maven { url '${buildContext.localRepository.toURI().toURL()}' }
            }

            task runTask(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiCompatibilityClient"
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(Integer.valueOf(project.findProperty("clientJdk")))
                }
                enableAssertions = true
                $jvmArgs

                args = [ "help", file("test-project"), project.findProperty("gradleVersion"), project.findProperty("targetJdk"), gradle.gradleUserHomeDir ]
            }

            task buildAction(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiCompatibilityClient"
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(Integer.valueOf(project.findProperty("clientJdk")))
                }
                enableAssertions = true
                $jvmArgs

                args = [ "action", file("test-project"), project.findProperty("gradleVersion"), project.findProperty("targetJdk"), gradle.gradleUserHomeDir ]
            }

            java {
                disableAutoTargetJvm()
                toolchain {
                    languageVersion = JavaLanguageVersion.of(6)
                }
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
            }
        """
        settingsFile << "rootProject.name = 'client-runner'"
        file('gradle.properties') << "org.gradle.java.installations.paths=${compilerJavaHomePath}"
        file("test-project/build.gradle") << "println 'Hello from ' + gradle.gradleVersion"
        file("test-project/settings.gradle") << "rootProject.name = 'target-project'"
        file("src/main/java/ToolingApiCompatibilityClient.java") << """
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.ProjectConnection;

            import java.io.ByteArrayOutputStream;
            import java.io.File;

            public class ToolingApiCompatibilityClient {
                public static void main(String[] args) throws Exception {
                    // parameters
                    // 1. action
                    // 2. project directory
                    // 3. target gradle version (or home)
                    // 4. target JDK
                    // 5. gradle user home
                    if (args.length == 5) {
                        String action = args[0];
                        File projectDir = new File(args[1]);
                        String gradleVersion = args[2];
                        File javaHome = new File(args[3]);
                        File gradleUserHome = new File(args[4]);
                        System.out.println("action = " + action);
                        System.out.println("projectDir = " + projectDir);
                        System.out.println("gradleVersion = " + gradleVersion);
                        System.out.println("javaHome = " + javaHome);
                        System.out.println("gradleUserHome = " + gradleUserHome);
                        if (action.equals("help")) {
                            int result = runHelp(projectDir, gradleVersion, javaHome, gradleUserHome);
                            System.exit(result);
                        } else if (action.equals("action")) {
                            int result = buildAction(projectDir, gradleVersion, javaHome, gradleUserHome);
                            System.exit(result);
                        }
                    }
                }

                private static int runHelp(File projectLocation, String gradleVersion, File javaHome, File gradleUserHome) {
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
                        return 0;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return 1;
                    } finally {
                        if (connection != null) {
                            connection.close();
                        }
                    }
                }

               private static int buildAction(File projectLocation, String gradleVersion, File javaHome, File gradleUserHome) {
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
                        return 0;
                    } catch(Exception e) {
                        e.printStackTrace();
                        return 1;
                    } finally {
                        System.out.println(out.toString());
                        System.err.println(err.toString());
                        if (connection != null) {
                            connection.close();
                        }
                    }
               }
            }
        """
        file("src/main/java/ToolingApiCompatibilityBuildAction.java") << """
            import org.gradle.tooling.BuildAction;
            import org.gradle.tooling.BuildController;

            public class ToolingApiCompatibilityBuildAction implements BuildAction<String> {

                @Override
                public String execute(BuildController controller) {
                    return "Build action result: " + controller.toString();
                }
            }
        """
    }

    abstract JavaVersion getClientJdkVersion()

    def "tapi client can launch task with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def tapiClientCompilerJdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_6)
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(tapiClientCompilerJdk && gradleDaemonJdk)

        when:
        succeeds("runTask",
                "-PclientJdk=" + clientJdkVersion.majorVersion,
                "-PtargetJdk=" + gradleDaemonJdk.javaHome.absolutePath,
                "-PgradleVersion=" + gradleVersion)

        then:
        output.contains("BUILD SUCCESSFUL")

        where:
        gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_6 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | "2.14.1" // last Gradle version that can run on Java 1.6

        JavaVersion.VERSION_1_7 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7

        JavaVersion.VERSION_1_8 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_8 | "4.10.3"
        JavaVersion.VERSION_1_8 | "5.0"
        JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_8 | "6.0"
    }

    @Unroll
    def "tapi client can run build action with Gradle and Java combination"(JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def tapiClientCompilerJdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_6)
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(tapiClientCompilerJdk && gradleDaemonJdk)

        if (gradleDaemonJdkVersion == JavaVersion.VERSION_1_6 && gradleVersion == "2.14.1") {
            executer.expectDeprecationWarning("Support for running Gradle using Java 6 has been deprecated and will be removed in Gradle 3.0")
        }

        when:
        succeeds("buildAction",
                "-PclientJdk=" + clientJdkVersion.majorVersion,
                "-PtargetJdk=" + gradleDaemonJdk.javaHome.absolutePath,
                "-PgradleVersion=" + gradleVersion)

        then:
        output.contains("BUILD SUCCESSFUL")

        where:
        gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_6 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | "2.14.1" // last Gradle version that can run on Java 1.6

        JavaVersion.VERSION_1_7 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7

        JavaVersion.VERSION_1_8 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_8 | "4.10.3"
        JavaVersion.VERSION_1_8 | "5.0"
        JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_8 | "6.0"
    }
}
