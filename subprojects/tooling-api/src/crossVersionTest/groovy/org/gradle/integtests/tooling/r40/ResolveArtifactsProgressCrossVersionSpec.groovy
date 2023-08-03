/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion

@TargetGradleVersion(">=4.0")
@Flaky(because = "https://github.com/gradle/gradle-private/issues/3638")
class ResolveArtifactsProgressCrossVersionSpec extends ToolingApiSpecification {
    private RepositoryHttpServer server

    def setup() {
        server = new RepositoryHttpServer(temporaryFolder, targetDist.version.version)
        server.before()
        toolingApi.requireIsolatedUserHome()
    }

    def cleanup() {
        server.after()
    }

    private String expectedDisplayName(String name, String extension, String version) {
        getTargetVersion() < GradleVersion.version("6.0") ? "$name.$extension" : "$name-$version.$extension"
    }

    @LeaksFileHandles
    def "generates event for resolving intrinsic artifacts by iterating the configuration"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('')
        expectDownload()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('provider', 'jar', '1.0')} (test:provider:1.0)")
    }

    def "generates event for resolving intrinsic artifacts via file collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.files')
        expectDownload()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('provider', 'jar', '1.0')} (test:provider:1.0)")
    }

    def "generates event for resolving intrinsic artifacts via incoming file collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.files')
        expectDownload()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('provider', 'jar', '1.0')} (test:provider:1.0)")
    }

    def "generates event for resolving intrinsic artifacts via incoming artifact collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.artifacts')
        expectDownload()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('provider', 'jar', '1.0')} (test:provider:1.0)")
    }

    def "generates event for resolving artifact view via artifact collection"() {
        given:
        settingsFile << settingsFileContent()
        expectDownloadOtherTypes()
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "thing") } }.artifacts')

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('other', 'thing', '1.0')} (test:other:1.0)")
    }

    def "generates event for resolving artifact view via file collection"() {
        given:
        settingsFile << settingsFileContent()
        expectDownloadOtherTypes()
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "thing") } }.files')

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 1
        resolveArtifacts.child("Resolve ${expectedDisplayName('other', 'thing', '1.0')} (test:other:1.0)")
    }

    def "generates event for resolving artifacts even if dependencies have no artifacts"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('')
        expectDownloadNoArtifacts()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.size() == 0
    }

    def "generates event for resolving artifact view even if the view is empty"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "none") } }.files')
        expectDownloadOtherTypes()

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveArtifacts = events.operation('Resolve files of :configurationWithDependency')
        resolveArtifacts.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveArtifacts.children.empty
    }

    def "does not generate event if configuration has no dependencies"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('', 'configurationWithoutDependency')

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection -> connection.newBuild().forTasks('resolve').addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        and:
        def resolveDependencies = events.operation('Resolve dependencies of :configurationWithoutDependency')
        resolveDependencies.parent.descriptor.displayName.matches("Execute .* for :resolve")
        resolveDependencies.parent.children.size() == 1
    }

    def settingsFileContent() {
        """
            rootProject.name = 'consumer'
            include 'provider'
        """
    }

    def expectDownload() {
        mavenHttpRepo.module("test", "provider", "1.0").publish().allowAll()
    }

    def expectDownloadNoArtifacts() {
        mavenHttpRepo.module("test", "provider", "1.0")
            .hasPackaging("pom")
            .hasType("pom")
            .dependsOn("test", "other", "1.0")
            .publish()
            .allowAll()
        mavenHttpRepo.module("test", "other", "1.0")
            .hasPackaging("pom")
            .hasType("pom")
            .publish()
            .allowAll()
    }

    def expectDownloadOtherTypes() {
        mavenHttpRepo.module("test", "provider", "1.0")
            .dependsOn("test", "other", "1.0")
            .publish()
            .allowAll()
        def m = mavenHttpRepo.module("test", "other", "1.0")
            .hasPackaging("thing")
            .hasType("thing")
        m.artifact(type: "thing")
        m.publish()
        m.allowAll()
    }

    def buildFileContent(String artifactsAccessor, String configuration = 'configurationWithDependency') {
        """
            def kind = Attribute.of('kind', String)

            repositories {
                maven { url = '${mavenHttpRepo.uri}' }
            }

            configurations {
                configurationWithDependency
                configurationWithoutDependency
            }

            dependencies {
                configurationWithDependency "test:provider:1.0"
                artifactTypes {
                    thing { attributes.attribute(kind, "thing") }
                    jar { attributes.attribute(kind, "jar") }
                }
            }

            task resolve {
                doLast {
                    configurations.${configuration}${artifactsAccessor}.each { }
                }
            }
        """
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenFileRepository getMavenRepo(String name = "repo") {
        return new MavenFileRepository(file(name))
    }
}
