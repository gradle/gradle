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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule


class NestedSourceDependencyIdentityIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repoB = new GitFileRepository('buildB', temporaryFolder.getTestDirectory())
    @Rule
    GitFileRepository repoC = new GitFileRepository('buildC', temporaryFolder.getTestDirectory())

    def setup() {
        settingsFile << """
            rootProject.name = 'buildA'
            sourceControl {
                vcsMappings {
                    withModule("org.test:buildB") {
                        from(GitVersionControlSpec) {
                            url = uri("${repoB.url}")
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            dependencies { implementation 'org.test:buildB:1.2' }
        """

        repoB.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.2'
        """

        repoC.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.2'
        """
    }

    def dependency(String moduleName) {
        repoB.file("settings.gradle") << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:${moduleName}") {
                        from(GitVersionControlSpec) {
                            url = uri("${repoC.url}")
                        }
                    }
                }
            }
        """
        repoB.file("build.gradle") << """
            dependencies { implementation 'org.test:${moduleName}:1.2' }
        """
    }

    def "includes build identifier in error message on failure to resolve dependencies of build with #display"() {
        repoC.file("settings.gradle") << """
            ${settings}
        """
        repoC.file("build.gradle") << """
            dependencies { implementation "test:test:1.2" }
        """
        repoC.commit("initial version")
        repoC.createLightWeightTag("1.2")

        dependency(dependencyName)
        repoB.commit("initial version")
        repoB.createLightWeightTag("1.2")

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Could not determine the dependencies of task ':${buildName}:compileJava'.")
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':${buildName}:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :${buildName}""")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildC"  | "buildC"       | "default root project name"
        "rootProject.name='someLib'" | "buildC"  | "someLib"      | "configured root project name"
    }

    @ToBeFixedForConfigurationCache
    def "includes build identifier in task failure error message with #display"() {
        repoC.file("settings.gradle") << """
            ${settings}
        """
        repoC.file("build.gradle") << """
            classes.doLast {
                throw new RuntimeException("broken")
            }
        """
        repoC.commit("initial version")
        repoC.createLightWeightTag("1.2")

        dependency(dependencyName)
        repoB.commit("initial version")
        repoB.createLightWeightTag("1.2")

        when:
        fails(":assemble")

        then:
        failure.assertHasDescription("Execution failed for task ':${buildName}:classes'.")
        failure.assertHasCause("broken")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildC"  | "buildC"       | "default root project name"
        "rootProject.name='someLib'" | "buildC"  | "someLib"      | "configured root project name"
    }

    @ToBeFixedForConfigurationCache
    def "includes build identifier in dependency resolution results with #display"() {
        repoC.file("settings.gradle") << """
            ${settings}
            include 'a'
        """
        repoC.file("build.gradle") << """
            allprojects { apply plugin: 'java-library' }
            dependencies { implementation project(':a') }
        """
        repoC.commit("initial version")
        repoC.createLightWeightTag("1.2")

        dependency(dependencyName)
        repoB.commit("initial version")
        repoB.createLightWeightTag("1.2")

        buildFile << """
            classes.doLast {
                def components = configurations.runtimeClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 4
                assert components[0].build.buildPath == ':'
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == 'buildA'
                assert components[0].buildTreePath == ':'
                assert components[1].build.buildPath == ':buildB'
                assert components[1].build.name == 'buildB'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == 'buildB'
                assert components[1].buildTreePath == ':buildB'
                assert components[2].build.buildPath == ':${buildName}'
                assert components[2].build.name == '${buildName}'
                assert !components[2].build.currentBuild
                assert components[2].projectPath == ':'
                assert components[2].projectName == '${dependencyName}'
                assert components[3].build.buildPath == ':${buildName}'
                assert components[3].build.name == '${buildName}'
                assert !components[3].build.currentBuild
                assert components[3].projectPath == ':a'
                assert components[3].projectName == 'a'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 3
                assert selectors[0].displayName == 'org.test:buildB:1.2'
                assert selectors[1].displayName == 'org.test:${dependencyName}:1.2'
                assert selectors[2].displayName == 'project :${buildName}:a'
                assert selectors[2].buildPath == ':${buildName}'
                assert selectors[2].buildName == '${buildName}'
                assert selectors[2].projectPath == ':a'
            }
        """

        expect:
        succeeds(":assemble")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildC"  | "buildC"       | "default root project name"
        "rootProject.name='someLib'" | "buildC"  | "someLib"      | "configured root project name"
    }
}
