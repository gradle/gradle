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
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.util.HelperUtil
import spock.lang.Specification

class MavenPublishPluginTest extends Specification {

    def project = HelperUtil.createRootProject()
    PublishingExtension publishing
    def componentArtifacts = Mock(FileCollection)
    def component = Stub(SoftwareComponentInternal)

    def setup() {
        project.plugins.apply(MavenPublishPlugin)
        publishing = project.extensions.getByType(PublishingExtension)
        project.components.add(component)

        PublishArtifactSet artifactSet = Stub() {
            getFiles() >> componentArtifacts
        }

        component.name >> "test-component"
        component.artifacts >> artifactSet
    }

    def "no publication without component"() {
        expect:
        publishing.publications.empty
    }

    def "publication can be added"() {
        when:
        publishing.publications.add("test", MavenPublication)

        then:
        publishing.publications.size() == 1
        publishing.publications.test instanceof DefaultMavenPublication
    }

    def "creates publish tasks for publication and repository"() {
        when:
        publishing.publications.add("test", MavenPublication)
        publishing.repositories { maven { url = "http://foo.com" } }

        then:
        project.tasks["publishTestPublicationToMavenRepository"] != null
        project.tasks["publishTestPublicationToMavenLocal"] != null
        project.tasks["generatePomFileForTestPublication"] != null
    }

    def "publication has artifacts from component"() {
        given:
        File artifactFile = project.file('artifactFile') << "content"
        PublishArtifactSet artifactSet = Mock()
        PublishArtifact artifact = Stub() {
            getFile() >> artifactFile
        }

        when:
        publishing.publications.add("test", MavenPublication) {
            from component
        }
        def pub = publishing.publications.test;

        then:
        pub.artifacts.size() == 1
        pub.artifacts.iterator().next().file == artifact.getFile()

        and:
        component.artifacts >> artifactSet
        artifactSet.iterator() >> [artifact].iterator()
    }

    def "task is created for publishing to mavenLocal"() {
        given:
        publishing.publications.add("test", MavenPublication)

        expect:
        publishLocalTasks.size() == 1
        publishLocalTasks.first().name == "publishTestPublicationToMavenLocal"
        publishLocalTasks.first().repository.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
        publishLocalTasks.first().repository.url == project.getServices().get(DependencyResolutionServices).baseRepositoryFactory.createMavenLocalRepository().url
    }

    def "can explicitly add mavenLocal as a publishing repository"() {
        given:
        publishing.publications.add("test", MavenPublication)

        when:
        def mavenLocal = publishing.repositories.mavenLocal()

        then:
        publishTasks.size() == 1
        publishTasks.first().repository.is(mavenLocal)

        publishLocalTasks.size() == 1
        publishTasks.first().repository.url == publishLocalTasks.first().repository.url
    }

    def "tasks are created for compatible publication / repo"() {
        given:
        publishing.publications.add("test", MavenPublication)

        expect:
        publishTasks.size() == 0

        when:
        def repo1 = publishing.repositories.maven { url "foo" }

        then:
        publishTasks.size() == 1
        publishTasks.last().repository.is(repo1)
        publishTasks.last().name == "publishTestPublicationToMavenRepository"

        when:
        publishing.repositories.ivy {}

        then:
        publishTasks.size() == 1

        when:
        def repo2 = publishing.repositories.maven { url "foo"; name "other" }

        then:
        publishTasks.size() == 2
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

    def "publication identity is a snapshot of project properties"() {
        when:
        project.group = "group"
        project.version = "version"

        and:
        publishing.publications.add("test", MavenPublication)

        then:
        with(publishing.publications.test.mavenProjectIdentity) {
            groupId == "group"
            version == "version"
        }

        when:
        project.group = "changed-group"
        project.version = "changed-version"

        then:
        with(publishing.publications.test.mavenProjectIdentity) {
            groupId == "group"
            version == "version"
        }
    }

    def "pom dir moves with build dir"() {
        when:
        publishing.publications.add("test", MavenPublication)

        then:
        project.tasks["generatePomFileForTestPublication"].destination == new File(project.buildDir, "publications/test/pom-default.xml")

        when:
        def newBuildDir = project.file("changed")
        project.buildDir = newBuildDir

        then:
        project.tasks["generatePomFileForTestPublication"].destination == new File(newBuildDir, "publications/test/pom-default.xml")
    }
}
