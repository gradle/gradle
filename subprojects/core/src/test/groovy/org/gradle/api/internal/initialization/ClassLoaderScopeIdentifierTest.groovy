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

package org.gradle.api.internal.initialization

import spock.lang.Specification

class ClassLoaderScopeIdentifierTest extends Specification {
    private ClassLoaderScopeIdentifier root = new ClassLoaderScopeIdentifier(null, "root")

    def "equality"() {
        expect:
        root.localId() == root.localId()
        root.localId() != root.exportId()
        root.exportId() != root.localId()
        root.exportId() == root.exportId()
    }

    def "creates child"() {
        def child = root.child(root.name)

        expect:
        child.localId() == child.localId()
        child.localId() != child.exportId()
        child.exportId() != child.localId()
        child.exportId() == child.exportId()
    }

    def "children with same name are equivalent"() {
        def child1 = root.child(root.name)
        def child2 = root.child(root.name)
        def grandChild1 = child1.child(root.name)
        def grandChild2 = child2.child(root.name)

        expect:
        equivalent(child1, child2)
        equivalent(grandChild1, grandChild2)
    }

    def "children from different parents with same name are not equal"() {
        def parent1 = root.child("parent1")
        def parent2 = root.child("parent2")
        def child1 = parent1.child(root.name)
        def child2 = parent2.child(root.name)

        expect:
        notEquivalent(child1, child2)
    }

    def "children with different name are not equal"() {
        given:
        def child1 = root.child(root.name)
        def child2 = root.child(root.name + "changed")

        expect:
        notEquivalent(child1, child2)
    }

    private static void equivalent(ClassLoaderScopeIdentifier child1, ClassLoaderScopeIdentifier child2) {
        assert child1 == child2
        assert child1.localId() == child2.localId()
        assert child1.exportId() == child2.exportId()
    }

    private static void notEquivalent(ClassLoaderScopeIdentifier child1, ClassLoaderScopeIdentifier child2) {
        assert child1 != child2
        assert child1.localId() != child2.localId()
        assert child1.exportId() != child2.exportId()
    }

}
