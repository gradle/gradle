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

package org.gradle.api.provider

import org.gradle.api.Task
import org.gradle.api.internal.provider.DefaultPropertyState
import org.gradle.api.internal.tasks.TaskResolver
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPropertyStateTest extends Specification {

    def taskResolver = Mock(TaskResolver)
    def task1 = Mock(Task)
    def task2 = Mock(Task)

    @Unroll
    def "can compare string representation with other instance returning value #value"() {
        given:
        boolean immutablePropertyStateValue1 = true
        PropertyState<Boolean> propertyState1 = createBooleanPropertyState(immutablePropertyStateValue1)
        PropertyState<Boolean> propertyState2 = createBooleanPropertyState(value)

        expect:
        (propertyState1.toString() == propertyState2.toString()) == stringRepresentation
        propertyState1.toString() == "value: $immutablePropertyStateValue1"
        propertyState2.toString() == "value: $value"

        where:
        value | stringRepresentation
        true  | true
        false | false
        null  | false
    }

    def "throws exception if null value is retrieved for non-null get method"() {
        when:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == DefaultPropertyState.NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState = createBooleanPropertyState(null)
        propertyState.get()

        then:
        t = thrown(IllegalStateException)
        t.message == DefaultPropertyState.NON_NULL_VALUE_EXCEPTION_MESSAGE
    }

    def "returns value or null for get or null method"() {
        when:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()

        then:
        propertyState.getOrNull() == null

        when:
        propertyState.set(true)

        then:
        propertyState.getOrNull()
    }

    def "can set value of external provider"() {
        def provider = Mock(Provider)

        given:
        PropertyState<Boolean> propertyState = createBooleanPropertyState()

        expect:
        propertyState.getOrNull() == null

        when:
        propertyState.set(provider)

        then:
        1 * provider.getOrNull() >> true
        propertyState.getOrNull() == true
    }

    private PropertyState<Boolean> createBooleanPropertyState() {
        new DefaultPropertyState<Boolean>(taskResolver)
    }

    private PropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(value)
        propertyState
    }
}
