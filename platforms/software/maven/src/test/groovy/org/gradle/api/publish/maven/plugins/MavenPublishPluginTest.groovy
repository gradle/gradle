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

package org.gradle.api.publish.maven.plugins


import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.component.ComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class MavenPublishPluginTest extends AbstractProjectBuilderSpec {

    PublishingExtension publishing
    def componentArtifacts = Mock(FileCollection)
    def component = Stub(SoftwareComponentInternal)

    def setup() {
        project.pluginManager.apply(MavenPublishPlugin)
        publishing = project.extensions.getByType(PublishingExtension)
        project.components.add(component)

        PublishArtifactSet artifactSet = Stub() {
            getFiles() >> componentArtifacts
        }

        component.name >> "test-component"
        component.artifacts >> artifactSet
    }

    def "no publication by default"() {
        expect:
        publishing.publications.empty
    }

    def "publication can be added"() {
        when:
        publishing.publications.create("test", MavenPublication)

        then:
        publishing.publications.size() == 1
        publishing.publications.test instanceof DefaultMavenPublication
    }

    def "creates generation tasks for publication"() {
        when:
        publishing.publications.create("test", MavenPublication)

        then:
        project.tasks["generatePomFileForTestPublication"] instanceof GenerateMavenPom
    }

    def "creates generation tasks for publication with component"() {
        def component = Stub(TestComponent)

        when:
        def publication = publishing.publications.create("test", MavenPublication)
        publication.from(component)

        then:
        project.tasks["generateMetadataFileForTestPublication"] instanceof GenerateModuleMetadata
    }

    def "creates publish tasks for each publication and repository"() {
        when:
        publishing.publications.create("test", MavenPublication)
        publishing.repositories { maven { url = "http://foo.com" } }

        then:
        project.tasks["publishTestPublicationToMavenRepository"] instanceof PublishToMavenRepository
    }

    def "creates task to publish each publication to mavenLocal"() {
        given:
        publishing.publications.create("test", MavenPublication)

        expect:
        publishLocalTasks.size() == 1
        publishLocalTasks.first().name == "publishTestPublicationToMavenLocal"
    }

    def "can explicitly add mavenLocal as a publishing repository"() {
        given:
        publishing.publications.create("test", MavenPublication)

        when:
        def mavenLocal = publishing.repositories.mavenLocal()

        then:
        publishTasks.size() == 1
        publishTasks.first().repository.is(mavenLocal)

        publishLocalTasks.size() == 1
    }

    def "tasks are created for compatible publication / repo"() {
        given:
        publishing.publications.create("test", MavenPublication)

        when:
        def repo1 = publishing.repositories.maven { url = "foo" }
        def repo2 = publishing.repositories.maven { url = "foo"; name "other" }
        publishing.repositories.ivy {}

        then:
        publishTasks.size() == 2
        publishTasks.first().repository.is(repo1)
        publishTasks.first().name == "publishTestPublicationToMavenRepository"
        publishTasks.last().repository.is(repo2)
        publishTasks.last().name == "publishTestPublicationToOtherRepository"
    }

    List<PublishToMavenLocal> getPublishLocalTasks() {
        project.tasks.withType(PublishToMavenLocal).sort { it.name }
    }

    List<PublishToMavenRepository> getPublishTasks() {
        def allTasks = project.tasks.withType(PublishToMavenRepository).sort { it.name }
        allTasks.removeAll(publishLocalTasks)
        return allTasks
    }

    def "publication coordinates are live"() {
        when:
        project.group = "group"
        project.version = "version"

        and:
        publishing.publications.create("test", MavenPublication)

        then:
        with(publishing.publications.test.pom.coordinates) {
            groupId.get() == "group"
            version.get() == "version"
        }

        when:
        project.group = "changed-group"
        project.version = "changed-version"

        then:
        with(publishing.publications.test.pom.coordinates) {
            groupId.get() == "changed-group"
            version.get() == "changed-version"
        }
    }

    def "pom dir moves with build dir"() {
        when:
        publishing.publications.create("test", MavenPublication)
        def newBuildDir = project.file("changed")
        project.buildDir = newBuildDir

        then:
        project.tasks["generatePomFileForTestPublication"].destination == new File(newBuildDir, "publications/test/pom-default.xml")
    }

    def "creates publish tasks for all publications in a repository"() {
        when:
        publishing.publications.create("test", MavenPublication)
        publishing.publications.create("test2", MavenPublication)
        publishing.repositories { maven { url = "http://foo.com" } }
        publishing.repositories { maven { name='other'; url = "http://bar.com" } }

        then:
        project.tasks["publishAllPublicationsToMavenRepository"].dependsOn.containsAll([
                "publishTestPublicationToMavenRepository",
                "publishTest2PublicationToMavenRepository"
        ])
        project.tasks["publishAllPublicationsToOtherRepository"].dependsOn.containsAll([
                "publishTestPublicationToOtherRepository",
                "publishTest2PublicationToOtherRepository"
        ])
    }

    interface TestComponent extends SoftwareComponentInternal, ComponentWithVariants {
    }
}
