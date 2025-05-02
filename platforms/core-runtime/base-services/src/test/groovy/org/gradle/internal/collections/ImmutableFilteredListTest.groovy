/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.collections

import spock.lang.Specification

/**
 * Tests {@link ImmutableFilteredList}.
 */
class ImmutableFilteredListTest extends Specification {

    def "can construct from an existing list"() {
        when:
        def list = ImmutableFilteredList.allOf([1, 2, 3, 4, 5, 6, 7, 8])

        then:
        list.size() == 8
        for (int i = 0; i < 8; i++) {
            assert list.get(i) == i + 1
        }
    }

    def "list equality works"() {
        expect:
        ImmutableFilteredList.allOf([1, 2, 3, 4, 5, 6, 7, 8]) == [1, 2, 3, 4, 5, 6, 7, 8]
        [1, 2, 3, 4, 5, 6, 7, 8] == ImmutableFilteredList.allOf([1, 2, 3, 4, 5, 6, 7, 8])
        ImmutableFilteredList.allOf([1, 2, 3]) == ImmutableFilteredList.allOf([1, 2, 3])
        ImmutableFilteredList.allOf([1, 2, 3]) != [1, 2]
        [1, 2] != ImmutableFilteredList.allOf([1, 2, 3])
        ImmutableFilteredList.allOf([1, 2, 3]) != [3, 1, 2]
    }

    def "list cannot be mutated"() {
        given:
        def list = ImmutableFilteredList.allOf([1, 2, 3])

        when:
        list.add(4)

        then:
        thrown(UnsupportedOperationException)

        when:
        list.add(0, 4)

        then:
        thrown(UnsupportedOperationException)

        when:
        list.remove((Object) 1)

        then:
        thrown(UnsupportedOperationException)

        when:
        list.remove(0)

        then:
        thrown(UnsupportedOperationException)
    }

    def "can filter list"() {
        given:
        def list = ImmutableFilteredList.allOf([1, 2, 3, 4, 5, 6])

        expect:
        list.matching(x -> x > 2) == [3, 4, 5, 6]
        list.matching(x -> x < 2) == [1]
        list.matching(x -> x > 2).matching(x -> x < 5) == [3, 4]
        list.matching(x -> x != 5).matching(x -> x != 3) == [1, 2, 4, 6]
        list.matching(x -> x != 1).matching(x -> x != 6) == [2, 3, 4, 5]
    }

    def "can exclude items from another ImmutableFilteredList"() {
        given:
        def list = ImmutableFilteredList.allOf([1, 2, 3, 4, 5, 6])
        def another = list.matching(x -> x % 2 == 0)

        expect:
        list.withoutIndexFrom(0, list) == [2, 3, 4, 5, 6]
        list.withoutIndexFrom(0, another) == [1, 3, 4, 5, 6]
        list.withoutIndexFrom(0, another).withoutIndexFrom(1, another).withoutIndexFrom(2, another) == [1, 3, 5]
    }
}
