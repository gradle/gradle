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


import spock.lang.Specification

class ConstantToDependentsMappingMergerTest extends Specification {

    private ConstantToDependentsMappingMerger merger = new ConstantToDependentsMappingMerger()

    def "maps new mapping to expected mapping if previous was null"() {
        given:
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "3")
            .addPrivateDependent("c", "4")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, null, [] as Set)

        then:
        mapping.findAccessibleConstantDependentsFor("a") == ["1", "2"] as Set
        mapping.findAccessibleConstantDependentsFor("b") == ["3"] as Set
        mapping.findAccessibleConstantDependentsFor("c") == [] as Set

        mapping.findPrivateConstantDependentsFor("a") == [] as Set
        mapping.findPrivateConstantDependentsFor("b") == [] as Set
        mapping.findPrivateConstantDependentsFor("c") == ["4"] as Set

        mapping.getDependentClasses().size() == 4
        mapping.getDependentClasses().containsAll(["1", "2", "3", "4"])
        mapping.getAccessibleConstantDependents().keySet() == ["a", "b"] as Set
        mapping.getPrivateConstantDependents().keySet() == ["c"] as Set
    }

    def "merges new mappings with old mappings"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addPrivateDependent("b", "3")
            .addAccessibleDependent("c", "4")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findAccessibleConstantDependentsFor("a") == ["1", "2"] as Set
        mapping.findAccessibleConstantDependentsFor("b") == [] as Set
        mapping.findAccessibleConstantDependentsFor("c") == ["4"] as Set

        mapping.findPrivateConstantDependentsFor("a") == [] as Set
        mapping.findPrivateConstantDependentsFor("b") == ["3"] as Set
        mapping.findPrivateConstantDependentsFor("c") == [] as Set

        mapping.getDependentClasses().size() == 4
        mapping.getDependentClasses().containsAll(["1", "2", "3", "4"])
    }

    def "removes all removed classes from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addPrivateDependent("b", "3")
            .addAccessibleDependent("c", "4")
            .build()

        Set<String> removedClasses = ["a", "c"] as Set

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, removedClasses)

        then:
        mapping.findAccessibleConstantDependentsFor("a").isEmpty()
        mapping.findAccessibleConstantDependentsFor("b").isEmpty()
        mapping.findAccessibleConstantDependentsFor("c").isEmpty()

        mapping.findPrivateConstantDependentsFor("a").isEmpty()
        mapping.findPrivateConstantDependentsFor("b") == ["3"] as Set
        mapping.findPrivateConstantDependentsFor("c").isEmpty()

        mapping.getDependentClasses().size() == 1
        mapping.getDependentClasses().containsAll(["3"])
    }

    def "removes all classes that were visited but are not dependents anymore from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addVisitedClass("2")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findAccessibleConstantDependentsFor("a") == ["1"] as Set
        mapping.findAccessibleConstantDependentsFor("b") == [] as Set
        mapping.getDependentClasses() as Set == ["1"] as Set
    }

    def "does not remove classes that are not in new mapping from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addVisitedClass("3")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.findAccessibleConstantDependentsFor("a") == ["1", "2"] as Set
        mapping.findAccessibleConstantDependentsFor("b") == ["2"] as Set
        mapping.getDependentClasses().size() == 2
        mapping.getDependentClasses() as Set == ["1", "2"] as Set
    }

    def "removes all removed classes from visited"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addVisitedClass("3")
            .addVisitedClass("4")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, ["1", "4"] as Set)

        then:
        mapping.findAccessibleConstantDependentsFor("a") == ["2"] as Set
        mapping.findAccessibleConstantDependentsFor("b") == ["2"] as Set
        mapping.getDependentClasses() as Set == ["2"] as Set
    }

}
