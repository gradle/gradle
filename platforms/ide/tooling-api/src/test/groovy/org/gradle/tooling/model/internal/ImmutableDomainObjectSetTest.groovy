/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.model.internal

import spock.lang.Specification

class ImmutableDomainObjectSetTest extends Specification {

    def emptySetHasZeroSize() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>([])

        expect:
        set.size() == 0
        set.isEmpty()
    }

    def canQuerySize() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'c'])

        expect:
        set.size() == 3
        !set.isEmpty()
    }

    def canGetElementByIndex() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'c'])

        expect:
        set[0] == 'a'
        set[2] == 'c'
    }

    def throwsIndexOutOfBoundsExceptionForInvalidIndex() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'c'])

        when:
        set[4]

        then:
        IndexOutOfBoundsException e = thrown()

        when:
        set[-1]

        then:
        e = thrown()
    }

    def iteratorOfEmptySetHasNoElements() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>([])

        expect:
        !set.iterator().hasNext()
    }

    def iteratorMaintainsOrderOfOriginalCollection() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'c'])

        expect:
        set.collect { it } == ['a', 'b', 'c']
        set.iterator().collect { it } == ['a', 'b', 'c']
    }

    def canGetElementsAsAList() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'c'])

        expect:
        set.all == ['a', 'b', 'c']
    }

    def ignoresDuplicateElementsInOriginalCollection() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>(['a', 'b', 'a', 'b', 'c'])

        expect:
        set.size() == 3
        set.collect { it } == ['a', 'b', 'c']
    }

    def cannotAddElementsToSet() {
        ImmutableDomainObjectSet<String> set = new ImmutableDomainObjectSet<String>([])

        when:
        set.add('a')

        then:
        UnsupportedOperationException e = thrown()
    }
}
