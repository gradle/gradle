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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.GradleProject
import org.gradle.util.GradleVersion
import spock.lang.Issue

class ToolingApiIntegrationTest extends AbstractIntegrationSpec {

    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    final GradleDistribution otherVersion = new ReleasedVersions(temporaryFolder).last
    final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()

    TestFile projectDir

    def setup() {
        projectDir = temporaryFolder.testDirectory
    }

    def "ensure the previous version supports short-lived daemons"() {
        expect:
        otherVersion.daemonIdleTimeoutConfigurable
    }

    def "tooling api uses to the current version of gradle when none has been specified"() {
        projectDir.file('build.gradle') << "assert gradle.gradleVersion == '${GradleVersion.current().version}'"

        when:
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "tooling api uses the wrapper properties to determine which version to use"() {
        toolingApi.isEmbedded = false

        projectDir.file('build.gradle').text = """
task wrapper(type: Wrapper) { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
task check << { assert gradle.gradleVersion == '${otherVersion.version.version}' }
"""
        executer.withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useDefaultDistribution()
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "tooling api searches up from the project directory to find the wrapper properties"() {
        toolingApi.isEmbedded = false

        projectDir.file('settings.gradle') << "include 'child'"
        projectDir.file('build.gradle') << """
task wrapper(type: Wrapper) { distributionUrl = '${otherVersion.binDistribution.toURI()}' }
allprojects {
    task check << { assert gradle.gradleVersion == '${otherVersion.version.version}' }
}
"""
        projectDir.file('child').createDir()
        executer.withTasks('wrapper').run()

        when:
        toolingApi.withConnector { connector ->
            connector.useDefaultDistribution()
            connector.searchUpwards(true)
            connector.forProjectDirectory(projectDir.file('child'))
        }
        toolingApi.withConnection { connection -> connection.newBuild().forTasks('check').run() }

        then:
        notThrown(Throwable)
    }

    def "can specify a gradle installation to use"() {
        toolingApi.isEmbedded = false
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useInstallation(otherVersion.gradleHomeDir)
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "can specify a gradle distribution to use"() {
        toolingApi.isEmbedded = false
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector { connector ->
            connector.useDistribution(otherVersion.binDistribution.toURI())
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "can specify a gradle version to use"() {
        toolingApi.isEmbedded = false
        projectDir.file('build.gradle').text = "assert gradle.gradleVersion == '${otherVersion.version.version}'"

        when:
        toolingApi.withConnector { GradleConnector connector ->
            connector.useGradleVersion(otherVersion.version.version)
        }
        GradleProject model = toolingApi.withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        model != null
    }

    def "tooling api reports an error when the specified gradle version does not support the tooling api"() {
        def distroZip = buildContext.distribution('0.9.2').binDistribution

        when:
        toolingApi.withConnector { connector -> connector.useDistribution(distroZip.toURI()) }
        toolingApi.maybeFailWithConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The specified Gradle distribution '${distroZip.toURI()}' is not supported by this tooling API version (${GradleVersion.current().version}, protocol version 4)"
    }

    @Issue("GRADLE-2419")
    def "tooling API does not hold JVM open"() {
        given:
        def buildFile = projectDir.file("build.gradle")
        def startTimeoutMs = 60000
        def stateChangeTimeoutMs = 15000
        def stopTimeoutMs = 10000
        def retryIntervalMs = 500

        def path = executer.gradleUserHomeDir.absolutePath
        def path1 = distribution.gradleHomeDir.absolutePath
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'

            repositories {
                maven { url "${new IntegrationTestBuildContext().libsRepo.toURI()}" }
                maven { url "http://repo.gradle.org/gradle/repo" }
            }

            dependencies {
                compile "org.gradle:gradle-tooling-api:${distribution.version.version}"
                runtime 'org.slf4j:slf4j-simple:1.7.2'
            }

            mainClassName = 'Main'

            run {
                args = ["${TextUtil.escapeString(path1)}", "${TextUtil.escapeString(path)}"]
                systemProperty 'org.gradle.daemon.idletimeout', 10000
                systemProperty 'org.gradle.daemon.registry.base', "${TextUtil.escapeString(projectDir.file("daemon").absolutePath)}"
            }

            task thing << {
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
        """

        projectDir.file("src/main/java/Main.java") << """
            import org.gradle.tooling.BuildLauncher;
            import org.gradle.tooling.GradleConnector;
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

                    ProjectConnection connection = connector.connect();
                    try {
                        // Configure the build
                        BuildLauncher launcher = connection.newBuild();
                        launcher.forTasks("thing").withArguments("-u");
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        launcher.setStandardOutput(outputStream);
                        launcher.setStandardError(outputStream);

                        // Run the build
                        launcher.run();
                    } finally {
                        // Clean up
                        connection.close();
                    }
                }
            }
        """

        when:
        GradleHandle handle = executer.inDirectory(projectDir)
                .withTasks('run')
                .withDaemonIdleTimeoutSecs(60)
                .start()

        then:
        // Wait for the tooling API to start the build
        def startMarkerFile = projectDir.file("start.marker")
        def foundStartMarker = startMarkerFile.exists()
        def startAt = System.currentTimeMillis()
        while (handle.running && !foundStartMarker) {
            if (System.currentTimeMillis() - startAt > startTimeoutMs) {
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
        def stopMarkerAt = System.currentTimeMillis()
        stopMarkerFile << new Date().toString()

        // Does the tooling API hold the JVM open (which will also hold the build open)?
        while (handle.running) {
            if (System.currentTimeMillis() - stopMarkerAt > stopTimeoutMs) {
                throw new Exception("timeout after placing stop marker (JVM might have been held open")
            }
        }

        handle.waitForFinish()
    }
}
