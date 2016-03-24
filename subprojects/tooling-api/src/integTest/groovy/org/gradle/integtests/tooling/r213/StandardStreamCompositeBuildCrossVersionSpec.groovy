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

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Ignore

class StandardStreamCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    @Rule RedirectStdOutAndErr stdOutAndErr = new RedirectStdOutAndErr()
    def escapeHeader = "\u001b["

    def "can receive stdout and stderr with model requests"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        OutputStream stdErr = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeConnection(builds) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardOutput(stdOut)
            modelBuilder.setStandardError(stdErr)
            modelBuilder.get()
        }
        then:
        !stdOut.toString().contains(escapeHeader)
        stdOut.toString().count("This is standard out") == numberOfParticipants
        stdErr.toString().count("This is standard err") == numberOfParticipants
        !stdOutAndErr.stdOut.contains("This is standard out")
        !stdOutAndErr.stdErr.contains("This is standard err")

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    def "can receive stdout and stderr with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        OutputStream stdErr = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            def buildLauncher = connection.newBuild(buildIds.get(0))
            buildLauncher.forTasks("log")
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.setStandardError(stdErr)
            buildLauncher.run()
        }
        then:
        !stdOut.toString().contains(escapeHeader)
        stdOut.toString().count("This is standard out") == 1
        stdErr.toString().count("This is standard err") == 1
        !stdOutAndErr.stdOut.contains("This is standard out")
        !stdOutAndErr.stdErr.contains("This is standard err")

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @TargetGradleVersion(">=2.3")
    def "can colorize output with model requests"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeConnection(builds) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.forTasks("log")
            modelBuilder.setStandardOutput(stdOut)
            modelBuilder.colorOutput = true
            modelBuilder.get()
        }
        then:
        stdOut.toString().count("UP-TO-DATE" + escapeHeader) == numberOfParticipants
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @TargetGradleVersion(">=2.3")
    def "can colorize output with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            def buildLauncher = connection.newBuild(buildIds.get(0))
            buildLauncher.forTasks("log")
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.colorOutput = true
            buildLauncher.run()
        }
        then:
        stdOut.toString().count("UP-TO-DATE" + escapeHeader) == 1
        !stdOutAndErr.stdOut.contains(escapeHeader)
        !stdOutAndErr.stdErr.contains(escapeHeader)

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    // Standard input sort of works, but the first build gobbles up all of the input
    // so none of the other participants see anything
    @Ignore
    def "can provide standard input to composite when executing tasks"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        InputStream stdIn = new ByteArrayInputStream(("Hello Gradle\n"*numberOfParticipants).bytes)
        def builds = createBuildsThatExpectInput(numberOfParticipants)
        when:
        withCompositeBuildParticipants(builds) { connection, buildIds ->
            def buildLauncher = connection.newBuild(buildIds.get(0))
            buildLauncher.forTasks("log")
            buildLauncher.setStandardInput(stdIn)
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.run()
        }
        then:
        noExceptionThrown()
        stdOut.toString().count("Hello Gradle") == 1
        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @Ignore
    def "can provide standard input to composite when requesting models"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        InputStream stdIn = new ByteArrayInputStream(("Hello Gradle\n"*numberOfParticipants).bytes)
        def builds = createBuildsThatExpectInput(numberOfParticipants)
        when:
        def modelRequests = withCompositeConnection(builds) { GradleConnection connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.setStandardInput(stdIn)
            modelBuilder.setStandardOutput(stdOut)
            modelBuilder.get()
        }
        then:
        noExceptionThrown()
        modelRequests.each {
            assert it.failure == null
        }
        stdOut.toString().count("Hello Gradle") == numberOfParticipants

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    private List createBuildsThatLogMessages(int numberOfParticipants) {
        createBuilds(numberOfParticipants,
"""
        task log {
            outputs.upToDateWhen { true }
        }

        System.out.println("This is standard out")
        System.err.println("This is standard err")
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
            builds << populate("build-${it}") {
                buildFile << buildFileText
            }
        }
        builds
    }
}
