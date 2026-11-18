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
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal
import org.gradle.util.Path
import spock.lang.Specification

class VersionMappingComponentDependencyResolverTest extends Specification {

    def projectDependencyResolver = Stub(ProjectDependencyPublicationResolver) {
        resolveComponent(ModuleVersionIdentifier, Path.path(":p")) >> DefaultModuleVersionIdentifier.newId("org", "p-published", "1.0")
    }

    def "resolves the version of a module in the graph"() {
        def root = component("org", "root", "1.0", [
            dependency(moduleSelector("org", "a"), component("org", "a", "2.0", [
                dependency(moduleSelector("org", "b"), component("org", "b", "3.0"))
            ]))
        ])

        expect:
        resolver(root).maybeResolveVersion("org", "b", null) == DefaultModuleVersionIdentifier.newId("org", "b", "3.0")
    }

    def "resolves a substituted module to the selected component"() {
        def root = component("org", "root", "1.0", [
            dependency(moduleSelector("org", "old"), component("org", "new", "2.0"))
        ])

        expect:
        resolver(root).maybeResolveVersion("org", "old", null) == DefaultModuleVersionIdentifier.newId("org", "new", "2.0")
    }

    def "resolves a requested project to its publication coordinates"() {
        def root = component("org", "root", "1.0", [
            dependency(projectSelector(":p"), projectComponent(":p"))
        ])

        expect:
        resolver(root).maybeResolveVersion("org", "unknown", Path.path(":p")) == DefaultModuleVersionIdentifier.newId("org", "p-published", "1.0")
    }

    def "does not resolve a module that is not in the graph"() {
        def root = component("org", "root", "1.0", [
            dependency(moduleSelector("org", "a"), component("org", "a", "2.0"))
        ])

        expect:
        resolver(root).maybeResolveVersion("org", "missing", null) == null
        resolver(root).maybeResolveVersion("org", "missing", Path.path(":other")) == null
    }

    def "prefers the dependency that requested the project over one that requested the module"() {
        def moduleDependency = dependency(moduleSelector("org", "x"), component("org", "substituted", "2.0"))
        def projectDependency = dependency(projectSelector(":p"), projectComponent(":p"))
        def root = component("org", "root", "1.0", moduleFirst ? [moduleDependency, projectDependency] : [projectDependency, moduleDependency])

        expect:
        resolver(root).maybeResolveVersion("org", "x", Path.path(":p")) == DefaultModuleVersionIdentifier.newId("org", "p-published", "1.0")

        where:
        moduleFirst << [true, false]
    }

    def "falls back to the dependency that requested the module when no dependency requested the project"() {
        def root = component("org", "root", "1.0", [
            dependency(moduleSelector("org", "x"), component("org", "substituted", "2.0"))
        ])

        expect:
        resolver(root).maybeResolveVersion("org", "x", Path.path(":p")) == DefaultModuleVersionIdentifier.newId("org", "substituted", "2.0")
    }

    def "walks the graph only once across lookups"() {
        def root = Mock(ResolvedComponentResult) {
            getModuleVersion() >> DefaultModuleVersionIdentifier.newId("org", "root", "1.0")
        }
        def resolver = resolver(root)

        when:
        resolver.maybeResolveVersion("org", "a", null)
        resolver.maybeResolveVersion("org", "b", null)
        resolver.maybeResolveVersion("org", "c", null)

        then:
        1 * root.getDependencies() >> ([] as Set)
    }

    private VersionMappingComponentDependencyResolver resolver(ResolvedComponentResult root) {
        new VersionMappingComponentDependencyResolver(projectDependencyResolver, root)
    }

    private ResolvedComponentResult component(String group, String name, String version, List<DependencyResult> dependencies = []) {
        Stub(ResolvedComponentResult) {
            getModuleVersion() >> DefaultModuleVersionIdentifier.newId(group, name, version)
            getDependencies() >> (dependencies as LinkedHashSet)
        }
    }

    private ResolvedComponentResult projectComponent(String identityPath) {
        def id = Stub(ProjectComponentIdentifierInternal) {
            getIdentityPath() >> Path.path(identityPath)
        }
        Stub(ResolvedComponentResult) {
            getId() >> id
            getModuleVersion() >> DefaultModuleVersionIdentifier.newId("org", "p-in-graph", "0.1")
            getDependencies() >> ([] as Set)
        }
    }

    private ResolvedDependencyResult dependency(ComponentSelector requested, ResolvedComponentResult selected) {
        Stub(ResolvedDependencyResult) {
            getRequested() >> requested
            getSelected() >> selected
        }
    }

    private static ComponentSelector moduleSelector(String group, String name) {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, name), "1.0")
    }

    private ComponentSelector projectSelector(String identityPath) {
        Stub(ProjectComponentSelectorInternal) {
            getIdentityPath() >> Path.path(identityPath)
        }
    }

}
