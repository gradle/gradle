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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=4.0")
class ResolveArtifactsProgressCrossVersionSpec extends ToolingApiSpecification {

    def "generates event for resolving intrinsic artifacts by iterating the configuration"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('')

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
        resolveArtifacts.child("Resolve a.jar (project :provider)")
    }

    def "generates event for resolving intrinsic artifacts via file collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.files')

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
        resolveArtifacts.child("Resolve a.jar (project :provider)")
    }

    def "generates event for resolving intrinsic artifacts via incoming file collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.files')

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
        resolveArtifacts.child("Resolve a.jar (project :provider)")
    }

    def "generates event for resolving intrinsic artifacts via incoming artifact collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.artifacts')

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
        resolveArtifacts.child("Resolve a.jar (project :provider)")
    }

    def "generates event for resolving artifact view via artifact collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "jar") } }.artifacts',
            'variants { var1 { attributes.attribute(kind, "jar"); artifact bJar } }')

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
        resolveArtifacts.child("Resolve b.jar (project :provider)")
    }

    def "generates event for resolving artifact view via file collection"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "jar") } }.files',
            'variants { var1 { attributes.attribute(kind, "jar"); artifact bJar } }')

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
        resolveArtifacts.child("Resolve b.jar (project :provider)")
    }

    def "generates event for resolving artifacts even if dependencies have no artifacts"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('', '')

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
        buildFile << buildFileContent('.incoming.artifactView { it.attributes { it.attribute(kind, "jar") } }.files',
            'variants { var1 { attributes.attribute(kind, "jar"); artifact bJar } }')

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
        resolveArtifacts.child("Resolve b.jar (project :provider)")
    }

    def "does not generate event if configuration has no dependencies"() {
        given:
        settingsFile << settingsFileContent()
        buildFile << buildFileContent('', '', 'configurationWithoutDependency')

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

    def buildFileContent(String artifactsAccessor, String artifactDeclaration = 'artifact aJar', String configuration = 'configurationWithDependency') {
        """
            def kind = Attribute.of('kind', String)

            project(':provider') {
                task aJar(type: Jar) { baseName = 'a' }
                task bJar(type: Jar) { baseName = 'b' }
                configurations {
                    'default' {
                        outgoing { $artifactDeclaration }
                    }
                }
            }
            
            configurations {
                configurationWithDependency
                configurationWithoutDependency
            }
            
            dependencies {
                configurationWithDependency project(":provider")
            }
            
            task resolve {
                doLast {
                    configurations.${configuration}${artifactsAccessor}.each { }
                }
            }
        """
    }
}
