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
import org.junit.Assume
import spock.lang.Unroll

class ToolingApiJdkCompatibilityTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }
            
            repositories {
                ${jcenterRepository()}
                maven { url '${buildContext.localRepository.toURI().toURL()}' }
            }
            
            task runTask(type: JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiCompatibilityClient"
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(Integer.valueOf(project.findProperty("clientJdk")))
                }
                enableAssertions = true

                args = [ "help", file("test-project"), project.findProperty("gradleVersion"), project.findProperty("targetJdk") ]
            }
            
            java {
                disableAutoTargetJvm()
                toolchain {
                    languageVersion = JavaLanguageVersion.of(8)
                }
                targetCompatibility = project.findProperty("compilerJdk")
            }
            
            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
            }
        """
        file("src/main/java/ToolingApiCompatibilityClient.java") << """
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class ToolingApiCompatibilityClient {
    public static void main(String[] args) throws Exception {
        try {
            // parameters
            // 1. action
            // 2. project directory
            // 3. target gradle version (or home)
            // 4. target JDK
            if (args.length == 4) {
                String action = args[0];
                File projectDir = new File(args[1]);
                String gradleVersion = args[2];
                File javaHome = new File(args[3]);
                System.out.println("action = " + action);
                System.out.println("projectDir = " + projectDir);
                System.out.println("gradleVersion = " + gradleVersion);
                System.out.println("javaHome = " + javaHome);
                if (action.equals("help")) {
                    runHelp(projectDir, gradleVersion, javaHome);
                    System.exit(0);
                }
            }
        } finally {
            System.err.println("something went wrong");
            System.exit(1);
        }
    }

    private static void runHelp(File projectLocation, String gradleVersion, File javaHome) throws Exception {
        GradleConnector connector = GradleConnector.newConnector();
        connector.useGradleVersion(gradleVersion);

        ProjectConnection connection = connector.forProjectDirectory(projectLocation).connect();

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
    }
}
"""
        settingsFile << "rootProject.name = 'client-runner'"

        file("test-project/build.gradle") << "println 'Hello from ' + gradle.gradleVersion"
        file("test-project/settings.gradle") << "rootProject.name = 'target-project'"
    }

    @Unroll
    def "tapi client with classes compiled for Java #compilerJdkVersion.majorVersion can launch task with Gradle #gradleVersion on Java #gradleDaemonJdkVersion.majorVersion from Java #clientJdkVersion.majorVersion"(JavaVersion compilerJdkVersion, JavaVersion clientJdkVersion, JavaVersion gradleDaemonJdkVersion, String gradleVersion) {
        setup:
        def tapiClientCompilerJdk = AvailableJavaHomes.getJdk(compilerJdkVersion)
        def gradleDaemonJdk = AvailableJavaHomes.getJdk(gradleDaemonJdkVersion)
        Assume.assumeTrue(tapiClientCompilerJdk && gradleDaemonJdk)

        when:
        succeeds("runTask",
                "-PclientJdk=" + clientJdkVersion.majorVersion,
                "-PtargetJdk=" + gradleDaemonJdk.javaHome.absolutePath,
                "-PcompilerJdk=" + compilerJdkVersion.name(),
                "-PgradleVersion=" + gradleVersion)

        then:
        output.contains("BUILD SUCCESSFUL")

        where:
        compilerJdkVersion      | clientJdkVersion      | gradleDaemonJdkVersion  | gradleVersion
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1" // last Gradle version that can run on Java 1.6
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_6 | "2.14.1"

        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3" // last Gradle version that can run on Java 1.7
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_7 | "4.10.3"

        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"    // minimum supported version for Tooling API
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"    // last version with reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"    // first version that had no reported regression
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.0"
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_6 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "6.0"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.0"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_7 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "6.0"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "2.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.6"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "4.7"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.0"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "5.6.4"
        JavaVersion.VERSION_1_8 | JavaVersion.current() | JavaVersion.VERSION_1_8 | "6.0"
    }
}
