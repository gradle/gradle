/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.integtests.tooling.r25

import groovy.transform.NotYetImplemented
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.*
import org.gradle.tooling.model.gradle.BuildInvocations

class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=1.0-milestone-8 <2.5")
    def "ignores listeners when Gradle version does not generate build events"() {
        given:
        goodCode()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    throw new RuntimeException()
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then:
        noExceptionThrown()
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive build progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        List<ProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << event
                }, EnumSet.of(OperationType.GENERIC)).get()
        }

        then: "build progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        List<ProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    result << event
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then: "build progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "build aborts if a build listener throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then: "build aborts if the build listener throws an exception"
        thrown(GradleConnectionException)
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive current build progress event even if one of multiple build listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<ProgressEvent> resultsOfFirstListener = []
        List<ProgressEvent> resultsOfLastListener = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    resultsOfFirstListener.add(event)
                }, EnumSet.of(OperationType.GENERIC)).addProgressListener({ ProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }, EnumSet.of(OperationType.GENERIC)).addProgressListener({ ProgressEvent event ->
                    resultsOfLastListener.add(event)
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then: "current build progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        ListenerFailedException ex = thrown()
        resultsOfFirstListener.size() > 0
        resultsOfLastListener.size() > 0
        ex.causes.size() == resultsOfLastListener.size()

        and: "build is successful"
        def lastEvent = resultsOfLastListener[-1]
        lastEvent instanceof FinishEvent
        lastEvent.displayName == 'Run build succeeded'
        lastEvent.result instanceof SuccessResult
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive build progress events for successful operations"() {
        given:
        goodCode()

        when:
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('classes').addProgressListener({ ProgressEvent event ->
                    result << event
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 6 * 2          // build running, init scripts, loading, configuring, populating task graph, executing tasks
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def buildRunningStarted = result[0]
        buildRunningStarted instanceof StartEvent &&
            buildRunningStarted.eventTime > 0 &&
            buildRunningStarted.displayName == 'Run build started' &&
            buildRunningStarted.descriptor.name == 'Run build' &&
            buildRunningStarted.descriptor.displayName == 'Run build' &&
            buildRunningStarted.descriptor.parent == null
        def evaluatingInitScriptsStarted = result[1]
        evaluatingInitScriptsStarted instanceof StartEvent &&
            evaluatingInitScriptsStarted.eventTime > 0 &&
            evaluatingInitScriptsStarted.displayName == 'Run init scripts started' &&
            evaluatingInitScriptsStarted.descriptor.name == 'Run init scripts' &&
            evaluatingInitScriptsStarted.descriptor.displayName == 'Run init scripts' &&
            evaluatingInitScriptsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingInitScriptsFinished = result[2]
        evaluatingInitScriptsFinished instanceof FinishEvent &&
            evaluatingInitScriptsFinished.eventTime > 0 &&
            evaluatingInitScriptsFinished.displayName == 'Run init scripts succeeded' &&
            evaluatingInitScriptsFinished.descriptor.name == 'Run init scripts' &&
            evaluatingInitScriptsFinished.descriptor.displayName == 'Run init scripts' &&
            evaluatingInitScriptsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingInitScriptsFinished.result instanceof SuccessResult &&
            evaluatingInitScriptsFinished.result.startTime == evaluatingInitScriptsStarted.eventTime &&
            evaluatingInitScriptsFinished.result.endTime == evaluatingInitScriptsFinished.eventTime
        def evaluatingSettingsStarted = result[3]
        evaluatingSettingsStarted instanceof StartEvent &&
            evaluatingSettingsStarted.eventTime > 0 &&
            evaluatingSettingsStarted.displayName == 'Load projects started' &&
            evaluatingSettingsStarted.descriptor.name == 'Load projects' &&
            evaluatingSettingsStarted.descriptor.displayName == 'Load projects' &&
            evaluatingSettingsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingSettingsFinished = result[4]
        evaluatingSettingsFinished instanceof FinishEvent &&
            evaluatingSettingsFinished.eventTime > 0 &&
            evaluatingSettingsFinished.displayName == 'Load projects succeeded' &&
            evaluatingSettingsFinished.descriptor.name == 'Load projects' &&
            evaluatingSettingsFinished.descriptor.displayName == 'Load projects' &&
            evaluatingSettingsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingSettingsFinished.result instanceof SuccessResult &&
            evaluatingSettingsFinished.result.startTime == evaluatingSettingsStarted.eventTime &&
            evaluatingSettingsFinished.result.endTime == evaluatingSettingsFinished.eventTime
        def configuringBuildStarted = result[5]
        configuringBuildStarted instanceof StartEvent &&
            configuringBuildStarted.eventTime > 0 &&
            configuringBuildStarted.displayName == 'Configure build started' &&
            configuringBuildStarted.descriptor.name == 'Configure build' &&
            configuringBuildStarted.descriptor.displayName == 'Configure build' &&
            configuringBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def configuringBuildFinished = result[6]
        configuringBuildFinished instanceof FinishEvent &&
            configuringBuildFinished.eventTime > 0 &&
            configuringBuildFinished.displayName == 'Configure build succeeded' &&
            configuringBuildFinished.descriptor.name == 'Configure build' &&
            configuringBuildFinished.descriptor.displayName == 'Configure build' &&
            configuringBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            configuringBuildFinished.result instanceof SuccessResult &&
            configuringBuildFinished.result.startTime == configuringBuildStarted.eventTime &&
            configuringBuildFinished.result.endTime == configuringBuildFinished.eventTime
        def populatingTaskGraphStarted = result[7]
        populatingTaskGraphStarted instanceof StartEvent &&
            populatingTaskGraphStarted.eventTime > 0 &&
            populatingTaskGraphStarted.displayName == 'Calculate task graph started' &&
            populatingTaskGraphStarted.descriptor.name == 'Calculate task graph' &&
            populatingTaskGraphStarted.descriptor.displayName == 'Calculate task graph' &&
            populatingTaskGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def populatingTaskGraphFinished = result[8]
        populatingTaskGraphFinished instanceof FinishEvent &&
            populatingTaskGraphFinished.eventTime > 0 &&
            populatingTaskGraphFinished.displayName == 'Calculate task graph succeeded' &&
            populatingTaskGraphFinished.descriptor.name == 'Calculate task graph' &&
            populatingTaskGraphFinished.descriptor.displayName == 'Calculate task graph' &&
            populatingTaskGraphFinished.descriptor.parent == buildRunningStarted.descriptor &&
            populatingTaskGraphFinished.result instanceof SuccessResult &&
            populatingTaskGraphFinished.result.startTime == populatingTaskGraphStarted.eventTime &&
            populatingTaskGraphFinished.result.endTime == populatingTaskGraphFinished.eventTime
        def executingTasksGraphStarted = result[9]
        executingTasksGraphStarted instanceof StartEvent &&
            executingTasksGraphStarted.eventTime > 0 &&
            executingTasksGraphStarted.displayName == 'Run tasks started' &&
            executingTasksGraphStarted.descriptor.name == 'Run tasks' &&
            executingTasksGraphStarted.descriptor.displayName == 'Run tasks' &&
            executingTasksGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def executingTasksFinished = result[10]
        executingTasksFinished instanceof FinishEvent &&
            executingTasksFinished.eventTime > 0 &&
            executingTasksFinished.displayName == 'Run tasks succeeded' &&
            executingTasksFinished.descriptor.name == 'Run tasks' &&
            executingTasksFinished.descriptor.displayName == 'Run tasks' &&
            executingTasksFinished.descriptor.parent == buildRunningStarted.descriptor &&
            executingTasksFinished.result instanceof SuccessResult &&
            executingTasksFinished.result.startTime == executingTasksGraphStarted.eventTime &&
            executingTasksFinished.result.endTime == executingTasksFinished.eventTime
        def buildRunningFinished = result[11]
        buildRunningFinished instanceof FinishEvent &&
            buildRunningFinished.eventTime > 0 &&
            buildRunningFinished.displayName == 'Run build succeeded' &&
            buildRunningFinished.descriptor.name == 'Run build' &&
            buildRunningFinished.descriptor.displayName == 'Run build' &&
            buildRunningFinished.descriptor.parent == null &&
            buildRunningFinished.result instanceof SuccessResult &&
            buildRunningFinished.result.startTime == buildRunningStarted.eventTime &&
            buildRunningFinished.result.endTime == buildRunningFinished.eventTime
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive build progress events for failed operations"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     throw new RuntimeException("broken", new RuntimeException("nope"));
                }
            }
        """

        when:
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener({ ProgressEvent event ->
                    result << event
                }, EnumSet.of(OperationType.GENERIC)).run()
        }

        then:
        thrown(BuildException)

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 6 * 2          // build running, init scripts, loading, configuring, populating task graph, executing tasks
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def buildRunningStarted = result[0]
        buildRunningStarted instanceof StartEvent &&
            buildRunningStarted.eventTime > 0 &&
            buildRunningStarted.displayName == 'Run build started' &&
            buildRunningStarted.descriptor.name == 'Run build' &&
            buildRunningStarted.descriptor.displayName == 'Run build' &&
            buildRunningStarted.descriptor.parent == null
        def evaluatingInitScriptsStarted = result[1]
        evaluatingInitScriptsStarted instanceof StartEvent &&
            evaluatingInitScriptsStarted.eventTime > 0 &&
            evaluatingInitScriptsStarted.displayName == 'Run init scripts started' &&
            evaluatingInitScriptsStarted.descriptor.name == 'Run init scripts' &&
            evaluatingInitScriptsStarted.descriptor.displayName == 'Run init scripts' &&
            evaluatingInitScriptsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingInitScriptsFinished = result[2]
        evaluatingInitScriptsFinished instanceof FinishEvent &&
            evaluatingInitScriptsFinished.eventTime > 0 &&
            evaluatingInitScriptsFinished.displayName == 'Run init scripts succeeded' &&
            evaluatingInitScriptsFinished.descriptor.name == 'Run init scripts' &&
            evaluatingInitScriptsFinished.descriptor.displayName == 'Run init scripts' &&
            evaluatingInitScriptsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingInitScriptsFinished.result instanceof SuccessResult &&
            evaluatingInitScriptsFinished.result.startTime == evaluatingInitScriptsStarted.eventTime &&
            evaluatingInitScriptsFinished.result.endTime == evaluatingInitScriptsFinished.eventTime
        def evaluatingSettingsStarted = result[3]
        evaluatingSettingsStarted instanceof StartEvent &&
            evaluatingSettingsStarted.eventTime > 0 &&
            evaluatingSettingsStarted.displayName == 'Load projects started' &&
            evaluatingSettingsStarted.descriptor.name == 'Load projects' &&
            evaluatingSettingsStarted.descriptor.displayName == 'Load projects' &&
            evaluatingSettingsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingSettingsFinished = result[4]
        evaluatingSettingsFinished instanceof FinishEvent &&
            evaluatingSettingsFinished.eventTime > 0 &&
            evaluatingSettingsFinished.displayName == 'Load projects succeeded' &&
            evaluatingSettingsFinished.descriptor.name == 'Load projects' &&
            evaluatingSettingsFinished.descriptor.displayName == 'Load projects' &&
            evaluatingSettingsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingSettingsFinished.result instanceof SuccessResult &&
            evaluatingSettingsFinished.result.startTime == evaluatingSettingsStarted.eventTime &&
            evaluatingSettingsFinished.result.endTime == evaluatingSettingsFinished.eventTime
        def configuringBuildStarted = result[5]
        configuringBuildStarted instanceof StartEvent &&
            configuringBuildStarted.eventTime > 0 &&
            configuringBuildStarted.displayName == 'Configure build started' &&
            configuringBuildStarted.descriptor.name == 'Configure build' &&
            configuringBuildStarted.descriptor.displayName == 'Configure build' &&
            configuringBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def configuringBuildFinished = result[6]
        configuringBuildFinished instanceof FinishEvent &&
            configuringBuildFinished.eventTime > 0 &&
            configuringBuildFinished.displayName == 'Configure build succeeded' &&
            configuringBuildFinished.descriptor.name == 'Configure build' &&
            configuringBuildFinished.descriptor.displayName == 'Configure build' &&
            configuringBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            configuringBuildFinished.result instanceof SuccessResult &&
            configuringBuildFinished.result.startTime == configuringBuildStarted.eventTime &&
            configuringBuildFinished.result.endTime == configuringBuildFinished.eventTime
        def populatingTaskGraphStarted = result[7]
        populatingTaskGraphStarted instanceof StartEvent &&
            populatingTaskGraphStarted.eventTime > 0 &&
            populatingTaskGraphStarted.displayName == 'Calculate task graph started' &&
            populatingTaskGraphStarted.descriptor.name == 'Calculate task graph' &&
            populatingTaskGraphStarted.descriptor.displayName == 'Calculate task graph' &&
            populatingTaskGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def populatingTaskGraphFinished = result[8]
        populatingTaskGraphFinished instanceof FinishEvent &&
            populatingTaskGraphFinished.eventTime > 0 &&
            populatingTaskGraphFinished.displayName == 'Calculate task graph succeeded' &&
            populatingTaskGraphFinished.descriptor.name == 'Calculate task graph' &&
            populatingTaskGraphFinished.descriptor.displayName == 'Calculate task graph' &&
            populatingTaskGraphFinished.descriptor.parent == buildRunningStarted.descriptor &&
            populatingTaskGraphFinished.result instanceof SuccessResult &&
            populatingTaskGraphFinished.result.startTime == populatingTaskGraphStarted.eventTime &&
            populatingTaskGraphFinished.result.endTime == populatingTaskGraphFinished.eventTime
        def executingTasksGraphStarted = result[9]
        executingTasksGraphStarted instanceof StartEvent &&
            executingTasksGraphStarted.eventTime > 0 &&
            executingTasksGraphStarted.displayName == 'Run tasks started' &&
            executingTasksGraphStarted.descriptor.name == 'Run tasks' &&
            executingTasksGraphStarted.descriptor.displayName == 'Run tasks' &&
            executingTasksGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def executingTasksFinished = result[10]
        executingTasksFinished instanceof FinishEvent &&
            executingTasksFinished.eventTime > 0 &&
            executingTasksFinished.displayName == 'Run tasks failed' &&
            executingTasksFinished.descriptor.name == 'Run tasks' &&
            executingTasksFinished.descriptor.displayName == 'Run tasks' &&
            executingTasksFinished.descriptor.parent == buildRunningStarted.descriptor &&
            executingTasksFinished.result instanceof FailureResult &&
            executingTasksFinished.result.startTime == executingTasksGraphStarted.eventTime &&
            executingTasksFinished.result.endTime == executingTasksFinished.eventTime &&
            executingTasksFinished.result.failures.size() == 1
        def buildRunningFinished = result[11]
        buildRunningFinished instanceof FinishEvent &&
            buildRunningFinished.eventTime > 0 &&
            buildRunningFinished.displayName == 'Run build failed' &&
            buildRunningFinished.descriptor.name == 'Run build' &&
            buildRunningFinished.descriptor.displayName == 'Run build' &&
            buildRunningFinished.descriptor.parent == null &&
            buildRunningFinished.result instanceof FailureResult &&
            buildRunningFinished.result.startTime == buildRunningStarted.eventTime &&
            buildRunningFinished.result.endTime == buildRunningFinished.eventTime &&
            buildRunningFinished.result.failures.size() == 1
    }

    @TargetGradleVersion('>=2.5')
    @ToolingApiVersion('>=2.5')
    @NotYetImplemented
    def "should receive build events from GradleBuild"() {
        buildFile << """task innerBuild(type:GradleBuild) {
            buildFile = file('other.gradle')
            tasks = ['innerTask']
        }"""
        file("other.gradle") << """
            task innerTask()
        """

        when:
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('innerBuild').addProgressListener({ ProgressEvent event ->
                result << event
            }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        result.size() % 2 == 0       // same number of start events as finish events
        result.size() == 7 * 2 * 2   // life-cycle for both inner and outer build
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/main/java/example/MyClass.java") << """
            package example;
            public class MyClass {
                public void foo() throws Exception {
                    Thread.sleep(100);
                }
            }
        """
    }

}
