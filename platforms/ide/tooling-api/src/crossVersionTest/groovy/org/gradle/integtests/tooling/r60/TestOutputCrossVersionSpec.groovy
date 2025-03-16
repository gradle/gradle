/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r60

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.JvmTestOperationDescriptor
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOutputEvent

@TargetGradleVersion('>=6.0')
class TestOutputCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "test execution exposes test output"() {
        when:
        runTestAndCollectProgressEvents()
        def events = getOutputEvents()

        then:
        events.find { TestOutputEvent event -> event.descriptor.message == "out1" && event.descriptor.destination == Destination.StdOut }
        events.find { TestOutputEvent event -> event.descriptor.message == "err1" && event.descriptor.destination == Destination.StdErr }
        events.find { TestOutputEvent event -> event.descriptor.message == "out2" && event.descriptor.destination == Destination.StdOut }
        events.find { TestOutputEvent event -> event.descriptor.message == "err2" && event.descriptor.destination == Destination.StdErr }
    }

    def "output events have correct parent test events"() {
        when:
        runTestAndCollectProgressEvents()

        TestFinishEvent testEvent = testEvents.find { TestFinishEvent e -> e.descriptor instanceof JvmTestOperationDescriptor && e.descriptor.methodName == 'works'}
        TestOutputEvent outputEvent = outputEvents.find { TestOutputEvent event -> event.descriptor.message == "out1" && event.descriptor.destination == Destination.StdOut }

        then:
        testEvent.descriptor == outputEvent.descriptor.parent
    }

    def progressEvents = []

    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies.${testImplementationConfiguration} 'junit:junit:4.13'
            test.ignoreFailures = true
        """
        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void works() throws Exception {
                    System.out.print("out1");
                    System.err.print("err1");
                }
                @org.junit.Test public void fails() throws Exception {
                    System.out.print("out2");
                    System.err.print("err2");
                    org.junit.Assert.fail();
                }
            }
        """
    }

    def runTestAndCollectProgressEvents() {
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addProgressListener({ event -> progressEvents << event } as org.gradle.tooling.events.ProgressListener).run()
        }
    }

    def getTestEvents() {
        getProgressEvents(TestFinishEvent)
    }

    def getOutputEvents() {
        getProgressEvents(TestOutputEvent)
    }

    def getProgressEvents(Class type) {
        progressEvents.findAll { type.isInstance(it) }
    }
}
