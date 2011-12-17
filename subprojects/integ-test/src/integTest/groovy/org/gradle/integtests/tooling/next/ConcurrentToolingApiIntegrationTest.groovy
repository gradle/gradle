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

package org.gradle.integtests.tooling.next

import org.gradle.integtests.fixtures.BasicGradleDistribution
import org.gradle.integtests.tooling.fixture.MinTargetGradleVersion
import org.gradle.integtests.tooling.fixture.MinToolingApiVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.tests.fixtures.ConcurrentTestUtil
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.model.Project
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Issue
import org.gradle.tooling.*

@MinToolingApiVersion(currentOnly = true)
@MinTargetGradleVersion(currentOnly = true)
@Issue("GRADLE-1933")
class ConcurrentToolingApiIntegrationTest extends ToolingApiSpecification {

    @Rule def concurrent = new ConcurrentTestUtil()
    int threads = 3

    def setup() {
        toolingApi.isEmbedded = false
        concurrent.shortTimeout = 20000
        new ConnectorServices().reset()
    }

    def "handles concurrent scenario"() {
        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        threads.times {
            concurrent.start { useToolingApi() }
        }

        then:
        concurrent.finished()
    }

    @Ignore
    //TODO SF enable this test after releasing 1.7
    def "handles concurrent builds with different target Gradle version"() {
        dist.file('build.gradle')  << "apply plugin: 'java'"

        when:
        threads.times { concurrent.start { useToolingApi() } }
        threads.times { concurrent.start { useToolingApi(dist.previousVersion("1.0-milestone-7"))} }

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
            def connector = new ConfigurableConnector(connector: toolingApi.connector())
                .distributionOperation( { it.description = "download for 1"; Thread.sleep(500) } )
                .forProjectDirectory(dist.file("build1"))

            connector.forProjectDirectory(dist.file("build1"))
            def connection = connector.connect()

            try {
                def build = connection.newBuild()
                def operation = new ConfigurableOperation(build)
                build.forTasks('foo1').run()
                assert operation.progressMessages.contains("download for 1")
                assert !operation.progressMessages.contains("download for 2")
                allProgress << operation.progressMessages
            } finally {
                connection.close()
            }
        }

        concurrent.start {
            def connector = new ConfigurableConnector(connector: toolingApi.connector())
                .distributionOperation( { it.description = "download for 2"; Thread.sleep(500) } )
                .forProjectDirectory(dist.file("build2"))

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
                def connector = new ConfigurableConnector(connector: toolingApi.connector())
                    .distributionProgressMessage("download for " + idx)

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

    static class ConfigurableConnector extends GradleConnector {
        @Delegate GradleConnector connector

        ConfigurableConnector distributionProgressMessage(String message) {
            connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: { it.description = message} )
            this
        }

        ConfigurableConnector distributionOperation(Closure operation) {
            connector.distribution = new ConfigurableDistribution(delegate: connector.distribution, operation: operation )
            this
        }
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
"""
        }

        then:
        threads.times { idx ->
            concurrent.start {
                withConnectionInDir("build$idx") { connection ->
                    def model = connection.model(Project.class)
                    def operation = new ConfigurableOperation(model)
                    assert model.get()

                    assert operation.getStandardOutput().contains("this is stdout: $idx")
                    assert operation.getStandardOutput().count("this is stdout") == 1

                    assert operation.getStandardError().contains("this is stderr: $idx")
                    assert operation.getStandardError().count("this is stderr") == 1
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
                }
            }
        }

        concurrent.finished()
    }

    static class ProgressTrackingListener implements ProgressListener {
        def progressMessages = []
        void statusChanged(ProgressEvent event) {
            progressMessages << event.description
        }
    }

    static class ConfigurableOperation {
        LongRunningOperation operation
        def listener = new ProgressTrackingListener()
        def stdout = new ByteArrayOutputStream()
        def stderr = new ByteArrayOutputStream()

        public ConfigurableOperation(LongRunningOperation operation) {
            this.operation = operation
            operation.addProgressListener(listener)
            operation.standardOutput = stdout
            operation.standardError = stderr
        }

        String getStandardOutput() {
            return stdout.toString()
        }

        String getStandardError() {
            return stderr.toString()
        }

        ConfigurableOperation setStandardInput(String input) {
            this.operation.standardInput = new ByteArrayInputStream(input.toString().bytes)
            return this
        }

        List getProgressMessages() {
            return listener.progressMessages
        }
    }

    def withConnectionInDir(String dir, Closure cl) {
        GradleConnector connector = toolingApi.connector()
        connector.forProjectDirectory(dist.file(dir))
        ProjectConnection connection = connector.connect()
        try {
            return cl(connection)
        } finally {
            connection.close();
        }
    }
}
