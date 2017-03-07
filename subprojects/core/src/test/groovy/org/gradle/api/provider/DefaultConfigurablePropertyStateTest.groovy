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
import org.gradle.api.internal.provider.DefaultConfigurablePropertyState
import org.gradle.api.internal.tasks.TaskResolver
import spock.lang.Specification
import spock.lang.Unroll

class DefaultConfigurablePropertyStateTest extends Specification {

    def taskResolver = Mock(TaskResolver)
    def task1 = Mock(Task)
    def task2 = Mock(Task)

    @Unroll
    def "can compare string representation with other instance returning value #value"() {
        given:
        ConfigurablePropertyState<Boolean> propertyState1 = createBooleanPropertyState(true)
        ConfigurablePropertyState<Boolean> propertyState2 = createBooleanPropertyState(value)

        expect:
        (propertyState1.toString() == propertyState2.toString()) == stringRepresentation

        where:
        value | stringRepresentation
        true  | true
        false | false
        null  | false
    }

    def "can define build dependencies"() {
        given:
        ConfigurablePropertyState<Boolean> propertyState = createBooleanPropertyState(true)

        expect:
        propertyState
        propertyState.buildDependencies
        propertyState.builtBy.empty

        when:
        propertyState.builtBy(task1)

        then:
        propertyState.builtBy.size() == 1
        propertyState.builtBy == [task1] as Set

        when:
        propertyState.builtBy(task1, task2)

        then:
        propertyState.builtBy.size() == 2
        propertyState.builtBy == [task1, task2] as Set
    }

    def "throws exception if null value is retrieved"() {
        when:
        ConfigurablePropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == DefaultConfigurablePropertyState.NON_NULL_VALUE_EXCEPTION_MESSAGE

        when:
        propertyState = createBooleanPropertyState(null)
        propertyState.get()

        then:
        t = thrown(IllegalStateException)
        t.message == DefaultConfigurablePropertyState.NON_NULL_VALUE_EXCEPTION_MESSAGE
    }

    private ConfigurablePropertyState<Boolean> createBooleanPropertyState() {
        new DefaultConfigurablePropertyState<Boolean>(taskResolver)
    }

    private ConfigurablePropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = createBooleanPropertyState()
        propertyState.set(value)
        propertyState
    }
}
