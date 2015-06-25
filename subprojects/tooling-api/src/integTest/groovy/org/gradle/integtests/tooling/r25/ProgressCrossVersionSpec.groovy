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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.internal.DefaultFinishEvent
import org.gradle.tooling.events.internal.DefaultStartEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.model.gradle.BuildInvocations

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=2.5")
class ProgressCrossVersionSpec extends ToolingApiSpecification {

    def "receive progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }).get()
        }

        then: "progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    def "receive progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }).run()
        }

        then: "progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    def "receive progress events when running a build action"() {
        given:
        goodCode()

        when: "running a build action"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.action(new NullAction()).addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }).run()
        }

        then: "progress events must be forwarded to the attached listeners"
        result.size() > 0
    }

    def "register for all progress events at once"() {
        given:
        goodCode()

        when: "registering for all progress event types"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.allOf(OperationType)).run()
        }

        then: "all progress events must be forwarded to the attached listener"
        result.size() > 0
        result.findAll { it instanceof TestProgressEvent }.size() > 0
        result.findAll { it instanceof TaskProgressEvent }.size() > 0
        result.findAll { it.class == DefaultStartEvent || it.class == DefaultFinishEvent }.size() > 0
        result.findIndexOf { it.class == DefaultStartEvent } < result.findIndexOf { it instanceof TaskStartEvent }
        result.findIndexOf { it instanceof TaskStartEvent } < result.findIndexOf { it instanceof TestStartEvent }
        result.findLastIndexOf { it instanceof TaskFinishEvent } > result.findLastIndexOf { it instanceof TestFinishEvent }
        result.findLastIndexOf { it.class == DefaultFinishEvent } > result.findLastIndexOf { it instanceof TaskFinishEvent }
    }

    def "register for subset of progress events at once"() {
        given:
        goodCode()

        when: "registering for subset of progress event types"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "only the matching progress events must be forwarded to the attached listener"
        result.size() > 0
        result.findAll { it instanceof TestProgressEvent }.size() > 0
        result.findAll { it instanceof TaskProgressEvent }.isEmpty()
        result.findAll { it.class == DefaultStartEvent || it.class == DefaultFinishEvent }.isEmpty()
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion("=2.4")
    def "register for all progress events when provider version only knows how to send test progress events"() {
        given:
        goodCode()

        when: "registering for all progress event types but provider only knows how to send test progress events"
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.allOf(OperationType)).run()
        }

        then: "only test progress events must be forwarded to the attached listener"
        result.size() > 0
        result.findAll { it instanceof TestProgressEvent }.size() > 0
        result.findAll { it instanceof TaskProgressEvent }.isEmpty()
        result.findAll { it.class == DefaultStartEvent || it.class == DefaultFinishEvent }.isEmpty()
    }

    def "when listening to all progress events they are all in a hierarchy with a single root node"() {
        given:
        goodCode()

        when: 'listening to progress events'
        List<ProgressEvent> result = new ArrayList<ProgressEvent>()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('build').addProgressListener(new ProgressListener() {
                    @Override
                    void statusChanged(ProgressEvent event) {
                        result << event
                    }
                }, EnumSet.allOf(OperationType.class)).run()
        }

        then: 'all events are in a hierarchy with a single root node'
        !result.isEmpty()
        def rootNodes = result.findAll { it.descriptor.parent == null }
        rootNodes.size() == 2
        rootNodes.each { it.class == DefaultStartEvent || it.class == DefaultFinishEvent }
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true
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
