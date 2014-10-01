/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.platform.base.Platform;
import spock.lang.Specification;

public class NamedElementSelectorTest extends Specification {


    def elements = Mock(NamedDomainObjectContainer)

    def platform1 = Stub(Platform) {
        getName() >> "name1"
    }
    def platform2 = Stub(Platform) {
        getName() >> "name2"
    }
    def platform3 = Stub(Platform) {
        getName() >> "name3"
    }

    def iterator = Mock(Iterator)

    def setup() {
        elements.iterator() >> iterator
        1 * elements.withType(Platform) >> elements

        (_..3) * iterator.hasNext() >> true
        (_..1) * iterator.next() >> platform2
        (_..1) * iterator.next() >> platform1 //notice 1 is here not 2
        (_..1) * iterator.next() >> platform3
        (_..1) * iterator.hasNext() >> false

        _ * elements.size() >> 3
    }

    def "selecting one element returns the correct element"() {
        def selector = new NamedElementSelector(Platform, ["name2"])

        when:
        def res = selector.transform(elements)

        then:
        res == [platform2]
    }

    def "when selecting no elements, the first one is returned"() {
        def selector = new NamedElementSelector(Platform, [])

        when:
        def res = selector.transform(elements)

        then:
        res == [platform2]
    }

    def "selecting multiple elements returns elements in the defined order"() {
        def selector = new NamedElementSelector(Platform, ["name1", "name2", "name3"])

        when:
        def res = selector.transform(elements)

        then:
        res == [platform2, platform1, platform3]
    }

    def "selecting an element that does not exists fails"() {
        def selector = new NamedElementSelector(Platform, ["blah"])

        when:
        selector.transform(elements)

        then:
        thrown(InvalidUserDataException)

    }

    def "selecting multiple elements where one does not exist fails"() {
        def selector = new NamedElementSelector(Platform, ["name1", "foo", "name2"])

        when:
        selector.transform(elements)

        then:
        thrown(InvalidUserDataException)
    }
}