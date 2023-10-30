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

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.integtests.tooling.r18.NullAction
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.gradle.BuildInvocations

class ProgressCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "receive progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener(events).get()
        }

        then: "progress events must be forwarded to the attached listeners"
        events.assertIsABuild()
    }

    def "receive progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks('assemble')
                    .addProgressListener(events)
                    .run()
        }

        then: "progress events must be forwarded to the attached listeners"
        events.assertIsABuild()
    }

     def "receive progress events when running a build action"() {
        given:
        goodCode()

        when: "running a build action"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.action(new NullAction()).addProgressListener(events).run()
        }

        then: "progress events must be forwarded to the attached listeners"
        events.assertIsABuild()
    }

    def "register for all progress events at once"() {
        given:
        goodCode()

        when: "registering for all progress event types"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.allOf(OperationType)).run()
        }

        then: "all progress events must be forwarded to the attached listener"
        events.assertHasSingleTree()
        !events.buildOperations.empty
        !events.tests.empty
        !events.tasks.empty
    }

    def "register for subset of progress events"() {
        given:
        goodCode()

        when: "registering for subset of progress event types"
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TEST)).run()
        }

        then: "only the matching progress events must be forwarded to the attached listener"
        !events.tests.empty
        events.operations == events.tests
        events.trees.size() == 1
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
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
