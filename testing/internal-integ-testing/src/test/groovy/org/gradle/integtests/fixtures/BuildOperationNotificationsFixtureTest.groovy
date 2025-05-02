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
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.internal.reflect.JavaReflectionUtil
import spock.lang.Specification

class BuildOperationNotificationsFixtureTest extends Specification {

    def "listener evaluates build op #notificationMethod notifications (#testedIf.simpleName)"() {
        given:
        def listener = listener()

        when:
        listener."$notificationMethod"(notificationEvent)

        then:
        noExceptionThrown()

        where:
        notificationMethod | testedIf       | notificationEvent
        'started'          | SimpleDetails  | startedNotification(SimpleDetails)
        'started'          | EmptyDetails   | startedNotification(EmptyDetails)
        'progress'         | SimpleProgress | progressNotification(SimpleProgress)
        'finished'         | SimpleResult   | finishedNotification(SimpleResult)
        'finished'         | NestedResult   | finishedNotification(NestedResult)
    }

    def "throws if public type cannot be determined"() {
        given:
        def listener = listener()

        when:
        listener."$notificationMethod"(notificationEvent)

        then:
        def e = thrown(GradleException)
        e.message == expectedMessage

        where:
        notificationMethod | notificationEvent                 | expectedMessage
        'started'          | startedNotification(SimpleResult) | "No interface with suffix 'Details' found."
        'progress'         | progressNotification()            | "No interface implemented by class java.lang.Object."
        'finished'         | finishedNotification(Nested)      | "No interface with suffix 'Result' found."
    }

    def "throws if method of type throws"() {
        given:
        def listener = listener()

        when:
        listener.started(startedNotification(ErroringDetails))

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to invoke erroring() of $ErroringDetails.name"
        e.cause instanceof RuntimeException
        e.cause.message == "!"
    }

    BuildOperationNotificationListener listener() {
        GroovyClassLoader groovyClassLoader = new GroovyClassLoader(BuildOperationNotificationsFixture.getClassLoader())
        Class theParsedClass = groovyClassLoader.parseClass(BuildOperationNotificationsFixture.EVALUATION_LISTENER_SOURCE)
        return JavaReflectionUtil.newInstance(theParsedClass) as BuildOperationNotificationListener
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

    static trait ErroringDetails {
        String erroring() { throw new RuntimeException("!") }
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
