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
        failure.assertHasCause("Could not resolve all task dependencies for configuration ':${buildName}:compileClasspath'.")
        failure.assertHasCause("""Cannot resolve external dependency test:test:1.2 because no repositories are defined.
Required by:
    project :${buildName}""")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    @ToBeFixedForConfigurationCache
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
        failure.assertHasDescription("Execution failed for task ':${buildName}:classes'.")
        failure.assertHasCause("broken")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }

    @ToBeFixedForConfigurationCache
    def "includes build identifier in dependency resolution results with #display"() {
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
            classes.doLast {
                def components = configurations.runtimeClasspath.incoming.resolutionResult.allComponents.id
                assert components.size() == 3
                assert components[0].build.buildPath == ':'
                assert components[0].build.name == ':'
                assert components[0].build.currentBuild
                assert components[0].projectPath == ':'
                assert components[0].projectName == 'buildA'
                assert components[1].build.buildPath == ':${buildName}'
                assert components[1].build.name == '${buildName}'
                assert !components[1].build.currentBuild
                assert components[1].projectPath == ':'
                assert components[1].projectName == '${dependencyName}'
                assert components[2].build.buildPath == ':${buildName}'
                assert components[2].build.name == '${buildName}'
                assert !components[2].build.currentBuild
                assert components[2].projectPath == ':a'
                assert components[2].projectName == 'a'

                def selectors = configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.requested
                assert selectors.size() == 2
                assert selectors[0].displayName == 'org.test:${dependencyName}:1.2'
                assert selectors[1].displayName == 'project :${buildName}:a'
                assert selectors[1].buildPath == ':${buildName}'
                assert selectors[1].buildName == '${buildName}'
                assert selectors[1].projectPath == ':a'
            }
        """

        3.times {
            executer.expectDocumentedDeprecationWarning("The BuildIdentifier.isCurrentBuild() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")
        }
        executer.expectDocumentedDeprecationWarning("The ProjectComponentSelector.getBuildName() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use getBuildPath() to get a unique identifier for the build. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#build_identifier_name_and_current_deprecation")

        expect:
        succeeds(":assemble")

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }
}
