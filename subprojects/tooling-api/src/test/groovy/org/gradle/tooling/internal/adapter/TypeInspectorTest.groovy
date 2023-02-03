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

package org.gradle.tooling.internal.adapter

import spock.lang.Specification

import static org.gradle.tooling.internal.adapter.TypeInspectorTestHelper.*

class TypeInspectorTest extends Specification {
    def inspector = new TypeInspector()

    def "inspects type and caches result"() {
        expect:
        def types = inspector.getReachableTypes(Thing)
        types == [Thing, SuperThing, GenericThing, Item1, Item2, Item3, Item4, Item5, Runnable, List, Map, Set] as Set

        def types2 = inspector.getReachableTypes(Thing)
        types2.is(types)
    }

    def "inspects cyclic types"() {
        expect:
        def types = inspector.getReachableTypes(Parent)
        types == [Parent, Child] as Set

        def types2 = inspector.getReachableTypes(Child)
        types2 == types
    }

    def "inspects cyclic generic types"() {
        expect:
        def types = inspector.getReachableTypes(GenericChild)
        types == [GenericItem1, GenericItem2, GenericChild] as Set

        def types2 = inspector.getReachableTypes(GenericChild)
        types2.is(types)
    }
}
