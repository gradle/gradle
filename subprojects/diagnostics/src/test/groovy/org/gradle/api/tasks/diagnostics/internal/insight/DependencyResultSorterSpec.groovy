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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedModuleVersionResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 8/22/12
 */
class DependencyResultSorterSpec extends Specification {
    def "sorts by requested version and prefers exact matching selected version over inexact"() {
        def d1 = newDependency(newSelector("org.aha", "aha", "1.0"), newId("org.gradle", "zzzz", "3.0"))

        def d2 = newDependency(newSelector("org.gradle", "core", "2.0"), newId("org.gradle", "core", "2.0"))
        def d3 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"))
        def d4 = newDependency(newSelector("org.gradle", "core", "1.5"), newId("org.gradle", "core", "2.0"))

        def d5 = newDependency(newSelector("org.gradle", "xxxx", "1.0"), newId("org.gradle", "xxxx", "1.0"))

        def d6 = newDependency(newSelector("org.gradle", "zzzz", "1.5"), newId("org.gradle", "zzzz", "3.0"))
        def d7 = newDependency(newSelector("org.gradle", "zzzz", "2.0"), newId("org.gradle", "zzzz", "3.0"))

        when:
        def sorted = DependencyResultSorter.sort([d5, d3, d6, d1, d2, d7, d4])

        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7]
    }

    def "semantically compares versions"() {
        def d1 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"))
        def d2 = newDependency(newSelector("org.gradle", "core", "1.0-alpha"), newId("org.gradle", "core", "2.0"))

        when:
        def sorted = DependencyResultSorter.sort([d1, d2])

        then:
        sorted == [d2, d1]
    }

    def "sorts by from when requested version is the same"() {
        def d1 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.a", "a", "1.0"))
        def d2 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.b", "a", "1.0"))
        def d3 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.b", "b", "0.8"))
        def d4 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.b", "b", "1.12"))
        def d5 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.b", "b", "2.0"))
        def d6 = newDependency(newSelector("org.gradle", "core", "1.0"), newId("org.gradle", "core", "2.0"), newId("org.b", "c", "0.9"))
        def d7 = newDependency(newSelector("org.gradle", "other", "1.0"), newId("org.gradle", "other", "1.0"), newId("org.b", "a", "0.9"))
        def d8 = newDependency(newSelector("org.gradle", "other", "1.0"), newId("org.gradle", "other", "1.0"), newId("org.b", "a", "0.9.1"))

        when:
        def sorted = DependencyResultSorter.sort([d7, d8, d1, d3, d5, d2, d4, d6])


        then:
        sorted == [d1, d2, d3, d4, d5, d6, d7, d8]
    }

    private newDependency(ModuleVersionSelector requested, ModuleVersionIdentifier selected, ModuleVersionIdentifier from = newId("org", "a", "1.0")) {
        new DefaultResolvedDependencyResult(requested, new DefaultResolvedModuleVersionResult(selected),
                new DefaultResolvedModuleVersionResult(from))
    }
}
