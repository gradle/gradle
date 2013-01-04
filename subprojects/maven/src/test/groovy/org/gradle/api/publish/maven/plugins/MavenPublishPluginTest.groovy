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
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.internal.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.MavenPublicationInternal
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.HelperUtil
import spock.lang.Specification

class MavenPublishPluginTest extends Specification {

    def project = HelperUtil.createRootProject()
    PublishingExtension publishing

    def setup() {
        project.plugins.apply(MavenPublishPlugin)
        publishing = project.extensions.getByType(PublishingExtension)
    }

    def "no publication without component"() {
        expect:
        publishing.publications.empty
    }

    def "default publication with java plugin"() {
        when:
        javaPluginApplied()

        then:
        publishing.publications.size() == 1
        publishing.publications.maven instanceof DefaultMavenPublication
    }

    def "creates publish tasks"() {
        when:
        javaPluginApplied()
        publishing.repositories { maven { url = "http://foo.com" } }

        then:
        project.tasks["publishMavenPublicationToMavenRepository"] != null
        project.tasks["publishMavenPublicationToMavenLocal"] != null
    }

    def "java publication always has jar artifact"() {
        when:
        javaPluginApplied()

        then:
        mainPublication.artifacts.size() == 1
        mainPublication.artifacts.find { it.archiveTask instanceof Jar }
    }

    protected MavenPublicationInternal getMainPublication() {
        publishing.publications.maven
    }

    def javaPluginApplied() {
        project.plugins.apply(JavaPlugin)
    }

    def "task is created for publishing to mavenLocal"() {
        given:
        javaPluginApplied()

        expect:
        publishLocalTasks.size() == 1
        publishLocalTasks.first().name == "publishMavenPublicationToMavenLocal"
        publishLocalTasks.first().repository.name == ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
        publishLocalTasks.first().repository.url == project.getServices().get(DependencyResolutionServices).baseRepositoryFactory.createMavenLocalRepository().url
    }

    def "can explicitly add mavenLocal as a publishing repository"() {
        given:
        javaPluginApplied()

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
        javaPluginApplied()

        expect:
        publishTasks.size() == 0

        when:
        def repo1 = publishing.repositories.maven { url "foo" }

        then:
        publishTasks.size() == 1
        publishTasks.last().repository.is(repo1)
        publishTasks.last().name == "publishMavenPublicationToMavenRepository"

        when:
        publishing.repositories.ivy {}

        then:
        publishTasks.size() == 1

        when:
        def repo2 = publishing.repositories.maven { url "foo"; name "other" }

        then:
        publishTasks.size() == 2
        publishTasks.last().repository.is(repo2)
        publishTasks.last().name == "publishMavenPublicationToOtherRepository"
    }

    List<PublishToMavenLocal> getPublishLocalTasks() {
        project.tasks.withType(PublishToMavenLocal).sort { it.name }
    }

    List<PublishToMavenRepository> getPublishTasks() {
        def allTasks = project.tasks.withType(PublishToMavenRepository).sort { it.name }
        allTasks.removeAll(publishLocalTasks)
        return allTasks
    }

    def "publication identity is live wrt project properties"() {
        given:
        javaPluginApplied()
        project.group = "group"
        project.version = "version"

        expect:
        with(mainPublication.asNormalisedPublication()) {
            groupId == "group"
            version == "version"
        }

        when:
        project.group = "changed-group"
        project.version = "changed-version"

        then:
        with(mainPublication.asNormalisedPublication()) {
            groupId == "changed-group"
            version == "changed-version"
        }
    }

    def "pom dir moves with build dir"() {
        given:
        javaPluginApplied()

        expect:
        mainPublication.pomDir == new File(project.buildDir, "publications/${mainPublication.name}")

        when:
        project.buildDir = project.file("changed")

        then:
        mainPublication.pomDir == new File(project.buildDir, "publications/${mainPublication.name}")
    }
}
