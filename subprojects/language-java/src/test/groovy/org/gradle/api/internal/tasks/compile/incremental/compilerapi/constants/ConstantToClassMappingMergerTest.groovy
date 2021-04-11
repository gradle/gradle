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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants

import it.unimi.dsi.fastutil.ints.IntSet
import spock.lang.Specification

class ConstantToClassMappingMergerTest extends Specification {

    private ConstantToClassMappingMerger merger = new ConstantToClassMappingMerger()

    def "maps new class-to-constant mapping to expected constant-to-class mapping if previous was null"() {
        given:
        Map<String, Collection<String>> newMapping = [
            "a": ["d", "e", "f"],
            "b": ["e", "f"],
            "c": []
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, null, [] as Set)

        then:
        mapping.constantDependentsForClassHash("d".hashCode()) == ["a"] as Set
        mapping.constantDependentsForClassHash("e".hashCode()) == ["a", "b"] as Set
        mapping.constantDependentsForClassHash("f".hashCode()) == ["a", "b"] as Set
        mapping.getClassNames().size() == 2
        mapping.getClassNames().containsAll(["a", "b"])
        mapping.getConstantToClassIndexes().keySet() == ["d".hashCode(), "e".hashCode(), "f".hashCode()] as Set
    }

    def "merges new class-to-constant to constant-to-class mapping with previous"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.of(["a", "b", "c"], [
            ("d".hashCode()): IntSet.of(0), // d: a
            ("e".hashCode()): IntSet.of(1), // e: b
            ("f".hashCode()): IntSet.of(2)  // f: c
        ] as Map)
        Map<String, Collection<String>> newMapping = [
            "a": ["d", "e", "f"],
            "b": ["e", "f"],
            "c": ["g"]
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.constantDependentsForClassHash("d".hashCode()) == ["a"] as Set
        mapping.constantDependentsForClassHash("e".hashCode()) == ["a", "b"] as Set
        mapping.constantDependentsForClassHash("f".hashCode()) == ["a", "b"] as Set
        mapping.constantDependentsForClassHash("g".hashCode()) == ["c"] as Set
        mapping.getClassNames().size() == 3
        mapping.getClassNames().containsAll(["a", "b", "c"])
        mapping.getConstantToClassIndexes().keySet() == ["d".hashCode(), "e".hashCode(), "f".hashCode(), "g".hashCode()] as Set
    }

    def "removes all removed classes from constant-to-class mapping on merge"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.of(["a", "b", "c"], [
            ("d".hashCode()): IntSet.of(0), // d: a
            ("e".hashCode()): IntSet.of(1), // e: b
            ("f".hashCode()): IntSet.of(2)  // f: c
        ] as Map)
        Map<String, Collection<String>> newMapping = [
            "a": ["d", "e", "f"],
            "b": ["e", "f"],
            "c": ["f"]
        ]
        Set<String> removedClasses = ["a", "c"] as Set

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, removedClasses)

        then:
        mapping.constantDependentsForClassHash("d".hashCode()).isEmpty()
        mapping.constantDependentsForClassHash("e".hashCode()) == ["b"] as Set
        mapping.constantDependentsForClassHash("f".hashCode()) == ["b"] as Set
        mapping.getClassNames().size() == 1
        mapping.getClassNames().containsAll(["b"])
        mapping.getConstantToClassIndexes().keySet() == ["e".hashCode(), "f".hashCode()] as Set
    }

    def "removes all classes with empty constant dependencies from constant-to-class mapping on merge"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.of(["a", "b", "c"], [
            ("d".hashCode()): IntSet.of(0), // d: a
            ("e".hashCode()): IntSet.of(1), // e: b
            ("f".hashCode()): IntSet.of(2)  // f: c
        ] as Map)
        Map<String, Collection<String>> newMapping = [
            "a": [],
            "b": ["e", "f"],
            "c": ["f"]
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.constantDependentsForClassHash("d".hashCode()) == [] as Set
        mapping.constantDependentsForClassHash("e".hashCode()) == ["b"] as Set
        mapping.constantDependentsForClassHash("f".hashCode()) == ["b", "c"] as Set
        mapping.getClassNames().size() == 2
        mapping.getClassNames().containsAll(["b", "c"])
        mapping.getConstantToClassIndexes().keySet() == ["e".hashCode(), "f".hashCode()] as Set
    }

    def "does not remove classes that are not in new mapping from constant-to-class mapping on merge"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.of(["a", "b", "c"], [
            ("d".hashCode()): IntSet.of(0), // d: a
            ("e".hashCode()): IntSet.of(1), // e: b
            ("f".hashCode()): IntSet.of(2)  // f: c
        ] as Map)
        Map<String, Collection<String>> newMapping = [
            "b": ["e", "f"]
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.constantDependentsForClassHash("d".hashCode()) == ["a"] as Set
        mapping.constantDependentsForClassHash("e".hashCode()) == ["b"] as Set
        mapping.constantDependentsForClassHash("f".hashCode()) == ["b", "c"] as Set
        mapping.getClassNames().size() == 3
        mapping.getClassNames().containsAll(["a", "b", "c"])
        mapping.getConstantToClassIndexes().keySet() == ["d".hashCode(), "e".hashCode(), "f".hashCode()] as Set
    }

}
