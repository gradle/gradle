/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.internal

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.util.TextUtil
import spock.lang.Specification

class ProjectDependencyPublicationResolverTest extends Specification {
    def projectDependency = Mock(ProjectDependency)
    def project = Mock(ProjectInternal)
    def extensions = Mock(ExtensionContainerInternal)
    def publishing = Mock(PublishingExtension)
    def publications = new DefaultPublicationContainer(DirectInstantiator.INSTANCE)
    def publication = Mock(PublicationInternal)

    def "resolves project coordinates if project does not have publishing extension"() {
        when:
        projectDependency.dependencyProject >> project
        project.evaluate() >> project
        project.extensions >> extensions
        extensions.findByType(PublishingExtension) >> null

        projectDependency.group >> "dep-group"
        project.name >> "project-name"
        projectDependency.version >> "dep-version"

        then:
        with (resolve()) {
            group == "dep-group"
            name == "project-name"
            version == "dep-version"
        }
    }

    def "uses project coordinates when dependent project has no publications"() {
        when:
        dependentProjectHasPublications()

        projectDependency.group >> "dep-group"
        project.name >> "project-name"
        projectDependency.version >> "dep-version"

        then:
        with (resolve()) {
            group == "dep-group"
            name == "project-name"
            version == "dep-version"
        }
    }

    def "uses coordinates of single publication from dependent project"() {
        when:
        def publication = Mock(PublicationInternal)
        publication.name >> 'mock'
        publication.coordinates >> new DefaultModuleVersionIdentifier("pub-group", "pub-name", "pub-version")

        dependentProjectHasPublications(publication)

        then:
        with (resolve()) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "prefers coordinates of publication from dependent project where all publications share coordinates"() {
        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('pub2', "pub-group", "pub-name", "pub-version")

        dependentProjectHasPublications(publication, publication2)

        then:
        with (resolve()) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "uses coordinates from root component publication"() {
        given:
        def child1 = Stub(TestComponent)
        def child2 = Stub(TestComponent)
        child2.variants >> [child1]
        def child3 = Stub(TestComponent)
        def root = Stub(TestComponent)
        root.variants >> [child2, child3]

        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        publication.component >> root

        def publication2 = pub('pub2', "pub-group", "pub-name-child1", "pub-version")
        publication2.component >> child1

        def publication3 = pub('pub3', "pub-group", "pub-name-child2", "pub-version")
        publication3.component >> child2

        def publication4 = pub('pub4', "pub-group", "pub-name-child3", "pub-version")
        publication4.component >> child3

        when:
        dependentProjectHasPublications(publication, publication2, publication3, publication4)

        then:
        with (resolve()) {
            group == "pub-group"
            name == "pub-name"
            version == "pub-version"
        }
    }

    def "resolve fails when target project has multiple publications with different coordinates"() {
        given:
        project.displayName >> "<project>"

        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('pub2', "other-group", "other-name", "other-version")

        dependentProjectHasPublications(publication, publication2)

        and:
        resolve()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == TextUtil.toPlatformLineSeparators("""Publishing is not yet able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in <project>:
  - Publication 'mock' with coordinates pub-group:pub-name:pub-version
  - Publication 'pub2' with coordinates other-group:other-name:other-version""")
    }

    def "resolve fails when target project has multiple component publications with different coordinates"() {
        given:
        def component1 = Stub(SoftwareComponentInternal)
        def component2 = Stub(SoftwareComponentInternal)
        def component3 = Stub(TestComponent)
        project.displayName >> "<project>"

        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        publication.component >> component1
        def publication2 = pub('pub2', "other-group", "other-name1", "other-version")
        publication2.component >> component2
        def publication3 = pub('pub3', "other-group", "other-name2", "other-version")
        publication3.component >> component3

        dependentProjectHasPublications(publication, publication2, publication3)

        and:
        resolve()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == TextUtil.toPlatformLineSeparators("""Publishing is not yet able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in <project>:
  - Publication 'mock' with coordinates pub-group:pub-name:pub-version
  - Publication 'pub2' with coordinates other-group:other-name1:other-version
  - Publication 'pub3' with coordinates other-group:other-name2:other-version""")
    }

    private ModuleVersionIdentifier resolve() {
        new ProjectDependencyPublicationResolver().resolve(projectDependency)
    }

    private void dependentProjectHasPublications(PublicationInternal... added) {
        projectDependency.dependencyProject >> project
        project.evaluate() >> project
        project.extensions >> extensions
        extensions.findByType(PublishingExtension) >> publishing
        publishing.publications >> publications
        publications.addAll(added)
    }

    private PublicationInternal pub(def name, def group, def module, def version) {
        def publication = Mock(PublicationInternal)
        publication.name >> name
        publication.coordinates >> new DefaultModuleVersionIdentifier(group, module, version)
        return publication
    }

    interface TestComponent extends SoftwareComponentInternal, ComponentWithVariants {
    }
}
