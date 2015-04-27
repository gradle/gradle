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

package org.gradle.api.internal

import spock.lang.Specification
import spock.lang.Subject

class AddOnlyDomainObjectSetTest extends Specification {

    @Subject
    def set = new AddOnlyDomainObjectSet(String)

    def "clear is not supported"() {
        when:
        set.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "remove is not supported"() {
        when:
        set.remove("foo")

        then:
        thrown(UnsupportedOperationException)
    }

    def "removeAll is not supported"() {
        when:
        set.removeAll([])

        then:
        thrown(UnsupportedOperationException)
    }

    def "retainAll is not supported"() {
        when:
        set.retainAll([])

        then:
        thrown(UnsupportedOperationException)
    }

    def "iterator does not support removal of elements"() {
        given:
        def iterator = set.iterator()

        when:
        iterator.remove()

        then:
        thrown(UnsupportedOperationException)
    }
}
