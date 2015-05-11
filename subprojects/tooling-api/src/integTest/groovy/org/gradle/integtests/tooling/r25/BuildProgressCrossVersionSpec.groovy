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
import org.gradle.tooling.events.ProgressEventType
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.internal.build.*
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
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new RuntimeException()
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).run()
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
                connection.model(BuildInvocations).forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).get()
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
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).run()
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
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).run()
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
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfFirstListener.add(event)
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        throw new IllegalStateException("Throwing an exception on purpose")
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        resultsOfLastListener.add(event)
                    }
                }, EnumSet.of(ProgressEventType.BUILD)).run()
        }

        then: "current build progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        ListenerFailedException ex = thrown()
        resultsOfFirstListener.size() > 0
        resultsOfLastListener.size() > 0
        ex.causes.size() == resultsOfLastListener.size()

        and: "build is successful"
        def lastEvent = resultsOfLastListener[-1]
        lastEvent.displayName == 'Running build succeeded'
        lastEvent instanceof BuildOperationFinishEvent
        def result = lastEvent.result
        result instanceof BuildSuccessResult
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive all possible build progress events types for successful run"() {
        given:
        goodCode()
        ProgressListener listener = Mock(ProgressListener)
        BuildOperationDescriptor buildDescriptor
        BuildOperationDescriptor configDescriptor

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(listener, EnumSet.of(ProgressEventType.BUILD)).run()
        }

        then:
        // running build started
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Running build started'
            buildDescriptor = event.descriptor
        }

        // evaluating init scripts
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Evaluating init scripts started'
            assert event.descriptor.name == 'Evaluating init scripts'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Evaluating init scripts succeeded'
            assert event.descriptor.name == 'Evaluating init scripts'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // evaluating settings
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Evaluating settings started'
            assert event.descriptor.name == 'Evaluating settings'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.descriptor.name == 'Evaluating settings'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // loading build
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.descriptor.name == 'Loading build'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.descriptor.name == 'Loading build'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // configuring build
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Configuring build started'
            assert event.descriptor.name == 'Configuring build'
            assert event.descriptor.parent == buildDescriptor
            configDescriptor = event.descriptor
        }

        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Configuring build succeeded'
            assert event.descriptor.name == 'Configuring build'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // populating task graph
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Populating task graph started'
            assert event.descriptor.name == 'Populating task graph'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Populating task graph succeeded'
            assert event.descriptor.name == 'Populating task graph'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // executing tasks
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Executing tasks started'
            assert event.descriptor.name == 'Executing tasks'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Executing tasks succeeded'
            assert event.descriptor.name == 'Executing tasks'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // running build finished
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Running build succeeded'
            assert event.descriptor.is(buildDescriptor)
            def result = event.result
            assert result instanceof BuildSuccessResult
        }
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for failed test run"() {
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

        ProgressListener listener = Mock(ProgressListener)
        BuildOperationDescriptor buildDescriptor
        BuildOperationDescriptor configDescriptor

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(listener, EnumSet.of(ProgressEventType.BUILD)).run()
        }

        then:
        thrown(BuildException)

        then:
        // running build started
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Running build started'
            buildDescriptor = event.descriptor
        }

        // evaluating init scripts
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'init scripts evaluation started'
            assert event.descriptor.name == 'init scripts evaluation'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'init scripts evaluation succeeded'
            assert event.descriptor.name == 'init scripts evaluation'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // evaluating settings
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Evaluating settings started'
            assert event.descriptor.name == 'Evaluating settings'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.descriptor.name == 'Evaluating settings'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // loading build
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.descriptor.name == 'Loading build'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.descriptor.name == 'Loading build'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // configuring build
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Configuring build started'
            assert event.descriptor.name == 'Configuring build'
            assert event.descriptor.parent == buildDescriptor
            configDescriptor = event.descriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Configuring build succeeded'
            assert event.descriptor.name == 'Configuring build'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // populating task graph
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Populating task graph started'
            assert event.descriptor.name == 'Populating task graph'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Populating task graph succeeded'
            assert event.descriptor.name == 'Populating task graph'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // executing tasks
        1 * listener.statusChanged(_ as BuildOperationStartEvent) >> { BuildOperationStartEvent event ->
            assert event.displayName == 'Executing tasks started'
            assert event.descriptor.name == 'Executing tasks'
            assert event.descriptor.parent == buildDescriptor
        }
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Executing tasks failed'
            assert event.descriptor.name == 'Executing tasks'
            assert event.descriptor.parent == buildDescriptor
            def result = event.result
            assert result instanceof BuildFailureResult
            assert result.failures.size() == 1
        }

        // running build finished
        1 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            assert event.displayName == 'Running build failed'
            assert event.descriptor.is(buildDescriptor)
            def result = event.result
            assert result instanceof BuildFailureResult
            assert result.failures.size() == 1
        }
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

        ProgressListener listener = Mock(ProgressListener)

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('innerBuild').addProgressListener(listener, EnumSet.of(ProgressEventType.BUILD)).run()
        }

        then:
        2 * listener.statusChanged(_ as BuildOperationStartEvent)
        6 * listener.statusChanged(_ as BuildOperationStartEvent) // settings loaded, projects loaded, projects evaluated
        2 * listener.statusChanged(_ as BuildOperationFinishEvent) >> { BuildOperationFinishEvent event ->
            def result = event.result
            assert result instanceof BuildSuccessResult
        }
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }


}
