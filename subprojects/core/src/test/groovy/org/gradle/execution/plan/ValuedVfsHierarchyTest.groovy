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

package org.gradle.execution.plan

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.gradle.internal.collect.PersistentList
import org.gradle.internal.snapshot.CaseSensitivity
import org.gradle.internal.snapshot.VfsRelativePath
import spock.lang.Specification

import java.util.function.Supplier

class ValuedVfsHierarchyTest extends Specification {

    def "the empty hierarchy has no values"() {
        def emptyHierarchy = emptyHierarchy()

        expect:
        emptyHierarchy.empty
        getAllValues(emptyHierarchy).empty
        getValuesFor(emptyHierarchy, "/some/location").empty
    }

    def "non-empty hierarchies are not-empty"() {
        def hierarchy = emptyHierarchy()
        hierarchy = hierarchy.recordValue(VfsRelativePath.of(location), 1)

        expect:
        !hierarchy.empty

        where:
        location << ['', '/some', '/some/location']
    }

    def "can query exact values"() {
        ValuedVfsHierarchy<Integer> hierarchy = complexHierarchy()

        when:
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues(location, visitor)
        then:
        visitor.exactValues == values

        where:
        location                                      | values
        ""                                            | []
        "some/location"                               | [1]
        "some/other/location"                         | [2]
        "some/location/intermediate"                  | []
        "some/location/intermediate/child"            | [4, 3]
        "some/location/intermediate/child/sub1/leaf1" | [5]
        "some/location/intermediate/child/sub1/leaf2" | [6]
        "some/location/intermediate/child/sub3/leaf"  | [7]
    }

    def "can query ancestors values"() {
        ValuedVfsHierarchy<Integer> hierarchy = complexHierarchy()

        when:
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues(location, visitor)
        then:
        visitor.ancestorValues.asMap() == values

        where:
        location                                        | values
        ""                                              | [:]
        "some"                                          | [:]
        "some/other/location"                           | [:]
        "non-existing"                                  | [:]
        "some/location/intermediate"                    | ['intermediate': [1]]
        "some/location/intermediate/child"              | ['intermediate/child': [1]]
        "some/location/intermediate/child/sub1/leaf1"   | ['intermediate/child/sub1/leaf1': [1], 'sub1/leaf1': [4, 3]]
        "some/location/intermediate/child/sub1/leaf2"   | ['intermediate/child/sub1/leaf2': [1], 'sub1/leaf2': [4, 3]]
        "some/location/intermediate/child/sub3/leaf"    | ['sub3/leaf': [4, 3], 'intermediate/child/sub3/leaf': [1]]
        "some/location/intermediate/child/non-existing" | ['intermediate/child/non-existing': [1], 'non-existing': [4, 3]]
    }

    def "can query child values"() {
        ValuedVfsHierarchy<Integer> hierarchy = complexHierarchy()

        when:
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues(location, visitor)
        then:
        visitor.childValues.asMap() == values

        where:
        location                                        | values
        ""                                              | ['some/location/intermediate/child/sub1/leaf1': [5],
                                                           'some/location/intermediate/child/sub1/leaf2': [6],
                                                           'some/location/intermediate/child/sub3/leaf': [7],
                                                           'some/other/location': [2],
                                                           'some/location/intermediate/child': [4, 3],
                                                           'some/location': [1]]
        "some"                                          | ['location/intermediate/child/sub1/leaf1': [5],
                                                           'location/intermediate/child/sub1/leaf2': [6],
                                                           'location/intermediate/child/sub3/leaf': [7],
                                                           'other/location': [2],
                                                           'location/intermediate/child': [4, 3],
                                                           location: [1]]
        "some/other/location"                           | [:]
        "non-existing"                                  | [:]
        "some/location/intermediate"                    | ['child/sub1/leaf2': [6], 'child/sub1/leaf1': [5], 'child/sub3/leaf': [7], child: [4, 3]]
        "some/location/intermediate/child"              | ['sub3/leaf': [7], 'sub1/leaf2': [6], 'sub1/leaf1': [5]]
        "some/location/intermediate/child/sub1"         | ['leaf2': [6], 'leaf1': [5]]
        "some/location/intermediate/child/sub1/leaf1"   | [:]
        "some/location/intermediate/child/sub1/leaf2"   | [:]
        "some/location/intermediate/child/sub3/leaf"    | [:]
        "some/location/intermediate/child/non-existing" | [:]
    }

    def "can record value at root"() {
        def hierarchy = complexHierarchy()
        hierarchy = hierarchy.recordValue(VfsRelativePath.of(""), 10)

        when:
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues("", visitor)
        then:
        visitor.exactValues == [10]
    }

    private ValuedVfsHierarchy<Integer> complexHierarchy() {
        def hierarchy = emptyHierarchy()
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location"), 1)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/other/location"), 2)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location/intermediate/child/sub1/leaf1"), 5)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location/intermediate/child/sub1/leaf2"), 6)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location/intermediate/child"), 3)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location/intermediate/child/sub3/leaf"), 7)
        hierarchy = hierarchy.recordValue(VfsRelativePath.of("some/location/intermediate/child"), 4)
        return hierarchy
    }

    private ValuedVfsHierarchy<Integer> emptyHierarchy() {
        return ValuedVfsHierarchy.<Integer> emptyHierarchy(CaseSensitivity.CASE_SENSITIVE)
    }

    static List<Integer> getAllValues(ValuedVfsHierarchy<Integer> hierarchy) {
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues("", visitor)
        return visitor.allValues
    }

    static List<Integer> getValuesFor(ValuedVfsHierarchy<Integer> hierarchy, String location) {
        def visitor = new CollectingValueVisitor()
        hierarchy.visitValues(location, visitor)
        return visitor.allValues
    }

    static class CollectingValueVisitor implements ValuedVfsHierarchy.ValueVisitor<Integer> {
        List<Integer> exactValues = []
        Multimap<String, Integer> ancestorValues = ArrayListMultimap.create()
        Multimap<String, Integer> childValues = ArrayListMultimap.create()

        List<Integer> getAllValues() {
            return exactValues + ancestorValues.values() + childValues.values()
        }

        @Override
        void visitExact(Integer value) {
            exactValues.add(value)
        }

        @Override
        void visitAncestor(Integer value, VfsRelativePath pathToVisitedLocation) {
            ancestorValues.put(pathToVisitedLocation.asString, value)
        }

        @Override
        void visitChildren(PersistentList<Integer> values, Supplier<String> relativePathSupplier) {
            values.forEach { value -> childValues.put(relativePathSupplier.get(), value)
            }
        }
    }
}
