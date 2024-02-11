/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.provider.Providers
import org.gradle.execution.ProjectConfigurer
import org.gradle.internal.Describables
import org.gradle.util.Path
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

import java.util.function.Function

class DefaultProjectDependencyPublicationResolverTest extends Specification {
    def projectState = Mock(ProjectState) {
        fromMutableState(_) >> { Function factory -> factory.apply(project) }
    }
    def project = Stub(ProjectInternal) {
        getOwner() >> projectState
        getIdentityPath() >> Path.path(":path")
    }
    def registry = new TestProjectPublicationRegistry()
    def projectConfigurer = Mock(ProjectConfigurer)
    def projects = Mock(ProjectStateRegistry)

    def setup() {
        project.displayName >> "<project>"
        projects.stateFor(project.identityPath) >> project.owner
    }

    def resolver = new DefaultProjectDependencyPublicationResolver(registry, projectConfigurer, projects)

    def "resolving component configures project"() {
        when:
        resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)

        then:
        1 * projectConfigurer.configureFully(project.owner)
    }

    def "resolving variant configures project"() {
        when:
        resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, "variant-name")

        then:
        1 * projectConfigurer.configureFully(project.owner)
    }

    def "uses project coordinates when dependent project has no publications"() {
        when:
        project.group >> "dep-group"
        project.name >> "project-name"
        project.version >> "dep-version"

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "dep-group"
            name == "project-name"
            version == "dep-version"
        }
    }

    def "uses coordinates of single publication from dependent project"() {
        when:
        registry.register(project.identityPath, pub("mock", "pub-group", "pub-name", "pub-version"))

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "prefers coordinates of publication from dependent project where all publications share coordinates"() {
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('pub2', "pub-group", "pub-name", "pub-version")

        when:
        registry.register(project.identityPath, publication, publication2)

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "uses coordinates from root component publication"() {
        when:
        configureMultiCoordinatePublication()

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "uses child coordinates when resolving variant"() {
        when:
        configureMultiCoordinatePublication()

        then:
        ["child1_variant1", "child1_variant2"].each {
            with(resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, it)) {
                group == "pub-group-child1"
                name == "pub-name-child1"
                version == "pub-version-child1"
            }
        }
        ["child2_variant1", "child2_variant2"].each {
            with(resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, it)) {
                group == "pub-group-child2"
                name == "pub-name-child2"
                version == "pub-version-child2"
            }
        }
        ["child3_variant1", "child3_variant2"].each {
            with(resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, it)) {
                group == "pub-group-child3"
                name == "pub-name-child3"
                version == "pub-version-child3"
            }
        }
        ["root_variant1", "root_variant2"].each {
            with(resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, it)) {
                group == "pub-group"
                name == "pub-name"
                version == "pub-version"
            }
        }
    }

    def "detects circular components graphs"() {
        given:
        def comp1 = Stub(TestComponent) {
            getName() >> "foo"
        }
        def comp2 = Stub(TestComponent) {
            getVariants() >> [comp1]
            getName() >> "bar"
        }
        comp1.getVariants() >> [comp2]

        def publication = pub('mock', "pub-group", "pub-name", "pub-version", comp1)
        registry.register(project.identityPath, publication)

        when:
        resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, "variant-name")

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Circular dependency detected while resolving component coordinates. Found the following components: foo, bar"
    }

    def "detects multiple components with the same coordinates"() {
        given:
        def child1 = Stub(TestComponent)
        def child2 = Stub(TestComponent)

        def root = Stub(TestComponent) {
            getVariants() >> [child1, child2]
        }

        def publication = pub('mock3', "root-group", "root-name", "root-version", root)
        def publication2 = pub('mock', "pub-group", "pub-name", "pub-version", child1)
        def publication3 = pub('mock2', "pub-group", "pub-name", "pub-version", child2)
        registry.register(project.identityPath, publication, publication2, publication3)

        when:
        resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, "variant-name")

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Multiple child components may not share the same coordinates: pub-group:pub-name:pub-version"
    }

    def "detects variants with the same name"() {
        def child1 = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "foo"
                }
            ]
        }
        def child2 = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "foo"
                }
            ]
        }

        def root = Stub(TestComponent) {
            getVariants() >> [child1, child2]
            getName() >> "root"
        }

        def publication = pub('mock', "root-group", "root-name", "root-version", root)
        def publication2 = pub('mock1', "child1-group", "child1-name", "child1-version", child1)
        def publication3 = pub('mock2', "child2-group", "child2-name", "child2-version", child2)
        registry.register(project.identityPath, publication, publication2, publication3)

        when:
        resolver.resolveVariant(ModuleVersionIdentifier, project.identityPath, "variant-name")

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Found multiple variants with name 'foo' in component 'root' of project ':path'"
    }

    def "ignores components without coordinates of requested type"() {
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication3 = Stub(ProjectComponentPublication)
        publication3.getCoordinates(_) >> null

        when:
        registry.register(project.identityPath, publication, publication2, publication3)

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "resolve fails when target project has multiple publications with different coordinates"() {
        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('pub2', "other-group", "other-name", "other-version")

        registry.register(project.identityPath, publication, publication2)

        and:
        resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == TextUtil.toPlatformLineSeparators("""Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in <project>:
  - Publication 'mock' with coordinates pub-group:pub-name:pub-version
  - Publication 'pub2' with coordinates other-group:other-name:other-version""")
    }

    def "resolve fails when target project has multiple component publications with different coordinates"() {
        given:
        def component1 = Stub(SoftwareComponentInternal)
        def component2 = Stub(SoftwareComponentInternal)
        def component3 = Stub(TestComponent)

        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version", component1)
        def publication2 = pub('pub2', "other-group", "other-name1", "other-version", component2)
        def publication3 = pub('pub3', "other-group", "other-name2", "other-version", component3)

        registry.register(project.identityPath, publication, publication2, publication3)

        and:
        resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == TextUtil.toPlatformLineSeparators("""Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in <project>:
  - Publication 'mock' with coordinates pub-group:pub-name:pub-version
  - Publication 'pub2' with coordinates other-group:other-name1:other-version
  - Publication 'pub3' with coordinates other-group:other-name2:other-version""")
    }

    def "When target project has multiple publications with different coordinates, but only one has a component, that one is resolved"() {
        given:
        def component1 = Stub(SoftwareComponentInternal) {
            getUsages() >> [Stub(UsageContext)]
        }

        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version", component1)
        def publication2 = pub('pub2', "other-group", "other-name1", "other-version")
        def publication3 = pub('pub3', "other-group", "other-name2", "other-version")

        registry.register(project.identityPath, publication, publication2, publication3)

        then:
        with (resolver.resolveComponent(ModuleVersionIdentifier, project.identityPath)) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "resolve fails when target project has no publications with coordinate of requested type and no default available"() {
        when:
        resolver.resolveComponent(String, project.identityPath)

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == 'Could not find any publications of type String in <project>.'
    }

    private ProjectComponentPublication pub(def name, def group, def module, def version, def component = null) {
        def publication = Mock(ProjectComponentPublication)
        publication.name >> name
        publication.displayName >> Describables.of("publication '" + name + "'")
        publication.getCoordinates(ModuleVersionIdentifier) >> DefaultModuleVersionIdentifier.newId(group, module, version)
        publication.component >> Providers.ofNullable(component)
        return publication
    }

    interface TestComponent extends SoftwareComponentInternal, ComponentWithVariants {
    }

    def configureMultiCoordinatePublication() {
        def child1 = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "child1_variant1"
                },
                Stub(UsageContext) {
                    getName() >> "child1_variant2"
                }
            ]
        }
        def child2 = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "child2_variant1"
                },
                Stub(UsageContext) {
                    getName() >> "child2_variant2"
                }
            ]
        }
        child2.variants >> [child1]
        def child3 = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "child3_variant1"
                },
                Stub(UsageContext) {
                    getName() >> "child3_variant2"
                }
            ]
        }
        def root = Stub(TestComponent) {
            getUsages() >> [
                Stub(UsageContext) {
                    getName() >> "root_variant1"
                },
                Stub(UsageContext) {
                    getName() >> "root_variant2"
                }
            ]
        }
        root.variants >> [child2, child3]

        def publication = pub('mock', "pub-group", "pub-name", "pub-version", root)
        def publication2 = pub('pub2', "pub-group-child1", "pub-name-child1", "pub-version-child1", child1)
        def publication3 = pub('pub3', "pub-group-child2", "pub-name-child2", "pub-version-child2", child2)
        def publication4 = pub('pub4', "pub-group-child3", "pub-name-child3", "pub-version-child3", child3)

        registry.register(project.identityPath, publication, publication2, publication3, publication4)
    }

    class TestProjectPublicationRegistry implements ProjectPublicationRegistry {

        Map<Path, List<ProjectComponentPublication>> map = [:]

        void register(Path identityPath, ProjectComponentPublication... publications) {
            map.computeIfAbsent(identityPath, { [] }).addAll(publications)
        }

        @Override
        <T extends ProjectPublication> Collection<ProjectComponentPublication> getPublications(Class<T> type, Path projectIdentityPath) {
            assert type == ProjectComponentPublication
            return map.getOrDefault(projectIdentityPath, [])
        }

        @Override
        void registerPublication(ProjectInternal project, ProjectPublication publication) {
            throw new UnsupportedOperationException()
        }

        @Override
        <T extends ProjectPublication> Collection<Reference<T>> getPublications(Class<T> type) {
            throw new UnsupportedOperationException()
        }
    }
}
