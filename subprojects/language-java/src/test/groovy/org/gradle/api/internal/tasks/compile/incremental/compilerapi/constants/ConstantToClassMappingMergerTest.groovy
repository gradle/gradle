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
        ConstantToClassMapping newMapping = ConstantToClassMapping.builder()
            .addPublicDependent("a".hashCode(), "1")
            .addPublicDependent("a".hashCode(), "2")
            .addPublicDependent("b".hashCode(), "3")
            .addPrivateDependent("c".hashCode(), "4")
            .build()

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, null, [] as Set)

        then:
        mapping.findPublicConstantDependentsForClassHash("a".hashCode()) == ["1", "2"] as Set
        mapping.findPublicConstantDependentsForClassHash("b".hashCode()) == ["3"] as Set
        mapping.findPublicConstantDependentsForClassHash("c".hashCode()) == [] as Set

        mapping.findPrivateConstantDependentsForClassHash("a".hashCode()) == [] as Set
        mapping.findPrivateConstantDependentsForClassHash("b".hashCode()) == [] as Set
        mapping.findPrivateConstantDependentsForClassHash("c".hashCode()) == ["4"] as Set

        mapping.getClassNames().size() == 4
        mapping.getClassNames().containsAll(["1", "2", "3", "4"])
        mapping.getPublicConstantDependents().keySet() == ["a".hashCode(), "b".hashCode()] as Set
        mapping.getPrivateConstantDependents().keySet() == ["c".hashCode()] as Set
    }

    def "merges new mappings with old mappings"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.builder()
            .addPublicDependent("a".hashCode(), "1")
            .addPublicDependent("b".hashCode(), "2")
            .build()
        ConstantToClassMapping newMapping = ConstantToClassMapping.builder()
            .addPublicDependent("a".hashCode(), "1")
            .addPublicDependent("a".hashCode(), "2")
            .addPrivateDependent("b".hashCode(), "3")
            .addPublicDependent("c".hashCode(), "4")
            .build()

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findPublicConstantDependentsForClassHash("a".hashCode()) == ["1", "2"] as Set
        mapping.findPublicConstantDependentsForClassHash("b".hashCode()) == [] as Set
        mapping.findPublicConstantDependentsForClassHash("c".hashCode()) == ["4"] as Set

        mapping.findPrivateConstantDependentsForClassHash("a".hashCode()) == [] as Set
        mapping.findPrivateConstantDependentsForClassHash("b".hashCode()) == ["3"] as Set
        mapping.findPrivateConstantDependentsForClassHash("c".hashCode()) == [] as Set

        mapping.getClassNames().size() == 4
        mapping.getClassNames().containsAll(["1", "2", "3", "4"])
    }

    def "removes all removed classes from mapping on merge"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.builder()
            .addPublicDependent("a".hashCode(), "1")
            .addPublicDependent("b".hashCode(), "2")
            .build()
        ConstantToClassMapping newMapping = ConstantToClassMapping.builder()
            .addPublicDependent("a".hashCode(), "1")
            .addPublicDependent("a".hashCode(), "2")
            .addPrivateDependent("b".hashCode(), "3")
            .addPublicDependent("c".hashCode(), "4")
            .build()

        Set<String> removedClasses = ["a", "c"] as Set

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, removedClasses)

        then:
        mapping.findPublicConstantDependentsForClassHash("a".hashCode()).isEmpty()
        mapping.findPublicConstantDependentsForClassHash("b".hashCode()).isEmpty()
        mapping.findPublicConstantDependentsForClassHash("c".hashCode()).isEmpty()

        mapping.findPrivateConstantDependentsForClassHash("a".hashCode()).isEmpty()
        mapping.findPrivateConstantDependentsForClassHash("b".hashCode()) == ["3"] as Set
        mapping.findPrivateConstantDependentsForClassHash("c".hashCode()).isEmpty()

        mapping.getClassNames().size() == 1
        mapping.getClassNames().containsAll(["3"])
    }

    def "removes all classes with empty constant dependencies from constant-to-class mapping on merge"() {
        given:
        ConstantToClassMapping oldMapping = ConstantToClassMapping.of(["a", "b", "c"], [
            ("d".hashCode()): IntSet.of(0), // d: a
            ("e".hashCode()): IntSet.of(1), // e: b
            ("f".hashCode()): IntSet.of(2)  // f: c
        ] as Map, [:])
        Map<String, Collection<String>> newMapping = [
            "a": [],
            "b": ["e", "f"],
            "c": ["f"]
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findPublicConstantDependentsForClassHash("d".hashCode()) == [] as Set
        mapping.findPublicConstantDependentsForClassHash("e".hashCode()) == ["b"] as Set
        mapping.findPublicConstantDependentsForClassHash("f".hashCode()) == ["b", "c"] as Set
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
        ] as Map, [:])
        Map<String, Collection<String>> newMapping = [
            "b": ["e", "f"]
        ]

        when:
        ConstantToClassMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findPublicConstantDependentsForClassHash("d".hashCode()) == ["a"] as Set
        mapping.findPublicConstantDependentsForClassHash("e".hashCode()) == ["b"] as Set
        mapping.findPublicConstantDependentsForClassHash("f".hashCode()) == ["b", "c"] as Set
        mapping.getClassNames().size() == 3
        mapping.getClassNames().containsAll(["a", "b", "c"])
        mapping.getConstantToClassIndexes().keySet() == ["d".hashCode(), "e".hashCode(), "f".hashCode()] as Set
    }

}
