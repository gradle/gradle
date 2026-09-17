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

package org.gradle.api.internal.tasks

import org.gradle.api.DomainObjectCollection
import org.gradle.api.internal.tasks.properties.DefaultFinalizingValidatingProperty
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue
import org.gradle.api.internal.tasks.properties.PropertyValidationContext
import org.gradle.api.internal.tasks.properties.ValidationActions
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.typeconversion.UnsupportedNotationException
import spock.lang.Specification

import java.nio.file.Path
import java.util.concurrent.Callable

class DefaultFinalizingValidatingPropertyTest extends Specification {
    def "notifies property value of start and end of execution when it implements lifecycle interface"() {
        def value = Mock(LifecycleAwareValue)
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> value

        when:
        property.prepareValue()

        then:
        1 * value.prepareValue()
        0 * value._

        when:
        property.cleanupValue()

        then:
        1 * value.cleanupValue()
        0 * value._
    }

    def "does not notify null property value"() {
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, true, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> null

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }

    def "does not notify property value that does not implement lifecycle interface"() {
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> "thing"

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }

    def "required files validation does not query present provider value"() {
        def provider = Mock(Provider)
        def valueWrapper = Stub(PropertyValue) { call() >> wrap(provider) }
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.REQUIRED_INPUT_FILES)

        when:
        property.validate(Stub(PropertyValidationContext))

        then:
        1 * provider.isPresent() >> true
        0 * provider._

        where:
        description | wrap
        "direct"    | { it }
        "list"      | { [it] }
        "array"     | { [it] as Object[] }
        "nested"    | { [[it] as Object[]] }
    }

    def "required files validation skips live collections and deferred elements"() {
        def domainObjects = Mock(DomainObjectCollection)
        def taskProvider = Mock(TaskProvider)
        def iterable = Mock(Iterable)
        def callable = Mock(Callable)
        def valueWrapper = Stub(PropertyValue) {
            call() >> [domainObjects, taskProvider, iterable, callable, Path.of("input.txt")]
        }
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.REQUIRED_INPUT_FILES)
        def context = Mock(PropertyValidationContext)

        when:
        property.validate(context)

        then:
        noExceptionThrown()
        0 * domainObjects._
        0 * taskProvider._
        0 * iterable._
        0 * callable._
        0 * context.visitPropertyError(_)
    }

    def "unsupported notation while unpacking a file input is reported as a property problem"() {
        def provider = Mock(Provider)
        def context = Mock(PropertyValidationContext)
        def valueWrapper = Stub(PropertyValue) { call() >> provider }
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.INPUT_FILE_VALIDATOR)

        when:
        property.validate(context)

        then:
        noExceptionThrown()
        1 * provider.isPresent() >> true
        1 * provider.get() >> { throw new UnsupportedNotationException("bad notation") }
        1 * context.visitPropertyError(_)
        0 * context.getFileResolver()
    }
}
