/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.publish.internal.mapping

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import spock.lang.Specification

class VersionMappingComponentDependencyResolverTest extends Specification {

    def projectDependencyResolver = Mock(ProjectDependencyPublicationResolver)

    def "graph is traversed only once across many lookups"() {
        given:
        // root -> a -> b
        //      -> c
        def aId = id("org", "a", "1.0")
        def bId = id("org", "b", "1.0")
        def cId = id("org", "c", "1.0")
        def rootId = id("org", "root", "1.0")

        def a = component(aId)
        def b = component(bId)
        def c = component(cId)
        def root = component(rootId)

        def aDep = moduleDependency("org", "a", a)
        def bDep = moduleDependency("org", "b", b)
        def cDep = moduleDependency("org", "c", c)

        def resolver = new VersionMappingComponentDependencyResolver(projectDependencyResolver, root)

        when:
        100.times {
            assert resolver.maybeResolveVersion("org", "a", null) == aId
            assert resolver.maybeResolveVersion("org", "b", null) == bId
            assert resolver.maybeResolveVersion("org", "c", null) == cId
            assert resolver.maybeResolveVersion("org", "missing", null) == null
        }

        then:
        1 * root.getDependencies() >> ([aDep, cDep] as Set)
        1 * a.getDependencies() >> ([bDep] as Set)
        1 * b.getDependencies() >> Collections.emptySet()
        1 * c.getDependencies() >> Collections.emptySet()
    }

    def "falls back to requested coordinates when component was substituted"() {
        given:
        // requested org:requested-a was substituted with org:actual-a:2.0 via the resolution graph
        def actualId = id("org", "actual-a", "2.0")
        def rootId = id("org", "root", "1.0")

        def actual = component(actualId)
        def root = component(rootId)

        actual.getDependencies() >> Collections.emptySet()
        root.getDependencies() >> ([moduleDependency("org", "requested-a", actual)] as Set)

        def resolver = new VersionMappingComponentDependencyResolver(projectDependencyResolver, root)

        expect:
        resolver.maybeResolveVersion("org", "requested-a", null) == actualId
        resolver.maybeResolveVersion("org", "actual-a", null) == actualId
        resolver.maybeResolveVersion("org", "missing", null) == null
    }

    private ResolvedComponentResult component(ModuleVersionIdentifier moduleVersion) {
        Mock(ResolvedComponentResult) {
            getModuleVersion() >> moduleVersion
            getId() >> Mock(ModuleComponentIdentifier)
        }
    }

    private ResolvedDependencyResult moduleDependency(String requestedGroup, String requestedModule, ResolvedComponentResult selected) {
        def selector = Mock(ModuleComponentSelector) {
            getGroup() >> requestedGroup
            getModule() >> requestedModule
        }
        Mock(ResolvedDependencyResult) {
            getRequested() >> selector
            getSelected() >> selected
        }
    }

    private static ModuleVersionIdentifier id(String group, String name, String version) {
        DefaultModuleVersionIdentifier.newId(group, name, version)
    }
}
