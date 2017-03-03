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

class DefaultPropertyStateTest extends Specification {

    def taskResolver = Mock(TaskResolver)
    def task1 = Mock(Task)
    def task2 = Mock(Task)

    def "can compare with other instance returning value #value"() {
        given:
        PropertyState<Boolean> propertyState1 = createBooleanPropertyState(true)
        PropertyState<Boolean> propertyState2 = createBooleanPropertyState(value)

        expect:
        (propertyState1 == propertyState2) == equality
        (propertyState1.hashCode() == propertyState2.hashCode()) == hashCode
        (propertyState1.toString() == propertyState2.toString()) == stringRepresentation

        where:
        value | equality | hashCode | stringRepresentation
        true  | true     | true     | true
        false | false    | false    | false
        null  | false    | false    | false
    }

    def "can define build dependencies"() {
        given:
        PropertyState<Boolean> propertyState = createBooleanPropertyState(true)

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

    private PropertyState<Boolean> createBooleanPropertyState(Boolean value) {
        PropertyState<Boolean> propertyState = new DefaultPropertyState<Boolean>(taskResolver)
        propertyState.set(value)
        propertyState
    }
}
