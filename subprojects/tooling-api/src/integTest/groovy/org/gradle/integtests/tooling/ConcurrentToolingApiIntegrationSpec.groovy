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

import org.gradle.initialization.BuildCancellationToken
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.ConfigurableOperation
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Assume
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Retry

import java.util.concurrent.CopyOnWriteArrayList

import static org.gradle.integtests.fixtures.RetryConditions.onWindowsSocketDisappearance
import static spock.lang.Retry.Mode.SETUP_FEATURE_CLEANUP

@Issue("GRADLE-1933")
@Retry(condition = { onWindowsSocketDisappearance(instance, failure) }, mode = SETUP_FEATURE_CLEANUP, count = 2)
@IgnoreIf({ GradleContextualExecuter.embedded }) // concurrent tooling api is only supported for forked mode
class ConcurrentToolingApiIntegrationSpec extends AbstractIntegrationSpec {

    @Rule final ConcurrentTestUtil concurrent = new ConcurrentTestUtil()
    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)

    int threads = 3

    def setup() {
        concurrent.shortTimeout = 180000
        settingsFile.touch()
    }

    def "handles the same target gradle version concurrently"() {
        buildFile << "apply plugin: 'java'"

        when:
        threads.times {
            concurrent.start { useToolingApi(toolingApi) }
        }

        then:
        concurrent.finished()
    }

    def "handles different target gradle versions concurrently"() {
        given:
        def last = new ReleasedVersionDistributions().getMostRecentRelease()
        // When adding support for a new JDK version, the previous release might not work with it yet.
        Assume.assumeTrue(last.worksWith(Jvm.current()))
        assert dist != last
        println "Combination of versions used: current - $dist, last - $last"
        def oldDistApi = new ToolingApi(last, temporaryFolder)

        buildFile << "apply plugin: 'java'"

        when:
        concurrent.start { useToolingApi(toolingApi) }
        concurrent.start { useToolingApi(oldDistApi)}

        then:
        concurrent.finished()
    }

    def useToolingApi(ToolingApi target) {
        target.withConnection { ProjectConnection connection ->
            try {
                def model = connection.getModel(IdeaProject)
                assert model != null
                //a bit more stress:
                connection.newBuild().forTasks('tasks').run()
            } catch (Exception e) {
                throw new RuntimeException("""We might have hit a concurrency problem.
See the full stacktrace and the list of causes to investigate""", e);
            }
        }
    }

    def "can share connection when running build"() {
        given:
        buildFile << """
def text = System.in.text
System.out.println 'out=' + text
System.err.println 'err=' + text
project.description = text
"""

        when:
        toolingApi.withConnection { connection ->
            threads.times { idx ->
                concurrent.start {
                    def model = connection.model(GradleProject.class)
                    def operation = new ConfigurableOperation(model)
                        .setStandardInput("hasta la vista $idx")

                    assert model.get().description == "hasta la vista $idx"

                    assert operation.standardOutput.contains("out=hasta la vista $idx")
                    assert operation.standardOutput.count("out=hasta la vista") == 1

                    assert operation.standardError.contains("err=hasta la vista $idx")
                    assert operation.standardError.count("err=hasta la vista") == 1
                }
            }
            concurrent.finished()
        }

        then:
        noExceptionThrown()
    }

    def "handles standard input concurrently when getting model"() {
        when:
        threads.times { idx ->
            buildTestFixture.withBuildInSubDir()
            singleProjectBuild("build$idx") {
                buildFile << "description = System.in.text"
            }
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def model = connection.model(GradleProject.class)
                    model.standardInput = new ByteArrayInputStream("project $idx".toString().bytes)
                    def project = model.get()
                    assert project.description == "project $idx"
                }
            }
        }

        concurrent.finished()
    }

    def "handles standard input concurrently when running build"() {
        when:
        threads.times { idx ->
            buildTestFixture.withBuildInSubDir()
            singleProjectBuild("build$idx") {
                buildFile << "task show { doLast { println System.in.text} }"
            }
        }


        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def build = connection.newBuild()
                    def operation = new ConfigurableOperation(build)
                        .setStandardInput("hasta la vista $idx")
                    build.forTasks('show').run()
                    assert operation.standardOutput.contains("hasta la vista $idx")
                }
            }
        }

        concurrent.finished()
    }

    def "during task execution receives distribution progress including waiting for the other thread"() {
        given:
        buildTestFixture.withBuildInSubDir()
        def build1 = singleProjectBuild("build1") {
            buildFile << "task foo1"
        }
        def build2 = singleProjectBuild("build2") {
            buildFile << "task foo2"
        }

        when:
        def allProgress = new CopyOnWriteArrayList<String>()

        concurrent.start {
            def connector = toolingApi.connector(build1)
            distributionOperation(connector, { it.description = "download for 1"; Thread.sleep(500) } )

            toolingApi.withConnection(connector) { connection ->
                def build = connection.newBuild()
                def operation = new ConfigurableOperation(build)
                build.forTasks('foo1').run()
                assert operation.progressMessages.contains("download for 1")
                assert !operation.progressMessages.contains("download for 2")
                allProgress << operation.progressMessages
            }
        }

        concurrent.start {
            def connector = toolingApi.connector(build2)
            distributionOperation(connector, { it.description = "download for 2"; Thread.sleep(500) } )

            def connection = connector.connect()

            try {
                def build = connection.newBuild()
                def operation = new ConfigurableOperation(build)
                build.forTasks('foo2').run()
                assert operation.progressMessages.contains("download for 2")
                assert !operation.progressMessages.contains("download for 1")
                allProgress << operation.progressMessages
            } finally {
                connection.close()
            }
        }

        then:
        concurrent.finished()
        //only one thread should log that progress message
        1 == allProgress.count {
            it.contains("Wait for the other thread to finish acquiring the distribution")
        }
    }

    def "during model building receives distribution progress"() {
        given:
        threads.times { idx ->
            file("build$idx/build.gradle") << "apply plugin: 'java'"
        }

        when:
        threads.times { idx ->
            concurrent.start {
                def connector = toolingApi.connector()
                distributionProgressMessage(connector, "download for " + idx)

                def connection = connector.connect()

                try {
                    def model = connection.model(GradleProject)
                    def operation = new ConfigurableOperation(model)

                    assert model.get()
                    assert operation.progressMessages.contains("download for " + idx)
                    assert !operation.progressMessages.contains("download for " + ++idx)
                } finally {
                    connection.close()
                }
            }
        }

        then:
        concurrent.finished()
    }

    static void distributionProgressMessage(connector, String message) {
        connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: { it.description = message} )
    }

    static void distributionOperation(connector, Closure operation) {
        connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: operation )
    }

    static class ConfigurableDistribution implements Distribution {
        Distribution delegate
        Closure operation

        String getDisplayName() {
            return 'mock'
        }

        ClassPath getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
            def o = progressLoggerFactory.newOperation("mock")
            operation(o)
            o.started()
            o.completed()
            return delegate.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken)
        }
    }

    def "receives progress and logging while the model is building"() {
        when:
        //create build folders with slightly different builds
        threads.times { idx ->
            buildTestFixture.withBuildInSubDir()
            singleProjectBuild("build$idx") {
                buildFile << """
System.out.println 'this is stdout: $idx'
System.err.println 'this is stderr: $idx'
logger.lifecycle 'this is lifecycle: $idx'
"""
            }
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def model = connection.model(GradleProject.class)
                    def operation = new ConfigurableOperation(model)
                    assert model.get()

                    assert operation.standardOutput.contains("this is stdout: $idx")
                    assert operation.standardOutput.count("this is stdout") == 1

                    assert operation.standardError.contains("this is stderr: $idx")
                    assert operation.standardError.count("this is stderr") == 1

                    assert operation.standardOutput.contains("this is lifecycle: $idx")
                    assert operation.standardOutput.count("this is lifecycle") == 1
                }
            }
        }

        concurrent.finished()
    }

    def "receives progress and logging while the build is executing"() {
        when:
        //create build folders with slightly different builds
        threads.times { idx ->
            buildTestFixture.withBuildInSubDir()
            singleProjectBuild("build$idx") {
                buildFile << """
System.out.println 'this is stdout: $idx'
System.err.println 'this is stderr: $idx'
logger.lifecycle 'this is lifecycle: $idx'
"""
            }
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def build = connection.newBuild()
                    def operation = new ConfigurableOperation(build)
                    build.run()

                    assert operation.standardOutput.contains("this is stdout: $idx")
                    assert operation.standardOutput.count("this is stdout") == 1

                    assert operation.standardError.contains("this is stderr: $idx")
                    assert operation.standardError.count("this is stderr") == 1

                    assert operation.standardOutput.contains("this is lifecycle: $idx")
                    assert operation.standardOutput.count("this is lifecycle") == 1
                }
            }
        }

        concurrent.finished()
    }

    def withConnectionInDir(String dir, Closure cl) {
        def connector = toolingApi.connector(file(dir))
        toolingApi.withConnection(connector, cl)
    }
}
