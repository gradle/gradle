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
    def "can obtain final project coordinates from a lazy resolution result"() {
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
        """
        file('library/build.gradle') << """
            plugins { id('java-library') }
            group = 'actual.group'
            version = '3.0'
            tasks.named('jar') { archiveBaseName = 'renamed-archive' }
            configurations.runtimeElements.outgoing.capability('misleading:feature:9.0')
            afterEvaluate { version = '3.1-SNAPSHOT' }
        """
        file('app/build.gradle') << '''
            import org.gradle.api.artifacts.result.ResolvedDependencyResult

            plugins { id('java') }
            dependencies {
                implementation(project(':library')) {
                    capabilities { requireCapability('misleading:feature') }
                }
                implementation('other:library:2.0')
            }

            abstract class WriteCoordinates extends DefaultTask {
                @Input abstract MapProperty<String, String> getCoordinates()
                @OutputFile abstract RegularFileProperty getReportFile()

                @TaskAction void writeReport() {
                    reportFile.get().asFile.text = coordinates.get().sort().collect { key, value ->
                        "$key=$value"
                    }.join('\\n') + '\\n'
                }
            }

            def coordinates = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent.map { root ->
                def result = [:]
                def queue = new ArrayDeque([root])
                def visited = new HashSet()
                while (!queue.empty) {
                    def component = queue.removeFirst()
                    if (!visited.add(component.id)) continue
                    if (component.id instanceof ProjectComponentIdentifier) {
                        result[component.id.buildTreePath] = component.moduleVersion.toString()
                    }
                    component.dependencies.findAll { it instanceof ResolvedDependencyResult }.each {
                        queue.addLast(it.selected)
                    }
                }
                result
            }

            tasks.register('writeCoordinates', WriteCoordinates) {
                it.coordinates.set(coordinates)
                reportFile = layout.buildDirectory.file('coordinates.txt')
            }
        '''

        when:
        isolatedProjectsRun('app:writeCoordinates')

        then:
        fixture.assertStateStored {
            projectsConfigured(':', ':app', ':library', ':included', ':included:library')
        }
        file('app/build/coordinates.txt').text == ''':app=root:app:unspecified
:included:library=other:library:2.0
:library=actual.group:library:3.1-SNAPSHOT
'''

        when:
        isolatedProjectsRun('app:writeCoordinates')

        then:
        fixture.assertStateLoaded()
        file('app/build/coordinates.txt').text == ''':app=root:app:unspecified
:included:library=other:library:2.0
:library=actual.group:library:3.1-SNAPSHOT
'''
    }
}
