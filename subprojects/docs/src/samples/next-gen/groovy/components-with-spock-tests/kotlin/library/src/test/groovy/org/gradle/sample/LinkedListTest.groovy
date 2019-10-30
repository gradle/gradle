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

package org.gradle.sample

import spock.lang.Specification

class LinkedListTest extends Specification {
    def "test LinkedList constructor"() {
        given:
        def list = new LinkedList()

        expect:
        list.size() == 0
    }

    def "test LinkedList#add"() {
        def list = new LinkedList()

        when:
        list.add('one');
        then:
        list.size() == 1
        list.get(0) == 'one'

        when:
        list.add('two');
        then:
        list.size() == 2
        list.get(1) == 'two'
    }

    def "test LinkedList#remove"() {
        def list = new LinkedList()

        given:
        list.add('one')
        list.add('two')

        expect:
        list.remove('one')
        list.size() == 1
        list.get(0) == 'two'

        list.remove("two")
        list.size() == 0
    }

    def "test LinkedList#remove for missing element"() {
        def list = new LinkedList()

        given:
        list.add("one")
        list.add("two")

        expect:
        !list.remove("three")
        list.size() == 2
    }
}
