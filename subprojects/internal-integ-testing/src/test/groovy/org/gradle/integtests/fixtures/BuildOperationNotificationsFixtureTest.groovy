/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import spock.lang.Specification
import spock.lang.Unroll

class BuildOperationNotificationsFixtureTest extends Specification {


    @Unroll
    def "listener evaluates build op #notificationMethod notifications (#testedIf.simpleName)"() {
        given:
        def logger = Mock(Logger)
        def listener = listener(logger)

        when:
        listener."$notificationMethod"(notificationEvent)

        then:
        expectedOutputs.each { expectedOutput ->
            1 * logger.info(_) >> {
                assert it[0].trim() == expectedOutput
            }
        }
        _ * logger.info(_)

        where:
        notificationMethod | testedIf       | notificationEvent                    | expectedOutputs
        'started'          | SimpleDetails  | startedNotification(SimpleDetails)   | ["Checking $SimpleDetails.name", "simpleDetailsMethod() -> simpleDetailsValue"]
        'started'          | EmptyDetails   | startedNotification(EmptyDetails)    | ["Checking $EmptyDetails.name"]
        'progress'         | SimpleProgress | progressNotification(SimpleProgress) | ["Checking $SimpleProgress.name", "output() -> output"]
        'finished'         | SimpleResult   | finishedNotification(SimpleResult)   | ["Checking $SimpleResult.name", 'simpleResultMethod() -> simpleResultValue']
        'finished'         | NestedResult   | finishedNotification(NestedResult)   | ["Checking $NestedResult.name", "getNested() -> Nested"]
    }

    @Unroll
    def "listener throws GradleException when no valid object has been found to d"() {
        given:
        def listener = listener(Mock(Logger))

        when:
        listener."$notificationMethod"(notificationEvent)

        then:
        def e = thrown(GradleException)

        e.message == expectedMessage

        where:
        notificationMethod | notificationEvent                 | expectedMessage
        'started'          | startedNotification(SimpleResult) | "No interface with postfix 'Details' found."
        'progress'         | progressNotification()            | "No interface implemented by class java.lang.Object."
        'finished'         | finishedNotification(Nested)      | "No interface with postfix 'Result' found."

    }

    def listener(Logger logger) {
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(BuildOperationNotificationsFixture.getClassLoader())
        Class theParsedClass = groovyClassLoader.parseClass(BuildOperationNotificationsFixture.EVALUATION_LISTENER_SOURCE)
        return theParsedClass.newInstance(logger)
    }

    def progressNotification(Class<SimpleProgress> progressClazz = null) {
        BuildOperationProgressNotification buildOperationProgressNotification = Mock()
        1 * buildOperationProgressNotification.notificationOperationProgressDetails >> (progressClazz == null ? new Object()
            : new Object().withTraits(progressClazz))
        buildOperationProgressNotification
    }

    def finishedNotification(Class<SimpleResult> resultClazz) {
        BuildOperationFinishedNotification buildOperationFinishedNotification = Mock()
        1 * buildOperationFinishedNotification.getNotificationOperationResult() >> new Object().withTraits(resultClazz)
        buildOperationFinishedNotification
    }

    BuildOperationStartedNotification startedNotification(Class<?> detailsClazz) {
        BuildOperationStartedNotification buildOperationStartedNotification = Mock()
        1 * buildOperationStartedNotification.getNotificationOperationDetails() >> new Object().withTraits(detailsClazz)
        buildOperationStartedNotification
    }

    static trait EmptyDetails {}

    static trait SimpleDetails {
        String simpleDetailsMethod() { "simpleDetailsValue" }
    }

    static trait SimpleProgress {
        String output() { "output" }
    }

    static trait SimpleResult {
        String simpleResultMethod() { "simpleResultValue" }
    }

    static trait NestedResult {
        Nested getNested() { new Object().withTraits(Nested) }
    }

    static trait Nested {
        String getNestedValue() { "nestedValue" }

        String toString() { "Nested" }
    }

}
