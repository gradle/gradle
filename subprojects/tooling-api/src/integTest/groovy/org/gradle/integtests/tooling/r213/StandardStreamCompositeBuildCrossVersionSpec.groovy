/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Ignore

@TargetGradleVersion(">=3.1")
class StandardStreamCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {
    def escapeHeader = "\u001b["
    def stdOutStream = new ByteArrayOutputStream()
    def stdErrStream = new ByteArrayOutputStream()

    def "can receive stdout and stderr with model requests"() {
        given:
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        includeBuilds(builds)

        when:
        withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardOutput(stdOutStream)
            modelBuilder.setStandardError(stdErrStream)
            modelBuilder.get()
        }
        then:
        !stdOut.contains(escapeHeader)
        numberOfParticipants.times {
            assertConfigured("build-$it")
        }

        !stdOut.contains("Stdout from task execution")
        !stdErr.contains("Stdout from task execution")

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    def "can receive stdout and stderr with build launcher"() {
        given:
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        includeBuilds(builds)
        buildFile << """
            task log(dependsOn: gradle.includedBuilds*.task(':log'))
        """

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("log")
            buildLauncher.setStandardOutput(stdOutStream)
            buildLauncher.setStandardError(stdErrStream)
            buildLauncher.run()
        }
        then:
        !stdOut.contains(escapeHeader)
        assertConfigured("build-0")
        assertTaskExecuted("build-0")

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    private void assertConfigured(String project) {
        assert stdOut.contains("Stdout from configuration of ${project}")
        assert stdErr.contains("Stderr from configuration of ${project}")
    }

    private void assertTaskExecuted(String project) {
        assert stdOut.contains("Stdout from task execution in ${project}")
        assert stdErr.contains("Stderr from task execution in ${project}")
    }

    def "can colorize output with build launcher"() {
        given:
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        includeBuilds(builds)

        buildFile << """
            task alwaysUpToDate(dependsOn: gradle.includedBuilds*.task(':alwaysUpToDate'))
        """

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("alwaysUpToDate")
            buildLauncher.setStandardOutput(stdOutStream)
            buildLauncher.colorOutput = true
            buildLauncher.run()
        }
        then:
        stdOut.count("UP-TO-DATE" + escapeHeader) == numberOfParticipants

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @Ignore("StdIn is not yet forwarded to included builds")
    def "can provide standard input to composite when executing tasks"() {
        given:
        InputStream stdIn = new ByteArrayInputStream(("Hello Gradle\n"*numberOfParticipants).bytes)
        def builds = createBuildsThatExpectInput(numberOfParticipants)
        includeBuilds(builds)
        buildFile << """
            task log(dependsOn: gradle.includedBuilds*.task(':log'))
        """

        when:
        withConnection { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks("log")
            buildLauncher.setStandardInput(stdIn)
            buildLauncher.setStandardOutput(stdOutStream)
            buildLauncher.run()
        }
        then:
        noExceptionThrown()
        stdOut.count("Hello Gradle") == 1
        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @Ignore("StdIn is not yet forwarded to included builds")
    def "can provide standard input to composite when requesting models"() {
        given:
        InputStream stdIn = new ByteArrayInputStream(("Hello Gradle\n"*numberOfParticipants).bytes)
        def builds = createBuildsThatExpectInput(numberOfParticipants)
        includeBuilds(builds)

        when:
        def modelRequests = withConnection { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardInput(stdIn)
            modelBuilder.setStandardOutput(stdOutStream)
            modelBuilder.get()
        }
        then:
        noExceptionThrown()
        modelRequests.each {
            assert it.failure == null
        }
        stdOut.count("Hello Gradle") == numberOfParticipants

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    private List createBuildsThatLogMessages(int numberOfParticipants) {
        createBuilds(numberOfParticipants,
"""
        task log {
            doLast {
                System.out.println("Stdout from task execution in \${project.name}")
                System.err.println("Stderr from task execution in \${project.name}")
            }
        }
        task alwaysUpToDate {
            outputs.upToDateWhen { true }
        }

        System.out.println("Stdout from configuration of \${project.name}")
        System.err.println("Stderr from configuration of \${project.name}")
""")
    }

    private List createBuildsThatExpectInput(int numberOfParticipants) {
        createBuilds(numberOfParticipants,
            """
        task log {
            outputs.upToDateWhen { true }
        }

        assert System.in
        def reader = new BufferedReader(new InputStreamReader(System.in));
        String msg = reader.readLine()
        assert msg == "Hello Gradle"
        println msg
""")
    }

    private List createBuilds(int numberOfParticipants, String buildFileText) {
        def builds = []
        numberOfParticipants.times {
            builds << singleProjectBuildInSubfolder("build-${it}") {
                buildFile << buildFileText
            }
        }
        builds
    }

    private String getStdOut() {
        stdOutStream.toString()
    }

    private String getStdErr() {
        stdErrStream.toString()
    }
}
