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
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses == ["1", "2"] as Set
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses == ["3"] as Set
        mapping.getConstantDependentsForClass("c").accessibleDependentClasses == [] as Set

        mapping.getConstantDependentsForClass("a").privateDependentClasses == [] as Set
        mapping.getConstantDependentsForClass("b").privateDependentClasses == [] as Set
        mapping.getConstantDependentsForClass("c").privateDependentClasses == ["4"] as Set
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
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, ["a", "b", "c"] as Set)

        then:
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses == ["1", "2"] as Set
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses == [] as Set
        mapping.getConstantDependentsForClass("c").accessibleDependentClasses == ["4"] as Set

        mapping.getConstantDependentsForClass("a").privateDependentClasses == [] as Set
        mapping.getConstantDependentsForClass("b").privateDependentClasses == ["3"] as Set
        mapping.getConstantDependentsForClass("c").privateDependentClasses == [] as Set
    }

    def "removes all removed classes from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addPrivateDependent("b", "3")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, ["a", "b", "c"] as Set)

        then:
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses.isEmpty()
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses.isEmpty()
        mapping.getConstantDependentsForClass("c").accessibleDependentClasses.isEmpty()

        mapping.getConstantDependentsForClass("a").privateDependentClasses.isEmpty()
        mapping.getConstantDependentsForClass("b").privateDependentClasses == ["3"] as Set
        mapping.getConstantDependentsForClass("c").privateDependentClasses.isEmpty()

    }

    def "removes all classes that were visited but are not dependents anymore from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, ["a", "b"] as Set)

        then:
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses == ["1"] as Set
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses == [] as Set
    }

    def "does not remove classes that are not in new mapping from mapping on merge"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, [] as Set)

        then:
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses == ["1", "2"] as Set
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses == ["2"] as Set
    }

    def "removes all removed classes from visited"() {
        given:
        ConstantToDependentsMapping oldMapping = ConstantToDependentsMapping.builder()
            .addAccessibleDependent("a", "1")
            .addAccessibleDependent("a", "2")
            .addAccessibleDependent("b", "2")
            .build()
        ConstantToDependentsMapping newMapping = ConstantToDependentsMapping.builder()
            .build()

        when:
        ConstantToDependentsMapping mapping = merger.merge(newMapping, oldMapping, ["1", "4"] as Set)

        then:
        mapping.getConstantDependentsForClass("a").accessibleDependentClasses == ["2"] as Set
        mapping.getConstantDependentsForClass("b").accessibleDependentClasses == ["2"] as Set
    }

}
