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
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Specification

public class ProjectDependencyPublicationResolverTest extends Specification {
    def projectDependency = Mock(ProjectDependency)
    def project = Mock(Project)
    def extensions = Mock(ExtensionContainer)
    def publishing = Mock(PublishingExtension)
    def publications = new DefaultPublicationContainer(new DirectInstantiator())
    def publication = Mock(PublicationInternal)

    def "resolves project coordinates if project does not have publishing extension"() {
        when:
        projectDependency.dependencyProject >> project
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

    def "fails if cannot resolve single publication"() {
        when:
        def publication = pub('mock', "pub-group", "pub-name", "pub-version")
        def publication2 = pub('pub2', "other-group", "other-name", "other-version")

        dependentProjectHasPublications(publication, publication2)

        and:
        resolve()

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Publishing is not yet able to resolve a dependency on a project with multiple different publications."
    }

    private ModuleVersionIdentifier resolve() {
        new ProjectDependencyPublicationResolver().resolve(projectDependency)
    }

    private void dependentProjectHasPublications(PublicationInternal... added) {
        projectDependency.dependencyProject >> project
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
}
