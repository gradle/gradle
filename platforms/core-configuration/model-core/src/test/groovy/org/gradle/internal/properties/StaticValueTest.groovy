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

package org.gradle.internal.properties

import org.gradle.api.Task
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

class StaticValueTest extends Specification {
    def "creates value for Provider with value"() {
        def provider = Mock(ProviderInternal)
        _ * provider.present >> true

        expect:
        def value = StaticValue.of(provider)
        value.call() == provider
        value.taskDependencies == provider
        value.attachProducer(Stub(Task))
        value.maybeFinalizeValue()
    }

    def "creates value for Property instance"() {
        def property = Mock(PropertyInternal)
        def task = Stub(GeneratedTask)
        _ * property.present >> true

        given:
        def value = StaticValue.of(property)
        value.call() == property
        value.taskDependencies == property

        when:
        value.attachProducer(task)

        then:
        1 * property.attachProducer(task)

        when:
        value.maybeFinalizeValue()

        then:
        1 * property.implicitFinalizeValue()
    }

    def "creates value for null value"() {
        expect:
        def value = StaticValue.of(null)
        value.call() == null
        value.taskDependencies == TaskDependencyContainer.EMPTY
        value.attachProducer(Stub(Task))
        value.maybeFinalizeValue()
    }

    def "creates value for other value"() {
        expect:
        def value = StaticValue.of("abc")
        value.call() == "abc"
        value.taskDependencies == TaskDependencyContainer.EMPTY
        value.attachProducer(Stub(Task))
        value.maybeFinalizeValue()

    }

    interface GeneratedTask extends Task, ModelObject {}
}
