/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.time.CountdownTimer
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.util.GradleVersion
import org.junit.Assume
import spock.lang.Ignore
import spock.lang.Issue

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.LOG_LEVEL_TEST_SCRIPT
import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.runLogScript
import static org.gradle.integtests.tooling.fixture.ToolingApiTestCommon.validateLogs

class ToolingApiIntegrationTest extends AbstractIntegrationSpec {

    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    final GradleDistribution otherVersion = new ReleasedVersionDistributions().mostRecentRelease

    TestFile projectDir

    def setup() {
        projectDir = temporaryFolder.testDirectory
        // When adding support for a new JDK version, the previous release might not work with it yet.
        Assume.assumeTrue(otherVersion.worksWith(Jvm.current()))

        settingsFile.touch()
    }

    void setupLoggingTest() {
        propertiesFile << "org.gradle.logging.level=quiet"
        buildFile << LOG_LEVEL_TEST_SCRIPT
    }


    def "tooling api uses to the current version of gradle when none has been specified"() {
        buildFile << "assert gradle.gradleVersion == '${GradleVersion.current().version}'"

        when:
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "tooling api output reports 'CONFIGURE SUCCESSFUL' for model requests"() {
        buildFile << "assert gradle.gradleVersion == '${GradleVersion.current().version}'"

        when:
        def stdOut = new ByteArrayOutputStream()
        toolingApi.withConnection { ProjectConnection connection ->
            connection.model(GradleProject.class).setStandardOutput(stdOut).get()
        }

        then:
        stdOut.toString().contains("CONFIGURE SUCCESSFUL")
        !stdOut.toString().contains("BUILD SUCCESSFUL")
    }

    def "can configure Kotlin DSL project with gradleApi() dependency via tooling API"() {
        given:
        buildKotlinFile << """
        plugins {
            java
        }

        dependencies {
            implementation(gradleApi())
        }
        """

        when:
        def stdOut = new ByteArrayOutputStream()
        toolingApi.withConnection { ProjectConnection connection ->
            connection.action(new KotlinIdeaModelBuildAction()).setStandardOutput(stdOut).run()
        }

        then:
        stdOut.toString().contains("CONFIGURE SUCCESSFUL")
    }

    def "tooling api uses log level set in arguments over gradle.properties"() {
        given:
        setupLoggingTest()

        when:
        def stdOut = runLogScript(toolingApi, arguments)
        then:
        validateLogs(stdOut, expectedLevel)

        where:
        expectedLevel  | arguments
        LogLevel.QUIET | []
        LogLevel.INFO  | ["--info"]
        LogLevel.INFO  | ["-Dorg.gradle.logging.level=info"]
    }

    @Ignore("Needs a fix for parallel artifact transform")
    def "tooling api uses the wrapper properties to determine which version to use"() {
        buildFile << """
        wrapper {
            distributionUrl = '${otherVersion.binDistribution.toURI()}'
        }
        task check {
            doLast {
                assert gradle.gradleVersion == '${otherVersion.version.version}'
            }
        }"""
        otherVersion.binDistribution.makeReadable()
        executer.withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useBuildDistribution()
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    @Ignore("Needs a fix for parallel artifact transform")
    def "tooling api searches up from the project directory to find the wrapper properties"() {
        settingsFile << "include 'child'"
        buildFile << """
        wrapper { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
        allprojects {
            task check { doLast { assert gradle.gradleVersion == '${otherVersion.version.version}' } }
        }
        """
        projectDir.file('child').createDir()
        otherVersion.binDistribution.makeReadable()
        executer.withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useBuildDistribution()
            connector.searchUpwards(true)
            connector.forProjectDirectory(projectDir.file('child'))
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "can specify a gradle installation to use"() {

        buildFile << "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useInstallation(otherVersion.gradleHomeDir)
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "can specify a gradle distribution to use"() {
        buildFile << "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useDistribution(otherVersion.binDistribution.toURI())
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "can specify a gradle version to use"() {
        buildFile << "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector {
            it.useGradleVersion(otherVersion.version.version)
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    @Issue("GRADLE-2419")
    def "tooling API does not hold JVM open"() {
        given:
        def startTimeoutMs = 90000
        def stateChangeTimeoutMs = 15000
        def stopTimeoutMs = 10000
        def retryIntervalMs = 500

        def gradleUserHomeDirPath = executer.gradleUserHomeDir.absolutePath
        def gradleHomeDirPath = otherVersion.gradleHomeDir.absolutePath

        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'

            repositories {
                maven { url "${buildContext.localRepository.toURI()}" }
                ${RepoScriptBlockUtil.gradleRepositoryDefinition()}
            }

            dependencies {
                implementation "org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}"
                runtimeOnly 'org.slf4j:slf4j-simple:1.7.10'
            }

            application.mainClass = 'Main'

            run {
                args = ["${TextUtil.escapeString(gradleHomeDirPath)}", "${TextUtil.escapeString(gradleUserHomeDirPath)}"]
                systemProperty 'org.gradle.daemon.idletimeout', 10000
                systemProperty 'org.gradle.daemon.registry.base', "${TextUtil.escapeString(projectDir.file("daemon").absolutePath)}"
            }

            task thing {
                doLast {
                    def startMarkerFile = file("start.marker")
                    startMarkerFile << new Date().toString()
                    println "start marker written (\$startMarkerFile)"

                    def stopMarkerFile = file("stop.marker")
                    def startedAt = System.currentTimeMillis()
                    println "waiting for stop marker (\$stopMarkerFile)"
                    while(!stopMarkerFile.exists()) {
                        if (System.currentTimeMillis() - startedAt > $stateChangeTimeoutMs) {
                            throw new Exception("Timeout ($stateChangeTimeoutMs ms) waiting for stop marker")
                        }
                        sleep $retryIntervalMs
                    }
                }
            }
        """

        projectDir.file("src/main/java/Main.java") << """
            import org.gradle.tooling.BuildLauncher;
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
            import org.gradle.tooling.ProjectConnection;

            import java.io.ByteArrayOutputStream;
            import java.io.File;
            import java.lang.System;

            public class Main {
                public static void main(String[] args) {
                    // Configure the connector and create the connection
                    GradleConnector connector = GradleConnector.newConnector();

                    if (args.length > 0) {
                        connector.useInstallation(new File(args[0]));
                        if (args.length > 1) {
                            connector.useGradleUserHomeDir(new File(args[1]));
                        }
                    }

                    connector.forProjectDirectory(new File("."));
                    if (args.length > 0) {
                        connector.useInstallation(new File(args[0]));
                    }

                    // required because invoked build script doesn't provide a settings file
                    ((DefaultGradleConnector) connector).searchUpwards(false);

                    ProjectConnection connection = connector.connect();
                    try {
                        System.out.println("About to configure a new build");
                        // Configure the build
                        BuildLauncher launcher = connection.newBuild();
                        launcher.forTasks("thing");
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        launcher.setStandardOutput(outputStream);
                        launcher.setStandardError(outputStream);
                        launcher.setColorOutput(${withColor});
                        System.out.println("About to run the build with color=" + ${withColor});
                        // Run the build
                        launcher.run();
                        System.out.println("Build was successful");
                    } finally {
                        // Clean up
                        System.out.println("Cleaning up after the build");
                        connection.close();
                        System.out.println("Connection is closed.");
                    }
                }
            }
        """

        when:
        GradleHandle handle = executer.inDirectory(projectDir)
            .withTasks('run')
            .start()

        then:
        // Wait for the tooling API to start the build
        def startMarkerFile = projectDir.file("start.marker")
        def foundStartMarker = startMarkerFile.exists()

        CountdownTimer startTimer = Time.startCountdownTimer(startTimeoutMs)
        while (handle.running && !foundStartMarker) {
            if (startTimer.hasExpired()) {
                throw new Exception("timeout waiting for start marker")
            } else {
                sleep retryIntervalMs
            }
            foundStartMarker = startMarkerFile.exists()
        }

        if (!foundStartMarker) {
            throw new Exception("Build finished without start marker appearing")
        }

        // Signal the build to finish
        def stopMarkerFile = projectDir.file("stop.marker")
        def stopTimer = Time.startCountdownTimer(stopTimeoutMs)
        stopMarkerFile << new Date().toString()

        // Does the tooling API hold the JVM open (which will also hold the build open)?
        while (handle.running) {
            if (stopTimer.hasExpired()) {
                // This test can fail if we have started a thread pool in Gradle and have not shut it down
                // properly. If you run this locally, you can connect to the JVM running Main above and
                // get a thread dump after you see "Connection is closed".
                throw new Exception("timeout after placing stop marker (JVM might have been held open)")
            }
            sleep retryIntervalMs
        }

        handle.waitForFinish()

        // https://github.com/gradle/gradle-private/issues/3005
        println "Waiting for daemon exit, start: ${ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)}"

        Thread.sleep(stopTimeoutMs)

        println "Waiting for daemon exit, end: ${ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)}"

        where:
        withColor << [true, false]
    }
}
