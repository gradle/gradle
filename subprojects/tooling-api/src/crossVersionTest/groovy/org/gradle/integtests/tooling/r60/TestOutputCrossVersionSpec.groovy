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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.events.test.TestOutputEvent


class TestOutputCrossVersionSpec extends BaseTestOutputSpec {

    @ToolingApiVersion('>=6.0')
    @TargetGradleVersion('>=6.0')
    def "test execution exposes test output"() {
        when:
        runTestAndCollectProgressEvents()
        progressEvents.each { println it }
        def events = getOutputEvents()

        then:
        events.find { TestOutputEvent event -> event.message == "out1" && event.destination == Destination.StdOut }
        events.find { TestOutputEvent event -> event.message == "err1" && event.destination == Destination.StdErr }
        events.find { TestOutputEvent event -> event.message == "out2" && event.destination == Destination.StdOut }
    }

//    def "output events have correct parent test events"() {
//        when:
//        runTestAndCollectProgressEvents()
//
//
//    }

    def runTestAndCollectProgressEvents() {
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addProgressListener({ event -> progressEvents << event } as org.gradle.tooling.events.ProgressListener).run()
        }
    }

    def getOutputEvents() {
        getProgressEvents(TestOutputEvent)
    }

    def getProgressEvents(Class type) {
        progressEvents.findAll { type.isInstance(it) }
    }
}
