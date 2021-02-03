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

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.DependencyEdge
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class DependencyResultSorterSpec extends Specification {
    def versionParser = new VersionParser()
    def versionComparator = new DefaultVersionComparator()
    def versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, versionParser)

    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    @Unroll
    def "throws exception if dependency or requested component selector is null (#d1, #d2)"() {
        when:
        DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == "Dependency edge or the requested component selector may not be null"

        where:
        d1                                                                                                                                                          | d2
        null                                                                                                                                                        | null
        null         | newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0")) | null
        newDependency(null, DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0")) | newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0")) | newDependency(null, DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
    }

    def "sorts by comparing ProjectComponentSelector on left and ModuleComponentSelector on right"() {
        def d1 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2]
    }

    def "sorts by comparing ModuleComponentSelector on left and ProjectComponentSelector on right"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        def d2 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d2, d1]
    }

    def "sorts by requested ModuleComponentSelector by version"() {
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.aha", "aha"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "core"), v("0.8")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "core"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "core"), v("1.5")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))

        def d5 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), "1.0"))

        def d6 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), v("1.5")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), v("2.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d5, d3, d6, d1, d2, d7, d4], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7]
    }

    def "for a given module prefers dependency where selected exactly matches requested"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("2.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("2.2")), DefaultModuleComponentIdentifier.newId(core, "2.2"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.5")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("3.0")), DefaultModuleComponentIdentifier.newId(core, "2.2"))

        when:
        def sorted = DependencyResultSorter.sort([d3, d1, d5, d2, d4], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2, d3, d4, d5]
    }

    def "semantically compares versions for ModuleComponentSelector"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("0.8")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0-alpha")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.2")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.11")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.11.2")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.11.11")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("2")), DefaultModuleComponentIdentifier.newId(core, "2.0"))

        when:
        def sorted = DependencyResultSorter.sort([d4, d7, d1, d6, d8, d5, d3, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8]
    }

    def "orders a mix of dynamic and static versions for ModuleComponentSelector"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("2.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("not-a-dynamic-selector")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("0.8")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("(,2.0]")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.2+")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("[1.2,)")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("latest.integration")), DefaultModuleComponentIdentifier.newId(core, "2.0"))
        def d9 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("latest.zzz")), DefaultModuleComponentIdentifier.newId(core, "2.0"))

        when:
        def sorted = DependencyResultSorter.sort([d4, d7, d1, d6, d8, d5, d3, d9, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8, d9]
    }

    def "sorts by from when requested ModuleComponentSelector version is the same"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.a", "a"), "1.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "a"), "1.0"))
        def d3 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "b"), "0.8"))
        def d4 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "b"), "1.12"))
        def d5 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "b"), "2.0"))
        def d6 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), DefaultModuleComponentIdentifier.newId(core, "2.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "c"), "0.9"))
        def d7 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "other"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "other"), "1.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "a"), "0.9"))
        def d8 = newDependency(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId("org.gradle", "other"), v("1.0")), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "other"), "1.0"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.b", "a"), "0.9.1"))

        when:
        def sorted = DependencyResultSorter.sort([d7, d8, d1, d3, d5, d2, d4, d6], versionSelectorScheme, versionComparator, versionParser)


        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8]
    }

    def "sorts by requested ProjectComponentSelector by project path"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(TestComponentIdentifiers.newSelector(":hisProject"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        def d2 = newDependency(TestComponentIdentifiers.newSelector(":myPath"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))
        def d3 = newDependency(TestComponentIdentifiers.newSelector(":newPath"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))
        def d4 = newDependency(TestComponentIdentifiers.newSelector(":path2:path6"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "core"), "2.0"))

        def d5 = newDependency(TestComponentIdentifiers.newSelector(":path3:path2"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), "1.0"))

        def d6 = newDependency(TestComponentIdentifiers.newSelector(":project2"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        def d7 = newDependency(TestComponentIdentifiers.newSelector(":project5"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d5, d3, d6, d1, d2, d7, d4], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7]
    }

    def "sorts by from for just ProjectComponentIdentifiers"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project6"), newProjectId(":project2"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project5"), newProjectId(":project1"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d2, d1]
    }

    def "sorts by from for just ModuleComponentIdentifiers"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project5"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "zzzz"), "3.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), "1.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d2, d1]
    }

    def "sorts by from for left ProjectComponentIdentifier and right ModuleComponentIdentifier"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project5"), newProjectId(":project1"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), "1.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d1, d2]
    }

    def "sorts by from for left ModuleComponentIdentifier and right ProjectComponentIdentifier"() {
        def core = DefaultModuleIdentifier.newId("org.gradle", "core")
        def d1 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project6"), DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.gradle", "xxxx"), "1.0"))
        def d2 = newDependency(DefaultModuleComponentSelector.newSelector(core, v("1.0")), newProjectId(":project5"), newProjectId(":project1"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2], versionSelectorScheme, versionComparator, versionParser)

        then:
        sorted == [d2, d1]
    }

    private newDependency(ComponentSelector requested, ComponentIdentifier selected, ComponentIdentifier from = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "a"), "1.0")) {
        return Stub(DependencyEdge) {
            toString() >> "$requested -> $selected"
            getRequested() >> requested
            getActual() >> selected
            getFrom() >> from
        }
    }
}
