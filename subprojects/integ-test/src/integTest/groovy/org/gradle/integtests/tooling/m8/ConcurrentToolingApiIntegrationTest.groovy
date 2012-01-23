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

package org.gradle.integtests.tooling.m8

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.fixtures.ReleasedVersions
import org.gradle.integtests.tooling.fixture.ConfigurableOperation
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import spock.lang.Issue

@MinToolingApiVersion('1.0-milestone-8')
@MinTargetGradleVersion('1.0-milestone-8')
@Issue("GRADLE-1933")
class ConcurrentToolingApiIntegrationTest extends ToolingApiSpecification {

    @Rule def concurrent = new ConcurrentTestUtil()
    int threads = 3

    def setup() {
        //concurrent tooling api at the moment is only supported for forked mode
        toolingApi.isEmbedded = false
        concurrent.shortTimeout = 30000
        new ConnectorServices().reset()
    }

    def cleanup() {
        new ConnectorServices().reset()
    }

    def "handles the same target gradle version concurrently"() {
        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        threads.times {
            concurrent.start { useToolingApi() }
        }

        then:
        concurrent.finished()
    }

    def "handles different target gradle versions concurrently"() {
        given:
        def current = getTargetDist()
        def previous = new ReleasedVersions(dist).getPreviousOf(current)
        assert current != previous
        println "Combination of versions used: current - $current, previous - $previous"

        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        concurrent.start { useToolingApi(current) }
        concurrent.start { useToolingApi(previous)}

        then:
        concurrent.finished()
    }

    def useToolingApi(BasicGradleDistribution target = null) {
        if (target != null) {
            selectTargetDist(target)
        }

        withConnection { ProjectConnection connection ->
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
        dist.file("build.gradle") << """
def text = System.in.text
System.out.println 'out=' + text
System.err.println 'err=' + text
project.description = text
"""

        when:
        withConnection { connection ->
            threads.times { idx ->
                concurrent.start {
                    def model = connection.model(Project.class)
                    def operation = new ConfigurableOperation(model)
                        .setStandardInput("hasta la vista $idx")

                    assert model.get().description == "hasta la vista $idx"

                    assert operation.getStandardOutput().contains("out=hasta la vista $idx")
                    assert operation.getStandardOutput().count("out=hasta la vista") == 1

                    assert operation.getStandardError().contains("err=hasta la vista $idx")
                    assert operation.getStandardError().count("err=hasta la vista") == 1
                }
            }
            concurrent.finished()
        }

        then: noExceptionThrown()
    }

    def "handles standard input concurrently when getting model"() {
        when:
        threads.times { idx ->
            dist.file("build$idx/build.gradle") << "description = System.in.text"
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def model = connection.model(Project.class)
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
            dist.file("build$idx/build.gradle") << "task show << { println System.in.text}"
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
        dist.file("build1/build.gradle") << "task foo1"
        dist.file("build2/build.gradle") << "task foo2"

        when:
        def allProgress = []

        concurrent.start {
            def connector = connector()
            distributionOperation(connector, { it.description = "download for 1"; Thread.sleep(500) } )
            connector.forProjectDirectory(dist.file("build1"))

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
            def connector = connector()
            distributionOperation(connector, { it.description = "download for 2"; Thread.sleep(500) } )
            connector.forProjectDirectory(dist.file("build2"))

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
            dist.file("build$idx/build.gradle") << "apply plugin: 'java'"
        }

        when:
        threads.times { idx ->
            concurrent.start {
                def connector = connector()
                distributionProgressMessage(connector, "download for " + idx)

                def connection = connector.connect()

                try {
                    def model = connection.model(Project)
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

    void distributionProgressMessage(GradleConnector connector, String message) {
        connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: { it.description = message} )
    }

    void distributionOperation(GradleConnector connector, Closure operation) {
        connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: operation )
    }

    static class ConfigurableDistribution implements Distribution {
        Distribution delegate
        Closure operation

        String getDisplayName() {
            return 'mock'
        }

        Set<File> getToolingImplementationClasspath(ProgressLoggerFactory progressLoggerFactory) {
            def o = progressLoggerFactory.newOperation("mock")
            operation(o)
            o.started()
            o.completed()
            return delegate.getToolingImplementationClasspath(progressLoggerFactory)
        }
    }

    def "receives progress and logging while the model is building"() {
        when:
        //create build folders with slightly different builds
        threads.times { idx ->
            dist.file("build$idx/build.gradle") << """
System.out.println 'this is stdout: $idx'
System.err.println 'this is stderr: $idx'
logger.lifecycle 'this is lifecycle: $idx'
"""
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def model = connection.model(Project.class)
                    def operation = new ConfigurableOperation(model)
                    assert model.get()

                    assert operation.standardOutput.contains("this is stdout: $idx")
                    assert operation.standardOutput.count("this is stdout") == 1

                    assert operation.standardError.contains("this is stderr: $idx")
                    assert operation.standardError.count("this is stderr") == 1

                    assert operation.standardOutput.contains("this is lifecycle: $idx")
                    assert operation.standardOutput.count("this is lifecycle") == 1
                    assert operation.standardError.count("this is lifecycle") == 0
                }
            }
        }

        concurrent.finished()
    }

    def "receives progress and logging while the build is executing"() {
        when:
        //create build folders with slightly different builds
        threads.times { idx ->
            dist.file("build$idx/build.gradle") << """
System.out.println 'this is stdout: $idx'
System.err.println 'this is stderr: $idx'
logger.lifecycle 'this is lifecycle: $idx'
"""
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
                    assert operation.standardError.count("this is lifecycle") == 0
                }
            }
        }

        concurrent.finished()
    }

    def withConnectionInDir(String dir, Closure cl) {
        GradleConnector connector = connector()
        connector.forProjectDirectory(dist.file(dir))
        withConnection(connector, cl)
    }
}
