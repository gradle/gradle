/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.insight

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class DependencyResultSorterSpec extends Specification {
    def versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())
    def versionComparator = new DefaultVersionComparator()

    @Unroll
    def "throws exception if dependencyt or requested component selector is null (#d1, #d2)"() {
        when:
        DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Dependency edge or the requested component selector may not be null"

        where:
        d1           | d2
        null         | null
        null         | newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0")) | null
        newDependency(null, DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0")) | newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0")) | newDependency(null, DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
    }

    def "sorts by comparing ProjectComponentSelector on left and ModuleComponentSelector on right"() {
        def d1 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2]
    }

    def "sorts by comparing ModuleComponentSelector on left and ProjectComponentSelector on right"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        def d2 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d2, d1]
    }

    def "sorts by requested ModuleComponentSelector by version"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.aha", "aha", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "0.8"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.5"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))

        def d5 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "xxxx", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "xxxx", "1.0"))

        def d6 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "zzzz", "1.5"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "zzzz", "2.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d5, d3, d6, d1, d2, d7, d4], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7]
    }

    def "for a given module prefers dependency where selected exactly matches requested"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "2.2"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.2"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.5"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "3.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.2"))

        when:
        def sorted = DependencyResultSorter.sort([d3, d1, d5, d2, d4], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2, d3, d4, d5]
    }

    def "semantically compares versions for ModuleComponentSelector"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "0.8"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0-alpha"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.2"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.11"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.11.2"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.11.11"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "2"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))

        when:
        def sorted = DependencyResultSorter.sort([d4, d7, d1, d6, d8, d5, d3, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8]
    }

    def "orders a mix of dynamic and static versions for ModuleComponentSelector"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "not-a-dynamic-selector"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "0.8"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "(,2.0]"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.2+"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "[1.2,)"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "latest.integration"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d9 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "latest.zzz"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))

        when:
        def sorted = DependencyResultSorter.sort([d4, d7, d1, d6, d8, d5, d3, d9, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8, d9]
    }

    def "sorts by from when requested ModuleComponentSelector version is the same"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.a", "a", "1.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.b", "a", "1.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.b", "b", "0.8"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.b", "b", "1.12"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.b", "b", "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"), DefaultModuleComponentIdentifier.newId("org.b", "c", "0.9"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "other", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "other", "1.0"), DefaultModuleComponentIdentifier.newId("org.b", "a", "0.9"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "other", "1.0"), DefaultModuleComponentIdentifier.newId("org.gradle", "other", "1.0"), DefaultModuleComponentIdentifier.newId("org.b", "a", "0.9.1"))

        when:
        def sorted = DependencyResultSorter.sort([d7, d8, d1, d3, d5, d2, d4, d6], versionSelectorScheme, versionComparator)


        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8]
    }

    def "sorts by requested ProjectComponentSelector by project path"() {
        def d1 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        def d2 = newDependency(TestComponentIdentifiers.newSelector(":myPath"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d3 = newDependency(TestComponentIdentifiers.newSelector(":newPath"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(TestComponentIdentifiers.newSelector(":path2:path6"), DefaultModuleComponentIdentifier.newId("org.gradle", "core", "2.0"))

        def d5 = newDependency(TestComponentIdentifiers.newSelector(":path3:path2"), DefaultModuleComponentIdentifier.newId("org.gradle", "xxxx", "1.0"))

        def d6 = newDependency(TestComponentIdentifiers.newSelector(":project2"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        def d7 = newDependency(TestComponentIdentifiers.newSelector(":project5"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d5, d3, d6, d1, d2, d7, d4], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7]
    }

    def "sorts by from for just ProjectComponentIdentifiers"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project6"), newProjectId(":project2"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project5"), newProjectId(":project1"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d2, d1]
    }

    def "sorts by from for just ModuleComponentIdentifiers"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project5"), DefaultModuleComponentIdentifier.newId("org.gradle", "zzzz", "3.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId("org.gradle", "xxxx", "1.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d2, d1]
    }

    def "sorts by from for left ProjectComponentIdentifier and right ModuleComponentIdentifier"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project5"), newProjectId(":project1"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId("org.gradle", "xxxx", "1.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d1, d2]
    }

    def "sorts by from for left ModuleComponentIdentifier and right ProjectComponentIdentifier"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId("org.gradle", "xxxx", "1.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector("org.gradle", "core", "1.0"), newProjectId(":project5"), newProjectId(":project1"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator)

        then:
        sorted == [d2, d1]
    }

    private newDependency(ComponentSelector requested, ComponentIdentifier selected, ComponentIdentifier from = DefaultModuleComponentIdentifier.newId("org", "a", "1.0")) {
        return Stub(DependencyEdge) {
            toString() >> "$requested -> $selected"
            getRequested() >> requested
            getActual() >> selected
            getFrom() >> from
        }
    }
}
