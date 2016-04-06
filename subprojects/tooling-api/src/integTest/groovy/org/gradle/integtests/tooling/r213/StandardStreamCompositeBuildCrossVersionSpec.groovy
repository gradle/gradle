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
import spock.lang.Ignore

class StandardStreamCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
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
        stdOut.toString().count("Stdout from configuration") == numberOfParticipants
        stdErr.toString().count("Stderr from configuration") == numberOfParticipants

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    def "can receive stdout and stderr with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        OutputStream stdErr = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(builds[0], "log")
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.setStandardError(stdErr)
            buildLauncher.run()
        }
        then:
        !stdOut.toString().contains(escapeHeader)
        stdOut.toString().count("Stdout from configuration") == 1
        stdErr.toString().count("Stderr from configuration") == 1
        stdOut.toString().count("Stdout from task execution") == 1
        stdErr.toString().count("Stderr from task execution") == 1

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @TargetGradleVersion(">=2.3")
    @Ignore("We do not support forTasks(String) on a composite connection for now")
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

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    @TargetGradleVersion(">=2.3")
    def "can colorize output with build launcher"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        def builds = createBuildsThatLogMessages(numberOfParticipants)
        when:
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(builds[0], "alwaysUpToDate")
            buildLauncher.setStandardOutput(stdOut)
            buildLauncher.colorOutput = true
            buildLauncher.run()
        }
        then:
        stdOut.toString().count("UP-TO-DATE" + escapeHeader) == 1

        where:
        numberOfParticipants << [ 1, 3 ]
    }

    // Standard input sort of works, but the first build gobbles up all of the input
    // so none of the other participants see anything
    @Ignore("setStandardInput unsupported by ModelBuilder and BuildLauncher")
    def "can provide standard input to composite when executing tasks"() {
        given:
        OutputStream stdOut = new ByteArrayOutputStream()
        InputStream stdIn = new ByteArrayInputStream(("Hello Gradle\n"*numberOfParticipants).bytes)
        def builds = createBuildsThatExpectInput(numberOfParticipants)
        when:
        withCompositeConnection(builds) { connection ->
            def buildLauncher = connection.newBuild()
            buildLauncher.forTasks(builds[0], "log")
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

    @Ignore("setStandardInput unsupported by ModelBuilder and BuildLauncher")
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
            doLast {
                System.out.println("Stdout from task execution")
                System.err.println("Stderr from task execution")
            }
        }
        task alwaysUpToDate {
            outputs.upToDateWhen { true }
        }

        System.out.println("Stdout from configuration")
        System.err.println("Stderr from configuration")
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
