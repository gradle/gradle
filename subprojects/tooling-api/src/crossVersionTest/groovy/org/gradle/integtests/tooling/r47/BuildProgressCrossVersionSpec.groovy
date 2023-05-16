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

package org.gradle.integtests.tooling.r47

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@TargetGradleVersion(">=4.7")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {

    public RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
    }

    def cleanup() {
        server.after()
    }

    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    def "generates download events during maven publish"() {
        given:
        toolingApi.requireIsolatedUserHome()

        int metadataDownloads = 1
        int metadataChecksumDownloads = 0
        // Older versions of Gradle use maven-aether for maven-publish, and perform additional downloads
        if (targetVersion.compareTo(GradleVersion.version("5.6")) < 0) {
            metadataDownloads = 2
            metadataChecksumDownloads = 2
        }

        def module = mavenHttpRepo.module('group', 'publish', '1')

        // module is published
        module.publish()

        // module will be published a second time via 'maven-publish'
        boolean isGradle6Plus = targetVersion.compareTo(GradleVersion.version("6.0")) >= 0
        module.artifact.expectPublish(isGradle6Plus)
        module.pom.expectPublish(isGradle6Plus)

        if (isGradle6Plus) {
            module.moduleMetadata.expectPublish() // Gradle Module Metadata is published with Gradle 6+
        }
        metadataDownloads.times {
            module.rootMetaData.expectGet()
        }
        metadataChecksumDownloads.times {
            module.rootMetaData.sha1.expectGet()
        }
        module.rootMetaData.expectPublish(isGradle6Plus)

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'
            version = '1'
            group = 'group'

            publishing {
                repositories {
                    maven { url "${mavenHttpRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        def events = ProgressEvents.create()

        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('publish')
                    .addProgressListener(events).run()
        }

        then:
        def publishTask = events.operation('Task :publishMavenPublicationToMavenRepository')
        publishTask.descendants { it.descriptor.displayName == "Download ${module.rootMetaData.uri}" }.size() == metadataDownloads
        publishTask.descendants { it.descriptor.displayName == "Download ${module.rootMetaData.sha1.uri}" }.size() == metadataChecksumDownloads
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }
}
