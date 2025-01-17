/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.provider

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import spock.lang.Specification

import static java.util.stream.Collectors.toList

class AppendOnceListTest extends Specification {
    def "can create empty list"() {
        given:
        def list = AppendOnceList.of()

        expect:
        list.size() == 0
        !list.iterator().hasNext()
    }

    def "can create single element list"() {
        given:
        def list = AppendOnceList.of("value")

        expect:
        list.size() == 1
        Iterables.getOnlyElement(list) == "value"
    }

    def "can append to list"() {
        when:
        def newList = list.plus("0")

        then:
        ImmutableList.copyOf(list) == listValues
        ImmutableList.copyOf(newList) == listValues + ["0"]

        where:
        list                                   || listValues
        AppendOnceList.<String> of()           || []
        AppendOnceList.of("1")                 || ["1"]
        AppendOnceList.<String> of().plus("1") || ["1"]
        AppendOnceList.of("1").plus("2")       || ["1", "2"]
    }

    def "cannot append twice"() {
        when:
        list.plus("other")

        then:
        thrown(IllegalStateException)

        where:
        list << [
            AppendOnceList.of().tap {
                plus("value")
            },
            AppendOnceList.of("value").tap {
                plus("value 2")
            },
            AppendOnceList.of("value").plus("value 2").tap {
                plus("value 3")
            }
        ]
    }

    def "can stream list"() {
        expect:
        list.stream().collect(toList()) == expectedValues

        where:
        list                             || expectedValues
        AppendOnceList.of()              || []
        AppendOnceList.of().plus("0")    || ["0"]
        AppendOnceList.of("0")           || ["0"]
        AppendOnceList.of("0").plus("1") || ["0", "1"]
    }

    def "can be collected to"() {
        expect:
        ImmutableList.copyOf(items.stream().collect(AppendOnceList.toAppendOnceList())) == items
        where:
        items << [
            [],
            ["0"],
            ["0", "1"]
        ]
    }

    def "list from collectors can be appended to"() {
        given:
        def list = items.stream().collect(AppendOnceList.toAppendOnceList())

        when:
        def newList = list.plus("100500")

        then:
        ImmutableList.copyOf(newList) == items + ["100500"]
        ImmutableList.copyOf(list) == items

        where:
        items << [
            [],
            ["0"],
            ["0", "1"]
        ]
    }

    def "list from collectors cannot be appended to twice"() {
        given:
        def list = items.stream().collect(AppendOnceList.toAppendOnceList())

        when:
        list.plus("100500")
        list.plus("100500")

        then:
        thrown(IllegalStateException)

        where:
        items << [
            [],
            ["0"],
            ["0", "1"]
        ]
    }

    def "list iterator is immutable"() {
        given:
        def list = AppendOnceList.of("1").plus("2")
        def iter = list.iterator().tap {
            assert it.hasNext()
            assert it.next() == "1"
        }

        when:
        iter.remove()

        then:
        thrown(UnsupportedOperationException)
    }
}
