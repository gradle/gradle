/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import spock.lang.Specification

class ConstructorComparatorTest extends Specification {
    def comparator = new ConstructorComparator()

    def "constructors with fewer parameters are first"() {
        def lhs = Stub(ClassGenerator.GeneratedConstructor)
        lhs.parameterTypes >> [String]
        def rhs = Stub(ClassGenerator.GeneratedConstructor)
        rhs.parameterTypes >> [String, Number]

        expect:
        comparator.compare(lhs, rhs) == -1
        comparator.compare(rhs, lhs) == 1
    }

    def "constructors with equal parameters are sorted by type names"() {
        def lhs = Stub(ClassGenerator.GeneratedConstructor)
        lhs.parameterTypes >> [String, Boolean]
        def rhs = Stub(ClassGenerator.GeneratedConstructor)
        rhs.parameterTypes >> [String, Number]

        expect:
        comparator.compare(lhs, rhs) == -1
        comparator.compare(rhs, lhs) == 1
    }

    def "can sort a list of constructors"() {
        def first = Stub(ClassGenerator.GeneratedConstructor)
        first.parameterTypes >> [String]
        def second = Stub(ClassGenerator.GeneratedConstructor)
        second.parameterTypes >> [String, Boolean]
        def third = Stub(ClassGenerator.GeneratedConstructor)
        third.parameterTypes >> [String, Number, Boolean]

        def list = [second, third, first]
        expect:
        list.sort(false, comparator) == [first, second, third]
    }
}
