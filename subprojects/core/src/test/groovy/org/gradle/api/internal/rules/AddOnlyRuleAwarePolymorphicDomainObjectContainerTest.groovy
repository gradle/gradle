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

package org.gradle.api.internal.rules

import org.gradle.api.Named
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification
import spock.lang.Subject

class AddOnlyRuleAwarePolymorphicDomainObjectContainerTest extends Specification {

    class ElementType implements Named {
        String name
    }

    @Subject
    def container = new AddOnlyRuleAwarePolymorphicDomainObjectContainer(ElementType, DirectInstantiator.INSTANCE) {}

    def "clear is not supported"() {
        when:
        container.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "remove is not supported"() {
        when:
        container.remove("foo")

        then:
        thrown(UnsupportedOperationException)
    }

    def "removeAll is not supported"() {
        when:
        container.removeAll([])

        then:
        thrown(UnsupportedOperationException)
    }

    def "retainAll is not supported"() {
        when:
        container.retainAll([])

        then:
        thrown(UnsupportedOperationException)
    }

    def "iterator does not support removal of elements"() {
        given:
        def iterator = container.iterator()

        when:
        iterator.remove()

        then:
        thrown(UnsupportedOperationException)
    }
}
