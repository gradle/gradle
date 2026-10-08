/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

import spock.lang.Issue

class IsolatedProjectsJavaIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "can build library with dependency on another library"() {
        settingsFile << """
            include("a")
            include("b")
        """
        file("a/build.gradle") << """
            plugins { id('java-library') }
        """
        file("b/build.gradle") << """
            plugins { id('java-library') }
            dependencies { implementation project(':a') }
        """

        when:
        isolatedProjectsRun("b:assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":a", ":b")
        }

        when:
        isolatedProjectsRun("b:assemble")

        then:
        fixture.assertStateLoaded()
    }

    @Issue("https://github.com/gradle/gradle/issues/31973")
    def "can obtain project artifact coordinates from a lazy resolution result"() {
        settingsFile << """
            rootProject.name = 'root'
            include('app', 'library')
            includeBuild('included') {
                dependencySubstitution {
                    substitute module('other:library') using project(':library')
                }
            }
        """
        file('included/settings.gradle') << """
            rootProject.name = 'included'
            include('library')
        """
        file('included/library/build.gradle') << """
            plugins { id('java-library') }
            group = 'other'
            version = '2.0'
            base { archivesName = 'included-lib' }
        """
        file('library/build.gradle') << """
            plugins {
                id('java-library')
                id('maven-publish')
            }
            group = 'actual.group'
            version = '3.0'
            configurations.runtimeElements.outgoing.capability('misleading:feature:9.0')
            afterEvaluate { version = '3.1-SNAPSHOT' }
            base { archivesName = 'main-lib' }
            dependencies { api('other:library:2.0') }
            publishing {
                publications {
                    library(MavenPublication) {
                        from components.java
                        groupId = 'published.group'
                        artifactId = 'published-name'
                        version = '4.0'
                    }
                }
            }
        """
        file('app/build.gradle') << '''
            import org.gradle.api.artifacts.component.ProjectComponentIdentifier
            import org.gradle.api.artifacts.result.ResolvedDependencyResult

            plugins { id('java') }
            dependencies {
                implementation(project(':library')) {
                    capabilities { requireCapability('misleading:feature') }
                }
            }

            abstract class WriteCoordinates extends DefaultTask {
                @Input abstract MapProperty<String, String> getCoordinates()
                @Classpath abstract ConfigurableFileCollection getClasspath()
                @OutputFile abstract RegularFileProperty getReportFile()

                @TaskAction void writeReport() {
                    reportFile.get().asFile.text = coordinates.get().sort().collect { key, value ->
                        "$key=$value"
                    }.join('\\n') + '\\n'
                }
            }

            def incoming = configurations.runtimeClasspath.incoming
            def coordinatesById = incoming.resolutionResult.rootComponent.map { root ->
                def pending = [root]
                def visited = [] as Set
                def coordinates = [:]
                while (!pending.empty) {
                    def component = pending.remove(0)
                    if (visited.add(component.id)) {
                        def version = component.moduleVersion
                        if (component.id instanceof ProjectComponentIdentifier && version != null) {
                            coordinates[component.id] = [version.group, version.name, version.version].join(':')
                        }
                        component.dependencies.findAll { it instanceof ResolvedDependencyResult }.each {
                            pending.add(it.selected)
                        }
                    }
                }
                coordinates
            }
            def coordinates = incoming.artifacts.resolvedArtifacts.zip(coordinatesById) { artifacts, byId ->
                artifacts.collectEntries { artifact ->
                    [(artifact.file.name): byId[artifact.id.componentIdentifier]]
                }
            }

            tasks.register('writeCoordinates', WriteCoordinates) {
                it.coordinates.set(coordinates)
                classpath.from(configurations.runtimeClasspath)
                reportFile = layout.buildDirectory.file('coordinates.txt')
            }
        '''

        when:
        isolatedProjectsRun('app:writeCoordinates')

        then:
        fixture.assertStateStored {
            projectsConfigured(':', ':app', ':library', ':included', ':included:library')
        }
        file('app/build/coordinates.txt').text == '''included-lib-2.0.jar=other:library:2.0
main-lib-3.1-SNAPSHOT.jar=actual.group:library:3.1-SNAPSHOT
'''

        when:
        isolatedProjectsRun('app:writeCoordinates', '--rerun-tasks')

        then:
        fixture.assertStateLoaded()
        file('app/build/coordinates.txt').text == '''included-lib-2.0.jar=other:library:2.0
main-lib-3.1-SNAPSHOT.jar=actual.group:library:3.1-SNAPSHOT
'''

        when:
        def producer = file('library/build.gradle')
        producer.text = producer.text.replace("group = 'actual.group'", "group = 'actual.next'")
        isolatedProjectsRun('app:writeCoordinates')

        then:
        file('app/build/coordinates.txt').text == '''included-lib-2.0.jar=other:library:2.0
main-lib-3.1-SNAPSHOT.jar=actual.next:library:3.1-SNAPSHOT
'''
    }
}
