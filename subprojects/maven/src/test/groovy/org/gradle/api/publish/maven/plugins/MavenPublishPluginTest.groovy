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

import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.internal.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.MavenPublicationInternal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.gradle.util.HelperUtil
import spock.lang.Specification

class MavenPublishPluginTest extends Specification {

    def project = HelperUtil.createRootProject()
    PublishingExtension publishing

    def setup() {
        project.plugins.apply(MavenPublishPlugin)
        publishing = project.extensions.getByType(PublishingExtension)
    }

    def "default publication"() {
        expect:
        publishing.publications.size() == 1
        publishing.publications.maven instanceof DefaultMavenPublication
    }

    def "creates publish tasks"() {
        when:
        publishing.repositories { maven { url = "http://foo.com" } }

        then:
        project.tasks["publishMavenPublicationToMavenRepository"] != null
        project.tasks["publishMavenPublicationToMavenLocal"] != null
    }

    def "default publication always has all visible config artifacts"() {
        given:
        MavenPublicationInternal publication = getMainPublication()

        expect:
        publication.artifacts.empty

        when:
        project.plugins.apply(JavaPlugin)

        then:
        publication.artifacts.size() == 1
        publication.artifacts.find { it.archiveTask instanceof Jar }

        when:
        project.plugins.apply(WarPlugin)

        then:
        publication.artifacts.size() == 2
        publication.artifacts.find { it.archiveTask instanceof War }
    }

    protected MavenPublicationInternal getMainPublication() {
        publishing.publications.maven
    }

    def "task is created for publishing to mavenLocal"() {
        expect:
        publishTasks.size() == 1
        publishTasks.first().name == "publishMavenPublicationToMavenLocal"
        publishTasks.first().repository.name == "mavenLocalPublish"
        publishTasks.first().repository.url == project.getServices().get(DependencyResolutionServices).baseRepositoryFactory.createMavenLocalRepository().url
    }

    def "tasks are created for compatible publication / repo"() {
        expect:
        publishTasks.size() == 1

        when:
        def repo1 = publishing.repositories.maven { url "foo" }

        then:
        publishTasks.size() == 2
        publishTasks.last().repository.is(repo1)
        publishTasks.last().name == "publishMavenPublicationToMavenRepository"

        when:
        publishing.repositories.ivy {}

        then:
        publishTasks.size() == 2

        when:
        def repo2 = publishing.repositories.maven { url "foo"; name "other" }

        then:
        publishTasks.size() == 3
        publishTasks.last().repository.is(repo2)
        publishTasks.last().name == "publishMavenPublicationToOtherRepository"
    }

    List<PublishToMavenRepository> getPublishTasks() {
        project.tasks.withType(PublishToMavenRepository).sort { it.name }
    }

    def "publication identity is live wrt project properties"() {
        given:
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
        expect:
        mainPublication.pomDir == new File(project.buildDir, "publications/${mainPublication.name}")

        when:
        project.buildDir = project.file("changed")

        then:
        mainPublication.pomDir == new File(project.buildDir, "publications/${mainPublication.name}")
    }
}
