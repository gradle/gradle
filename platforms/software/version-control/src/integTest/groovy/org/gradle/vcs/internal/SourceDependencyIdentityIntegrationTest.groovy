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

package org.gradle.vcs.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule


class SourceDependencyIdentityIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('buildB', temporaryFolder.getTestDirectory())

    def setup() {
        settingsFile << """
            rootProject.name = 'buildA'
        """
        buildFile << """
            apply plugin: 'java'
        """

        repo.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.2'
        """
    }

    def dependency(String moduleName) {
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:${moduleName}") {
                        from(GitVersionControlSpec) {
                            url = uri("${repo.url}")
                        }
                    }
                }
            }
        """
        buildFile << """
            dependencies { implementation 'org.test:${moduleName}:1.2' }
        """
    }

    def "includes build identifier in error message on failure to resolve dependencies of build with #display"() {
        repo.file("settings.gradle") << """
            ${settings}
        """
        repo.file("build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")
        dependency(dependencyName)

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':${buildName}:compileJava'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':${buildName}:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project ':${buildName}'""")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    def "includes build identifier in task failure error message with #display"() {
        repo.file("settings.gradle") << """
            ${settings}
        """
        repo.file("build.gradle") << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")
        dependency(dependencyName)

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':${buildName}:classes' (registered by plugin class 'org.gradle.api.plugins.JavaBasePlugin').")
        failure.assertHasCause("broken")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects { apply plugin: 'java-library' } in included build")
    def "includes build identifier in dependency resolution results with #display"() {
        repo.file("a/.gitkeepdir").touch()
        repo.file("settings.gradle") << """
            ${settings}
            include 'a'
        """
        repo.file("build.gradle") << """
            allprojects { apply plugin: 'java-library' }
            dependencies { implementation project(':a') }
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")
        dependency(dependencyName)

        buildFile << """
            classes {
                // Capture the resolution result lazily at configuration time so the assertions
                // don't read project state at execution time (configuration-cache compatible).
                def rootComponent = configurations.runtimeClasspath.incoming.resolutionResult.rootComponent
                doLast {
                    def components = []
                    def selectors = []
                    def seen = new HashSet()
                    def queue = [rootComponent.get()]
                    while (!queue.isEmpty()) {
                        def component = queue.remove(0)
                        if (!seen.add(component.id)) {
                            continue
                        }
                        components << component.id
                        component.dependencies.each { dependency ->
                            selectors << dependency.requested
                            if (dependency instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                                queue << dependency.selected
                            }
                        }
                    }

                    assert components.size() == 3
                    assert components.collect { [it.build.buildPath, it.projectPath, it.projectName] } as Set == [
                        [':', ':', 'buildA'],
                        [':${buildName}', ':', '${dependencyName}'],
                        [':${buildName}', ':a', 'a']
                    ] as Set

                    assert selectors.size() == 2
                    assert selectors.collect { it.displayName } as Set == [
                        'org.test:${dependencyName}:1.2',
                        "project ':${buildName}:a'"
                    ] as Set
                    def projectSelector = selectors.find { it instanceof org.gradle.api.artifacts.component.ProjectComponentSelector }
                    assert projectSelector.buildPath == ':${buildName}'
                    assert projectSelector.projectPath == ':a'
                }
            }
        """

        expect:
        succeeds(":assemble")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }
}
