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
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.build.*
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
                connection.newBuild().forTasks('test').addBuildProgressListener { throw new RuntimeException() }.run()
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
        List<BuildProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('test').addBuildProgressListener { BuildProgressEvent event ->
                    result << event
                }.get()
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
        List<BuildProgressEvent> result = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addBuildProgressListener { BuildProgressEvent event ->
                    result << event
                }.run()
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
                connection.newBuild().forTasks('test').addBuildProgressListener { BuildProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }.run()
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
        List<BuildProgressEvent> resultsOfFirstListener = new ArrayList<BuildProgressEvent>()
        List<BuildProgressEvent> resultsOfLastListener = new ArrayList<BuildProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addBuildProgressListener { BuildProgressEvent event ->
                    resultsOfFirstListener.add(event)
                }.addBuildProgressListener { BuildProgressEvent event ->
                    throw new IllegalStateException("Throwing an exception on purpose")
                }.addBuildProgressListener { BuildProgressEvent event ->
                    resultsOfLastListener.add(event)
                }.run()
        }

        then: "current build progress event must still be forwarded to the attached listeners even if one of the listeners throws an exception"
        thrown(GradleConnectionException)
        resultsOfFirstListener.size() > 0
        resultsOfLastListener.size() > 0
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive all possible build progress events types for successful run"() {
        given:
        goodCode()
        BuildProgressListener listener = Mock()
        BuildOperationDescriptor parentDescriptor

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addBuildProgressListener(listener).run()
        }

        then:
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            // build start
            assert event.displayName == 'build started'
            parentDescriptor = event.descriptor
        }

        // settings evaluated
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            assert event.displayName == 'settings evaluation started'
            assert event.descriptor.name == 'settings evaluation'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
//            assert event.displayName == 'settings evaluation finished'
            assert event.descriptor.name == 'settings evaluation'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // projects loaded
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
//            assert event.displayName == 'projects loading started'
            assert event.descriptor.name == 'projects loading'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
//            assert event.displayName == 'projects loading finished'
            assert event.descriptor.name == 'projects loading'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // projects evaluated
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
//            assert event.displayName == 'projects evaluation started'
            assert event.descriptor.name == 'projects evaluation'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
//            assert event.displayName == 'projects evaluation finished'
            assert event.descriptor.name == 'projects evaluation'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // build finish
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
            assert event.displayName == 'build finished with success'
            assert event.descriptor.is(parentDescriptor)
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

        BuildProgressListener listener = Mock()
        BuildOperationDescriptor parentDescriptor

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addBuildProgressListener(listener).run()
        }

        then:
        BuildException ex = thrown()
        then:
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            // build start
            assert event.displayName == 'build started'
            parentDescriptor = event.descriptor
        }

        // settings evaluated
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            assert event.displayName == 'settings evaluation started'
            assert event.descriptor.displayName == 'settings evaluation'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
            assert event.displayName == 'settings evaluation finished'
            assert event.descriptor.displayName == 'settings evaluation'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // projects loaded
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            assert event.displayName == 'projects loading started'
            assert event.descriptor.displayName == 'projects loading'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
            assert event.displayName == 'projects loading finished'
            assert event.descriptor.displayName == 'projects loading'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // projects evaluated
        1 * listener.statusChanged(_ as BuildStartEvent) >> { BuildStartEvent event ->
            assert event.displayName == 'projects evaluation started'
            assert event.descriptor.displayName == 'projects evaluation'
            assert event.descriptor.parent == parentDescriptor
        }
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
            assert event.displayName == 'projects evaluation finished'
            assert event.descriptor.displayName == 'projects evaluation'
            assert event.descriptor.parent == parentDescriptor
            def result = event.result
            assert result instanceof BuildSuccessResult
        }

        // build finish
        1 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
            assert event.displayName == 'build finished with failure'
            assert event.descriptor.is(parentDescriptor)
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

        BuildProgressListener listener = Mock()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('innerBuild').addBuildProgressListener(listener).run()
        }

        then:
        2 * listener.statusChanged(_ as BuildStartEvent)
        6 * listener.statusChanged(_ as BuildStartEvent) // settings loaded, projects loaded, projects evaluated
        2 * listener.statusChanged(_ as BuildFinishEvent) >> { BuildFinishEvent event ->
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
