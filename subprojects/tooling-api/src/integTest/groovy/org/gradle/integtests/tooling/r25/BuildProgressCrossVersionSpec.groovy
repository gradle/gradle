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
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.internal.DefaultFinishEvent
import org.gradle.tooling.events.internal.DefaultOperationFailureResult
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult
import org.gradle.tooling.events.internal.DefaultStartEvent
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
        lastEvent instanceof DefaultFinishEvent
        lastEvent.displayName == 'Running build succeeded'
        lastEvent.result instanceof DefaultOperationSuccessResult
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
        result.size() == 7 * 2          // build running, init scripts, settings, loading, configuring, populating task graph, executing tasks
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def buildRunningStarted = result[0]
        buildRunningStarted instanceof DefaultStartEvent &&
            buildRunningStarted.eventTime > 0 &&
            buildRunningStarted.displayName == 'Running build started' &&
            buildRunningStarted.descriptor.name == 'Running build' &&
            buildRunningStarted.descriptor.displayName == 'Running build' &&
            buildRunningStarted.descriptor.parent == null
        def evaluatingInitScriptsStarted = result[1]
        evaluatingInitScriptsStarted instanceof DefaultStartEvent &&
            evaluatingInitScriptsStarted.eventTime > 0 &&
            evaluatingInitScriptsStarted.displayName == 'Evaluating init scripts started' &&
            evaluatingInitScriptsStarted.descriptor.name == 'Evaluating init scripts' &&
            evaluatingInitScriptsStarted.descriptor.displayName == 'Evaluating init scripts' &&
            evaluatingInitScriptsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingInitScriptsFinished = result[2]
        evaluatingInitScriptsFinished instanceof DefaultFinishEvent &&
            evaluatingInitScriptsFinished.eventTime > 0 &&
            evaluatingInitScriptsFinished.displayName == 'Evaluating init scripts succeeded' &&
            evaluatingInitScriptsFinished.descriptor.name == 'Evaluating init scripts' &&
            evaluatingInitScriptsFinished.descriptor.displayName == 'Evaluating init scripts' &&
            evaluatingInitScriptsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingInitScriptsFinished.result instanceof DefaultOperationSuccessResult &&
            evaluatingInitScriptsFinished.result.startTime == evaluatingInitScriptsStarted.eventTime &&
            evaluatingInitScriptsFinished.result.endTime == evaluatingInitScriptsFinished.eventTime
        def evaluatingSettingsStarted = result[3]
        evaluatingSettingsStarted instanceof DefaultStartEvent &&
            evaluatingSettingsStarted.eventTime > 0 &&
            evaluatingSettingsStarted.displayName == 'Evaluating settings started' &&
            evaluatingSettingsStarted.descriptor.name == 'Evaluating settings' &&
            evaluatingSettingsStarted.descriptor.displayName == 'Evaluating settings' &&
            evaluatingSettingsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingSettingsFinished = result[4]
        evaluatingSettingsFinished instanceof DefaultFinishEvent &&
            evaluatingSettingsFinished.eventTime > 0 &&
            evaluatingSettingsFinished.displayName == 'Evaluating settings succeeded' &&
            evaluatingSettingsFinished.descriptor.name == 'Evaluating settings' &&
            evaluatingSettingsFinished.descriptor.displayName == 'Evaluating settings' &&
            evaluatingSettingsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingSettingsFinished.result instanceof DefaultOperationSuccessResult &&
            evaluatingSettingsFinished.result.startTime == evaluatingSettingsStarted.eventTime &&
            evaluatingSettingsFinished.result.endTime == evaluatingSettingsFinished.eventTime
        def loadingBuildStarted = result[5]
        loadingBuildStarted instanceof DefaultStartEvent &&
            loadingBuildStarted.eventTime > 0 &&
            loadingBuildStarted.displayName == 'Loading build started' &&
            loadingBuildStarted.descriptor.name == 'Loading build' &&
            loadingBuildStarted.descriptor.displayName == 'Loading build' &&
            loadingBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def loadingBuildFinished = result[6]
        loadingBuildFinished instanceof DefaultFinishEvent &&
            loadingBuildFinished.eventTime > 0 &&
            loadingBuildFinished.displayName == 'Loading build succeeded' &&
            loadingBuildFinished.descriptor.name == 'Loading build' &&
            loadingBuildFinished.descriptor.displayName == 'Loading build' &&
            loadingBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            loadingBuildFinished.result instanceof DefaultOperationSuccessResult &&
            loadingBuildFinished.result.startTime == loadingBuildStarted.eventTime &&
            loadingBuildFinished.result.endTime == loadingBuildFinished.eventTime
        def configuringBuildStarted = result[7]
        configuringBuildStarted instanceof DefaultStartEvent &&
            configuringBuildStarted.eventTime > 0 &&
            configuringBuildStarted.displayName == 'Configuring build started' &&
            configuringBuildStarted.descriptor.name == 'Configuring build' &&
            configuringBuildStarted.descriptor.displayName == 'Configuring build' &&
            configuringBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def configuringBuildFinished = result[8]
        configuringBuildFinished instanceof DefaultFinishEvent &&
            configuringBuildFinished.eventTime > 0 &&
            configuringBuildFinished.displayName == 'Configuring build succeeded' &&
            configuringBuildFinished.descriptor.name == 'Configuring build' &&
            configuringBuildFinished.descriptor.displayName == 'Configuring build' &&
            configuringBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            configuringBuildFinished.result instanceof DefaultOperationSuccessResult &&
            configuringBuildFinished.result.startTime == configuringBuildStarted.eventTime &&
            configuringBuildFinished.result.endTime == configuringBuildFinished.eventTime
        def populatingTaskGraphStarted = result[9]
        populatingTaskGraphStarted instanceof DefaultStartEvent &&
            populatingTaskGraphStarted.eventTime > 0 &&
            populatingTaskGraphStarted.displayName == 'Populating task graph started' &&
            populatingTaskGraphStarted.descriptor.name == 'Populating task graph' &&
            populatingTaskGraphStarted.descriptor.displayName == 'Populating task graph' &&
            populatingTaskGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def populatingTaskGraphFinished = result[10]
        populatingTaskGraphFinished instanceof DefaultFinishEvent &&
            populatingTaskGraphFinished.eventTime > 0 &&
            populatingTaskGraphFinished.displayName == 'Populating task graph succeeded' &&
            populatingTaskGraphFinished.descriptor.name == 'Populating task graph' &&
            populatingTaskGraphFinished.descriptor.displayName == 'Populating task graph' &&
            populatingTaskGraphFinished.descriptor.parent == buildRunningStarted.descriptor &&
            populatingTaskGraphFinished.result instanceof DefaultOperationSuccessResult &&
            populatingTaskGraphFinished.result.startTime == populatingTaskGraphStarted.eventTime &&
            populatingTaskGraphFinished.result.endTime == populatingTaskGraphFinished.eventTime
        def executingTasksGraphStarted = result[11]
        executingTasksGraphStarted instanceof DefaultStartEvent &&
            executingTasksGraphStarted.eventTime > 0 &&
            executingTasksGraphStarted.displayName == 'Executing tasks started' &&
            executingTasksGraphStarted.descriptor.name == 'Executing tasks' &&
            executingTasksGraphStarted.descriptor.displayName == 'Executing tasks' &&
            executingTasksGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def executingTasksFinished = result[12]
        executingTasksFinished instanceof DefaultFinishEvent &&
            executingTasksFinished.eventTime > 0 &&
            executingTasksFinished.displayName == 'Executing tasks succeeded' &&
            executingTasksFinished.descriptor.name == 'Executing tasks' &&
            executingTasksFinished.descriptor.displayName == 'Executing tasks' &&
            executingTasksFinished.descriptor.parent == buildRunningStarted.descriptor &&
            executingTasksFinished.result instanceof DefaultOperationSuccessResult &&
            executingTasksFinished.result.startTime == executingTasksGraphStarted.eventTime &&
            executingTasksFinished.result.endTime == executingTasksFinished.eventTime
        def buildRunningFinished = result[13]
        buildRunningFinished instanceof DefaultFinishEvent &&
            buildRunningFinished.eventTime > 0 &&
            buildRunningFinished.displayName == 'Running build succeeded' &&
            buildRunningFinished.descriptor.name == 'Running build' &&
            buildRunningFinished.descriptor.displayName == 'Running build' &&
            buildRunningFinished.descriptor.parent == null &&
            buildRunningFinished.result instanceof DefaultOperationSuccessResult &&
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
        result.size() == 7 * 2          // build running, init scripts, settings, loading, configuring, populating task graph, executing tasks
        result.each {
            assert it.displayName == it.toString()
            assert it.descriptor.displayName == it.descriptor.toString()
        }

        def buildRunningStarted = result[0]
        buildRunningStarted instanceof DefaultStartEvent &&
            buildRunningStarted.eventTime > 0 &&
            buildRunningStarted.displayName == 'Running build started' &&
            buildRunningStarted.descriptor.name == 'Running build' &&
            buildRunningStarted.descriptor.displayName == 'Running build' &&
            buildRunningStarted.descriptor.parent == null
        def evaluatingInitScriptsStarted = result[1]
        evaluatingInitScriptsStarted instanceof DefaultStartEvent &&
            evaluatingInitScriptsStarted.eventTime > 0 &&
            evaluatingInitScriptsStarted.displayName == 'Evaluating init scripts started' &&
            evaluatingInitScriptsStarted.descriptor.name == 'Evaluating init scripts' &&
            evaluatingInitScriptsStarted.descriptor.displayName == 'Evaluating init scripts' &&
            evaluatingInitScriptsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingInitScriptsFinished = result[2]
        evaluatingInitScriptsFinished instanceof DefaultFinishEvent &&
            evaluatingInitScriptsFinished.eventTime > 0 &&
            evaluatingInitScriptsFinished.displayName == 'Evaluating init scripts succeeded' &&
            evaluatingInitScriptsFinished.descriptor.name == 'Evaluating init scripts' &&
            evaluatingInitScriptsFinished.descriptor.displayName == 'Evaluating init scripts' &&
            evaluatingInitScriptsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingInitScriptsFinished.result instanceof DefaultOperationSuccessResult &&
            evaluatingInitScriptsFinished.result.startTime == evaluatingInitScriptsStarted.eventTime &&
            evaluatingInitScriptsFinished.result.endTime == evaluatingInitScriptsFinished.eventTime
        def evaluatingSettingsStarted = result[3]
        evaluatingSettingsStarted instanceof DefaultStartEvent &&
            evaluatingSettingsStarted.eventTime > 0 &&
            evaluatingSettingsStarted.displayName == 'Evaluating settings started' &&
            evaluatingSettingsStarted.descriptor.name == 'Evaluating settings' &&
            evaluatingSettingsStarted.descriptor.displayName == 'Evaluating settings' &&
            evaluatingSettingsStarted.descriptor.parent == buildRunningStarted.descriptor
        def evaluatingSettingsFinished = result[4]
        evaluatingSettingsFinished instanceof DefaultFinishEvent &&
            evaluatingSettingsFinished.eventTime > 0 &&
            evaluatingSettingsFinished.displayName == 'Evaluating settings succeeded' &&
            evaluatingSettingsFinished.descriptor.name == 'Evaluating settings' &&
            evaluatingSettingsFinished.descriptor.displayName == 'Evaluating settings' &&
            evaluatingSettingsFinished.descriptor.parent == buildRunningStarted.descriptor &&
            evaluatingSettingsFinished.result instanceof DefaultOperationSuccessResult &&
            evaluatingSettingsFinished.result.startTime == evaluatingSettingsStarted.eventTime &&
            evaluatingSettingsFinished.result.endTime == evaluatingSettingsFinished.eventTime
        def loadingBuildStarted = result[5]
        loadingBuildStarted instanceof DefaultStartEvent &&
            loadingBuildStarted.eventTime > 0 &&
            loadingBuildStarted.displayName == 'Loading build started' &&
            loadingBuildStarted.descriptor.name == 'Loading build' &&
            loadingBuildStarted.descriptor.displayName == 'Loading build' &&
            loadingBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def loadingBuildFinished = result[6]
        loadingBuildFinished instanceof DefaultFinishEvent &&
            loadingBuildFinished.eventTime > 0 &&
            loadingBuildFinished.displayName == 'Loading build succeeded' &&
            loadingBuildFinished.descriptor.name == 'Loading build' &&
            loadingBuildFinished.descriptor.displayName == 'Loading build' &&
            loadingBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            loadingBuildFinished.result instanceof DefaultOperationSuccessResult &&
            loadingBuildFinished.result.startTime == loadingBuildStarted.eventTime &&
            loadingBuildFinished.result.endTime == loadingBuildFinished.eventTime
        def configuringBuildStarted = result[7]
        configuringBuildStarted instanceof DefaultStartEvent &&
            configuringBuildStarted.eventTime > 0 &&
            configuringBuildStarted.displayName == 'Configuring build started' &&
            configuringBuildStarted.descriptor.name == 'Configuring build' &&
            configuringBuildStarted.descriptor.displayName == 'Configuring build' &&
            configuringBuildStarted.descriptor.parent == buildRunningStarted.descriptor
        def configuringBuildFinished = result[8]
        configuringBuildFinished instanceof DefaultFinishEvent &&
            configuringBuildFinished.eventTime > 0 &&
            configuringBuildFinished.displayName == 'Configuring build succeeded' &&
            configuringBuildFinished.descriptor.name == 'Configuring build' &&
            configuringBuildFinished.descriptor.displayName == 'Configuring build' &&
            configuringBuildFinished.descriptor.parent == buildRunningStarted.descriptor &&
            configuringBuildFinished.result instanceof DefaultOperationSuccessResult &&
            configuringBuildFinished.result.startTime == configuringBuildStarted.eventTime &&
            configuringBuildFinished.result.endTime == configuringBuildFinished.eventTime
        def populatingTaskGraphStarted = result[9]
        populatingTaskGraphStarted instanceof DefaultStartEvent &&
            populatingTaskGraphStarted.eventTime > 0 &&
            populatingTaskGraphStarted.displayName == 'Populating task graph started' &&
            populatingTaskGraphStarted.descriptor.name == 'Populating task graph' &&
            populatingTaskGraphStarted.descriptor.displayName == 'Populating task graph' &&
            populatingTaskGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def populatingTaskGraphFinished = result[10]
        populatingTaskGraphFinished instanceof DefaultFinishEvent &&
            populatingTaskGraphFinished.eventTime > 0 &&
            populatingTaskGraphFinished.displayName == 'Populating task graph succeeded' &&
            populatingTaskGraphFinished.descriptor.name == 'Populating task graph' &&
            populatingTaskGraphFinished.descriptor.displayName == 'Populating task graph' &&
            populatingTaskGraphFinished.descriptor.parent == buildRunningStarted.descriptor &&
            populatingTaskGraphFinished.result instanceof DefaultOperationSuccessResult &&
            populatingTaskGraphFinished.result.startTime == populatingTaskGraphStarted.eventTime &&
            populatingTaskGraphFinished.result.endTime == populatingTaskGraphFinished.eventTime
        def executingTasksGraphStarted = result[11]
        executingTasksGraphStarted instanceof DefaultStartEvent &&
            executingTasksGraphStarted.eventTime > 0 &&
            executingTasksGraphStarted.displayName == 'Executing tasks started' &&
            executingTasksGraphStarted.descriptor.name == 'Executing tasks' &&
            executingTasksGraphStarted.descriptor.displayName == 'Executing tasks' &&
            executingTasksGraphStarted.descriptor.parent == buildRunningStarted.descriptor
        def executingTasksFinished = result[12]
        executingTasksFinished instanceof DefaultFinishEvent &&
            executingTasksFinished.eventTime > 0 &&
            executingTasksFinished.displayName == 'Executing tasks failed' &&
            executingTasksFinished.descriptor.name == 'Executing tasks' &&
            executingTasksFinished.descriptor.displayName == 'Executing tasks' &&
            executingTasksFinished.descriptor.parent == buildRunningStarted.descriptor &&
            executingTasksFinished.result instanceof DefaultOperationFailureResult &&
            executingTasksFinished.result.startTime == executingTasksGraphStarted.eventTime &&
            executingTasksFinished.result.endTime == executingTasksFinished.eventTime &&
            executingTasksFinished.result.failures.size() == 1
        def buildRunningFinished = result[13]
        buildRunningFinished instanceof DefaultFinishEvent &&
            buildRunningFinished.eventTime > 0 &&
            buildRunningFinished.displayName == 'Running build failed' &&
            buildRunningFinished.descriptor.name == 'Running build' &&
            buildRunningFinished.descriptor.displayName == 'Running build' &&
            buildRunningFinished.descriptor.parent == null &&
            buildRunningFinished.result instanceof DefaultOperationFailureResult &&
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
        result.size() == 7 * 2 * 2   // life-cycle for both inner and outter build
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
